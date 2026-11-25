import type { Db } from '../../lib/prisma.js';
import type { AiClient } from './ai.client.js';
import { MAX_RECENT_TURN } from './assistant.js';
import { buildSummaryMessages, clipSummary, FOLD_THRESHOLD, RECENT_WINDOW } from './summary.js';

/** Most turns handed to the model in one fold; anything left is folded the next time. */
const MAX_TURNS_PER_FOLD = 60;
/** Arbitrary key of the PostgreSQL advisory lock that serialises appendPair. */
const PAIR_LOCK = 827_364;

export interface StoredMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  suggestions: unknown[] | null;
  /** `{ "<suggestion index>": "accepted" | "skipped" }` */
  outcomes: Record<string, string> | null;
  createdAt: Date;
}

type Row = { id: string; role: string; content: string; suggestions: unknown; outcomes: unknown; createdAt: Date };

const stored = (row: Row): StoredMessage => ({
  id: row.id,
  role: row.role as StoredMessage['role'],
  content: row.content,
  suggestions: Array.isArray(row.suggestions) ? row.suggestions : null,
  outcomes: row.outcomes && typeof row.outcomes === 'object' && !Array.isArray(row.outcomes) ? (row.outcomes as Record<string, string>) : null,
  createdAt: row.createdAt,
});

/** The single running conversation: messages, what happened to the suggestions, and the rolling summary. */
export class ChatService {
  constructor(private readonly db: Db) {}

  /** The last [limit] messages, oldest first. */
  async recent(limit: number): Promise<StoredMessage[]> {
    const rows = await this.db.chatMessage.findMany({ orderBy: { createdAt: 'desc' }, take: limit });
    return rows.reverse().map(stored);
  }

  /** A page of history, newest first. [before] is the id of the oldest message already shown. */
  async page(before: string | null, limit: number): Promise<{ messages: StoredMessage[]; nextBefore: string | null }> {
    let where = {};
    if (before) {
      const anchor = await this.db.chatMessage.findUnique({ where: { id: before } });
      if (anchor) where = { createdAt: { lt: anchor.createdAt } };
    }
    const rows = await this.db.chatMessage.findMany({ where, orderBy: { createdAt: 'desc' }, take: limit + 1 });
    const more = rows.length > limit;
    const messages = rows.slice(0, limit).map(stored);
    return { messages, nextBefore: more ? messages[messages.length - 1]!.id : null };
  }

  /** Saves a question and its reply together (both or neither), the reply one millisecond after the question. */
  async appendPair(question: string, reply: string, suggestions: unknown[]): Promise<{ userId: string; assistantId: string }> {
    return this.db.$transaction(async (tx) => {
      // One pair at a time, so two questions asked together never interleave or share a timestamp.
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(${PAIR_LOCK})`;
      const last = await tx.chatMessage.findFirst({ orderBy: { createdAt: 'desc' } });
      const base = Math.max(Date.now(), last ? last.createdAt.getTime() + 1 : 0);
      const user = await tx.chatMessage.create({ data: { role: 'USER', content: question, createdAt: new Date(base) } });
      const assistant = await tx.chatMessage.create({
        data: { role: 'ASSISTANT', content: reply, suggestions: suggestions as object[], createdAt: new Date(base + 1) },
      });
      return { userId: user.id, assistantId: assistant.id };
    });
  }

  /** Records what the owner did with suggestion [index] of an assistant message. False when there is no such suggestion. */
  async setOutcome(messageId: string, index: number, outcome: 'accepted' | 'skipped'): Promise<boolean> {
    const row = await this.db.chatMessage.findUnique({ where: { id: messageId } });
    if (!row || row.role !== 'ASSISTANT' || !Array.isArray(row.suggestions) || index < 0 || index >= row.suggestions.length) return false;
    const previous = row.outcomes && typeof row.outcomes === 'object' && !Array.isArray(row.outcomes) ? (row.outcomes as Record<string, string>) : {};
    await this.db.chatMessage.update({ where: { id: messageId }, data: { outcomes: { ...previous, [String(index)]: outcome } } });
    return true;
  }

  /** Deletes every message and the summary (memory is kept). */
  async clear(): Promise<void> {
    await this.db.$transaction([this.db.chatMessage.deleteMany(), this.db.chatSummary.deleteMany()]);
  }

  async summary(): Promise<string> {
    return (await this.db.chatSummary.findUnique({ where: { id: 1 } }))?.text ?? '';
  }

  /**
   * When more than 20 messages sit outside the recent window of 10 and are not in the summary yet, asks the model to fold
   * them into the summary (one call). Does nothing otherwise.
   */
  async foldIfNeeded(client: AiClient): Promise<void> {
    const current = await this.db.chatSummary.findUnique({ where: { id: 1 } });
    const window = await this.db.chatMessage.findMany({ orderBy: { createdAt: 'desc' }, take: RECENT_WINDOW });
    const oldest = window[window.length - 1];
    if (!oldest || window.length < RECENT_WINDOW) return;
    const where = {
      createdAt: { lt: oldest.createdAt, ...(current?.coversUntil ? { gt: current.coversUntil } : {}) },
    };
    if ((await this.db.chatMessage.count({ where })) <= FOLD_THRESHOLD) return;

    const rows = await this.db.chatMessage.findMany({ where, orderBy: { createdAt: 'asc' }, take: MAX_TURNS_PER_FOLD });
    const turns = rows.map((r) => ({ who: r.role === 'USER' ? 'owner' : 'assistant', text: r.content.slice(0, MAX_RECENT_TURN) }));
    const text = clipSummary(await client.complete(buildSummaryMessages(current?.text ?? '', turns)));
    const coversUntil = rows[rows.length - 1]!.createdAt;
    await this.db.chatSummary.upsert({ where: { id: 1 }, create: { id: 1, text, coversUntil }, update: { text, coversUntil } });
  }
}
