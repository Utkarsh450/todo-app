import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import type { Db } from '../src/lib/prisma.js';
import type { AiClient, ChatMessage } from '../src/modules/ai/ai.client.js';
import { ChatService } from '../src/modules/ai/chat.service.js';
import { MAX_MEMORIES } from '../src/modules/ai/memory.js';
import { MemoryService } from '../src/modules/ai/memory.service.js';
import { FOLD_THRESHOLD, RECENT_WINDOW } from '../src/modules/ai/summary.js';
import { createTestApp, resetDatabase } from './helpers.js';

// These tests need the local PostgreSQL test database (paper_todo_test): run them on the Mac or in CI.

let app: FastifyInstance;
let db: Db;
let memory: MemoryService;
let chat: ChatService;

beforeAll(async () => {
  ({ app, db } = await createTestApp());
  memory = new MemoryService(db);
  chat = new ChatService(db);
});
afterAll(async () => {
  await app.close();
  await db.$disconnect();
});
beforeEach(() => resetDatabase(db));

/** A model that always answers with the given text and records what it was asked. */
function fakeClient(answer: string) {
  const asked: ChatMessage[][] = [];
  const client: AiClient = {
    configured: true,
    model: 'fake',
    async complete(messages) {
      asked.push(messages);
      return answer;
    },
  };
  return { client, asked };
}

describe('MemoryService', () => {
  it('create refuses secrets and merges a reworded duplicate into the old row', async () => {
    expect(await memory.create('The wifi password is hunter2', 'OTHER', 'AUTO')).toBeNull();

    const first = await memory.create('Wakes at 7', 'ROUTINE', 'AUTO');
    const again = await memory.create('wakes at 7.', 'ROUTINE', 'AUTO');

    expect(first).not.toBeNull();
    expect(again!.id).toBe(first!.id);
    expect(await memory.list()).toHaveLength(1);
  });

  it('refuses the 101st fact', async () => {
    for (let i = 0; i < MAX_MEMORIES; i += 1) {
      expect(await memory.create(`Fact number${i} about subject${i}`, 'OTHER', 'AUTO')).not.toBeNull();
    }
    expect(await memory.create('One more completely different thing', 'OTHER', 'AUTO')).toBeNull();
    expect(await memory.list()).toHaveLength(MAX_MEMORIES);
  });

  it('applyOps forgets only ids that exist and notes the new facts', async () => {
    const a = (await memory.create('Wakes at 7', 'ROUTINE', 'AUTO'))!;
    await memory.create('Gym on Mondays', 'ROUTINE', 'USER');

    const result = await memory.applyOps({
      remember: [{ text: 'Likes tea', category: 'PREFERENCE' }],
      forget: [a.id, '00000000-0000-4000-8000-000000000000'],
    });

    expect(result.forgotten).toEqual([a.id]);
    expect(result.noted.map((n) => n.text)).toEqual(['Likes tea']);
    expect((await memory.list()).map((m) => m.text).sort()).toEqual(['Gym on Mondays', 'Likes tea']);
  });

  it('update changes the text, refuses a secret, and says null for an unknown id', async () => {
    const a = (await memory.create('Wakes at 7', 'ROUTINE', 'AUTO'))!;
    expect((await memory.update(a.id, 'Wakes at 6'))!.text).toBe('Wakes at 6');
    expect(await memory.update(a.id, 'The password is hunter2')).toBeNull();
    expect((await memory.list())[0]!.text).toBe('Wakes at 6');
    expect(await memory.update('00000000-0000-4000-8000-000000000000', 'x y')).toBeNull();
  });

  it('remove says whether something was removed, and removeAll counts', async () => {
    const a = (await memory.create('Wakes at 7', 'ROUTINE', 'AUTO'))!;
    await memory.create('Likes tea', 'PREFERENCE', 'AUTO');
    expect(await memory.remove(a.id)).toBe(true);
    expect(await memory.remove(a.id)).toBe(false);
    expect(await memory.removeAll()).toBe(1);
  });
});

describe('ChatService', () => {
  it('appendPair stores the question and the reply in that order', async () => {
    const ids = await chat.appendPair('Plan my day', 'Done.', [{ kind: 'MOVE' }]);
    const stored = await chat.recent(10);
    expect(stored.map((m) => [m.id, m.role, m.content])).toEqual([
      [ids.userId, 'USER', 'Plan my day'],
      [ids.assistantId, 'ASSISTANT', 'Done.'],
    ]);
    expect(stored[1]!.suggestions).toEqual([{ kind: 'MOVE' }]);
    expect(stored[0]!.createdAt.getTime()).toBeLessThan(stored[1]!.createdAt.getTime());
  });

  it('setOutcome writes into outcomes[index] and rejects an unknown index, a user message and an unknown id', async () => {
    const { userId, assistantId } = await chat.appendPair('q', 'r', [{ kind: 'MOVE' }, { kind: 'SPLIT' }]);

    expect(await chat.setOutcome(assistantId, 1, 'accepted')).toBe(true);
    expect(await chat.setOutcome(assistantId, 0, 'skipped')).toBe(true);
    expect((await chat.recent(10))[1]!.outcomes).toEqual({ '0': 'skipped', '1': 'accepted' });

    expect(await chat.setOutcome(assistantId, 5, 'skipped')).toBe(false);
    expect(await chat.setOutcome(userId, 0, 'skipped')).toBe(false);
    expect(await chat.setOutcome('00000000-0000-4000-8000-000000000000', 0, 'skipped')).toBe(false);
  });

  it('page is empty for no messages, newest first, and the cursor continues to the end', async () => {
    expect(await chat.page(null, 10)).toEqual({ messages: [], nextBefore: null });

    for (let i = 1; i <= 3; i += 1) await chat.appendPair(`q${i}`, `r${i}`, []);

    const first = await chat.page(null, 4);
    expect(first.messages.map((m) => m.content)).toEqual(['r3', 'q3', 'r2', 'q2']);
    expect(first.nextBefore).toBe(first.messages[3]!.id);

    const second = await chat.page(first.nextBefore, 4);
    expect(second.messages.map((m) => m.content)).toEqual(['r1', 'q1']);
    expect(second.nextBefore).toBeNull();
  });

  it('a cursor that does not exist any more (after a clear) gives an empty page, not the newest page again', async () => {
    await chat.appendPair('q', 'r', []);
    expect(await chat.page('00000000-0000-4000-8000-000000000000', 10)).toEqual({ messages: [], nextBefore: null });
  });

  it('suggestions with dates are stored as plain JSON', async () => {
    const { assistantId } = await chat.appendPair('q', 'r', [{ kind: 'MOVE', dueAt: new Date('2026-11-09T03:30:00.000Z') }]);
    expect((await chat.recent(10)).find((m) => m.id === assistantId)!.suggestions).toEqual([{ kind: 'MOVE', dueAt: '2026-11-09T03:30:00.000Z' }]);
  });

  it('clear removes every message and the summary', async () => {
    await chat.appendPair('q', 'r', []);
    await db.chatSummary.upsert({ where: { id: 1 }, create: { id: 1, text: 'Old notes' }, update: { text: 'Old notes' } });

    await chat.clear();

    expect(await chat.page(null, 10)).toEqual({ messages: [], nextBefore: null });
    expect(await chat.summary()).toBe('');
  });

  it('foldIfNeeded does nothing at 20 messages outside the window, folds at 21 with one model call, and then stays quiet', async () => {
    for (let i = 0; i < (RECENT_WINDOW + FOLD_THRESHOLD) / 2; i += 1) await chat.appendPair(`q${i}`, `r${i}`, []);
    const { client, asked } = fakeClient('They are planning exam week.');

    await chat.foldIfNeeded(client);
    expect(asked).toHaveLength(0); // 30 messages: exactly 20 outside the window of 10

    await db.chatMessage.create({ data: { role: 'USER', content: 'one more', createdAt: new Date(Date.now() + 60_000) } });
    await chat.foldIfNeeded(client);
    expect(asked).toHaveLength(1);
    expect(await chat.summary()).toBe('They are planning exam week.');
    expect((await db.chatSummary.findUniqueOrThrow({ where: { id: 1 } })).coversUntil).not.toBeNull();

    await chat.foldIfNeeded(client);
    expect(asked).toHaveLength(1); // everything outside the window is summarised now
  });
});
