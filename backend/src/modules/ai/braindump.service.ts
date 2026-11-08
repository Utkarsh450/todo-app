import type { Db } from '../../lib/prisma.js';
import type { AiClient } from './ai.client.js';
import { buildBrainDumpMessages, parseBrainDump, type DumpedTask } from './braindump.js';

export class BrainDumpService {
  constructor(
    private readonly client: AiClient,
    private readonly db: Db,
    private readonly now: () => Date = () => new Date(),
  ) {}

  /** Sorts a rambling note into tasks. Nothing is saved: the app creates the ones the person keeps. */
  async sort(text: string, timeZone: string): Promise<DumpedTask[]> {
    const now = this.now();
    const projects = await this.db.project.findMany({ select: { id: true, name: true }, orderBy: { name: 'asc' } });
    const raw = await this.client.complete(buildBrainDumpMessages(text, projects, now, timeZone));
    return parseBrainDump(raw, projects, now);
  }
}
