import type { Db } from '../../lib/prisma.js';
import type { AiClient } from './ai.client.js';
import { buildAssistantMessages, parseAssistantReply, type AssistantExtras, type AssistantReply, type TaskBrief } from './assistant.js';

const MAX_TASKS = 40;
const MAX_EVENTS = 30;
const MAX_REMINDERS = 20;

export class AssistantService {
  constructor(
    private readonly client: AiClient,
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Answers one question about the open tasks. Nothing is changed: the app applies the suggestions the person accepts. */
  async ask(message: string, timeZone: string): Promise<AssistantReply> {
    const now = this.now();
    const [tasks, extras] = await Promise.all([this.openTasks(), this.extras(now)]);
    const raw = await this.client.complete(buildAssistantMessages(message, tasks, now, timeZone, extras));
    return parseAssistantReply(raw, tasks, now, extras.projects);
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
