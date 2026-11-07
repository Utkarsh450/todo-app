import { AppError, notFound } from '../../lib/errors.js';
import type { Db } from '../../lib/prisma.js';
import { DEFAULT_PROJECTS, type CreateProjectInput, type UpdateProjectInput } from './projects.schema.js';

const isUniqueViolation = (e: unknown) => typeof e === 'object' && e !== null && (e as { code?: string }).code === 'P2002';

export class ProjectsService {
  constructor(private readonly db: Db) {}

  list() {
    return this.db.project.findMany({ orderBy: { name: 'asc' } });
  }

  async create(input: CreateProjectInput) {
    try {
      return await this.db.project.create({ data: input });
    } catch (e) {
      if (isUniqueViolation(e)) throw new AppError(409, `Project "${input.name}" already exists`, 'CONFLICT');
      throw e;
    }
  }

  async update(id: string, input: UpdateProjectInput) {
    try {
      return await this.db.project.update({ where: { id }, data: input });
    } catch (e) {
      if (isUniqueViolation(e)) throw new AppError(409, 'Project name already exists', 'CONFLICT');
      if ((e as { code?: string }).code === 'P2025') throw notFound('Project');
      throw e;
    }
  }

  async remove(id: string) {
    try {
      await this.db.project.delete({ where: { id } });
    } catch (e) {
      if ((e as { code?: string }).code === 'P2025') throw notFound('Project');
      throw e;
    }
  }

  /** Inserts the design's default projects that do not exist yet. Safe to call repeatedly. */
  async seedDefaults(): Promise<number> {
    const result = await this.db.project.createMany({ data: [...DEFAULT_PROJECTS], skipDuplicates: true });
    return result.count;
  }
}
