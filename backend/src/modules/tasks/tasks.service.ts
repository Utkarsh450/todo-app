import { notFound } from '../../lib/errors.js';
import type { Db } from '../../lib/prisma.js';
import { statusFields } from './task-status.js';
import type { CreateTaskInput, ListTasksQuery, UpdateTaskInput } from './tasks.schema.js';

const include = { subtasks: { orderBy: { position: 'asc' as const } } };
const isMissing = (e: unknown) => (e as { code?: string } | null)?.code === 'P2025';

export class TasksService {
  constructor(
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {}

  list(q: ListTasksQuery) {
    return this.db.task.findMany({
      where: {
        archived: q.archived,
        status: q.status,
        projectId: q.projectId,
        title: q.q ? { contains: q.q, mode: 'insensitive' } : undefined,
        dueAt: q.from || q.to ? { gte: q.from, lt: q.to } : undefined,
      },
      orderBy: [{ dueAt: { sort: 'asc', nulls: 'last' } }, { createdAt: 'asc' }],
      include,
    });
  }

  async get(id: string) {
    const task = await this.db.task.findUnique({ where: { id }, include });
    if (!task) throw notFound('Task');
    return task;
  }

  create(input: CreateTaskInput) {
    const { subtasks, status = 'TODO', ...rest } = input;
    return this.db.task.create({
      data: {
        ...rest,
        ...statusFields(status, this.now()),
        subtasks: subtasks ? { create: subtasks.map((title, position) => ({ title, position })) } : undefined,
      },
      include,
    });
  }

  async update(id: string, input: UpdateTaskInput) {
    const { status, ...rest } = input;
    const current = await this.get(id);
    return this.db.task.update({
      where: { id },
      data: { ...rest, ...(status ? statusFields(status, this.now(), current.completedAt) : {}) },
      include,
    });
  }

  complete(id: string) {
    return this.update(id, { status: 'DONE' });
  }

  reopen(id: string) {
    return this.update(id, { status: 'TODO' });
  }

  async remove(id: string) {
    try {
      await this.db.task.delete({ where: { id } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Task');
      throw e;
    }
  }

  async addSubtask(taskId: string, title: string) {
    await this.get(taskId);
    const last = await this.db.subtask.aggregate({ where: { taskId }, _max: { position: true } });
    return this.db.subtask.create({ data: { taskId, title, position: (last._max.position ?? -1) + 1 } });
  }

  async updateSubtask(id: string, data: { title?: string; done?: boolean }) {
    try {
      return await this.db.subtask.update({ where: { id }, data });
    } catch (e) {
      if (isMissing(e)) throw notFound('Subtask');
      throw e;
    }
  }

  async removeSubtask(id: string) {
    try {
      await this.db.subtask.delete({ where: { id } });
    } catch (e) {
      if (isMissing(e)) throw notFound('Subtask');
      throw e;
    }
  }
}
