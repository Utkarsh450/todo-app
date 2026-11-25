import type { Db } from '../../lib/prisma.js';
import type { AiClient } from './ai.client.js';
import {
  buildAssistantMessages,
  parseAssistantReply,
  type AssistantExtras,
  type AssistantReply,
  type PromptMemory,
  type TaskBrief,
} from './assistant.js';
import { ChatService, type StoredMessage } from './chat.service.js';
import { parseMemoryOps, type MemoryBrief } from './memory.js';
import { MemoryService } from './memory.service.js';
import { RECENT_WINDOW } from './summary.js';

const MAX_TASKS = 40;
const MAX_EVENTS = 30;
const MAX_REMINDERS = 20;

/** The reply, plus the id it was saved under and what the assistant learned or forgot in this turn. */
export interface AssistantAnswer extends AssistantReply {
  messageId: string;
  noted: MemoryBrief[];
  forgotten: string[];
}

export class AssistantService {
  private readonly memory: MemoryService;
  private readonly chat: ChatService;

  constructor(
    private readonly client: AiClient,
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {
    this.memory = new MemoryService(db);
    this.chat = new ChatService(db);
  }

  /**
   * Answers one request, with what it remembers about the owner and how the conversation went. Nothing about the owner's
   * tasks is changed here (the app applies the suggestions that get accepted). The turn is saved only after the model
   * answered, so a failed call leaves no half conversation behind; a broken `remember` part never costs the reply.
   */
  async ask(message: string, timeZone: string): Promise<AssistantAnswer> {
    const now = this.now();
    const [tasks, extras, facts, summary, recent] = await Promise.all([
      this.openTasks(),
      this.extras(now),
      this.memory.list(),
      this.chat.summary(),
      this.chat.recent(RECENT_WINDOW),
    ]);
    const promptMemory: PromptMemory = {
      facts,
      summary,
      recent: recent.map((m) => ({ who: m.role === 'USER' ? 'owner' : 'assistant', text: m.content })),
      actions: actionsOf(recent),
    };

    const raw = await this.client.complete(buildAssistantMessages(message, tasks, now, timeZone, extras, promptMemory));
    const reply = parseAssistantReply(raw, tasks, now, extras.projects);
    const ops = parseMemoryOps(raw, facts);

    const { assistantId } = await this.chat.appendPair(message, reply.reply, reply.suggestions);
    const { noted, forgotten } = await this.memory.applyOps(ops);
    // Folding old turns into the summary is housekeeping: if it fails the answer still goes out.
    await this.chat.foldIfNeeded(this.client).catch(() => undefined);

    return { ...reply, messageId: assistantId, noted, forgotten };
  }

  /** Events of the next two weeks, upcoming reminders and project names: context for planning and for picking a free time. */
  private async extras(now: Date): Promise<AssistantExtras> {
    const horizon = new Date(now.getTime() + 14 * 24 * 3_600_000);
    const [events, reminders, projects] = await Promise.all([
      this.db.calendarEvent.findMany({ where: { endAt: { gte: now }, startAt: { lt: horizon } }, orderBy: { startAt: 'asc' }, take: MAX_EVENTS }),
      this.db.reminder.findMany({ where: { enabled: true, remindAt: { gte: now } }, orderBy: { remindAt: 'asc' }, take: MAX_REMINDERS }),
      this.db.project.findMany({ select: { id: true, name: true }, orderBy: { name: 'asc' } }),
    ]);
    return {
      events: events.map((e) => ({ title: e.title, startAt: e.startAt, endAt: e.endAt, allDay: e.allDay })),
      reminders: reminders.map((r) => ({ title: r.title, remindAt: r.remindAt, repeatRule: r.repeatRule })),
      projects,
    };
  }

  private async openTasks(): Promise<TaskBrief[]> {
    const rows = await this.db.task.findMany({
      where: { archived: false, status: { not: 'DONE' } },
      orderBy: [{ dueAt: { sort: 'asc', nulls: 'last' } }, { createdAt: 'asc' }],
      take: MAX_TASKS,
      include: { project: { select: { name: true } }, subtasks: { select: { done: true } } },
    });
    return rows.map((t) => ({
      id: t.id,
      title: t.title,
      priority: t.priority,
      project: t.project?.name ?? null,
      dueAt: t.dueAt,
      openSubtasks: t.subtasks.filter((s) => !s.done).length,
    }));
  }
}

/** What the assistant proposed in its latest reply and what the owner did with each suggestion (pending until told). */
function actionsOf(recent: StoredMessage[]): PromptMemory['actions'] {
  const last = [...recent].reverse().find((m) => m.role === 'ASSISTANT' && m.suggestions && m.suggestions.length > 0);
  if (!last?.suggestions) return [];
  return last.suggestions.map((raw, index) => {
    const s = (raw ?? {}) as { kind?: unknown; title?: unknown; taskTitle?: unknown };
    const outcome = last.outcomes?.[String(index)];
    return {
      kind: typeof s.kind === 'string' ? s.kind : 'UNKNOWN',
      title: typeof s.title === 'string' ? s.title : typeof s.taskTitle === 'string' ? s.taskTitle : '',
      outcome: outcome === 'accepted' || outcome === 'skipped' ? outcome : 'pending',
    };
  });
}
