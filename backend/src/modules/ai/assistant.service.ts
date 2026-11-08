import type { Db } from '../../lib/prisma.js';
import type { AiClient } from './ai.client.js';
import { buildAssistantMessages, parseAssistantReply, type AssistantReply, type TaskBrief } from './assistant.js';

const MAX_TASKS = 40;

export class AssistantService {
  constructor(
    private readonly client: AiClient,
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Answers one question about the open tasks. Nothing is changed: the app applies the suggestions the person accepts. */
  async ask(message: string, timeZone: string): Promise<AssistantReply> {
    const now = this.now();
    const tasks = await this.openTasks();
    const raw = await this.client.complete(buildAssistantMessages(message, tasks, now, timeZone));
    return parseAssistantReply(raw, tasks, now);
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
