import type { Db } from '../../lib/prisma.js';
import {
  isNearDuplicate,
  MAX_MEMORIES,
  validateFact,
  type MemoryBrief,
  type MemoryCategory,
  type MemoryOps,
} from './memory.js';

type Row = { id: string; text: string; category: string };

const brief = (row: Row): MemoryBrief => ({ id: row.id, text: row.text, category: row.category as MemoryCategory });

/** The facts the assistant keeps about the owner. Every write goes through the rules in memory.ts. */
export class MemoryService {
  constructor(private readonly db: Db) {}

  /** Newest first. */
  async list(): Promise<MemoryBrief[]> {
    const rows = await this.db.memory.findMany({ orderBy: { updatedAt: 'desc' } });
    return rows.map(brief);
  }

  /** Everything the Memory screen shows, newest first. */
  async listFull(): Promise<(MemoryBrief & { source: 'AUTO' | 'USER'; updatedAt: Date })[]> {
    const rows = await this.db.memory.findMany({ orderBy: { updatedAt: 'desc' } });
    return rows.map((row) => ({ ...brief(row), source: row.source, updatedAt: row.updatedAt }));
  }

  /**
   * Saves a fact. Returns null when it is refused (empty, too long, a secret, an order to the AI) or when the 100-fact
   * limit is reached. A fact that says the same as an old one refreshes the old row instead of adding another.
   */
  async create(text: string, category: MemoryCategory, source: 'AUTO' | 'USER'): Promise<MemoryBrief | null> {
    const check = validateFact(text, category);
    if (!check.ok) return null;
    const existing = await this.db.memory.findMany();
    const same = existing.find((row) => isNearDuplicate(row.text, check.fact.text));
    if (same) {
      const refreshed = await this.db.memory.update({ where: { id: same.id }, data: { text: check.fact.text } });
      return brief(refreshed);
    }
    if (existing.length >= MAX_MEMORIES) return null;
    const row = await this.db.memory.create({ data: { text: check.fact.text, category: check.fact.category, source } });
    return brief(row);
  }

  /** Changes the text of a fact; null when it is refused or does not exist. */
  async update(id: string, text: string): Promise<MemoryBrief | null> {
    const check = validateFact(text);
    if (!check.ok) return null;
    const row = await this.db.memory.findUnique({ where: { id } });
    if (!row) return null;
    return brief(await this.db.memory.update({ where: { id }, data: { text: check.fact.text } }));
  }

  async remove(id: string): Promise<boolean> {
    const { count } = await this.db.memory.deleteMany({ where: { id } });
    return count > 0;
  }

  async removeAll(): Promise<number> {
    const { count } = await this.db.memory.deleteMany();
    return count;
  }

  /** Forgets first (so room is made), then remembers. Ids that do not exist are ignored. */
  async applyOps(ops: MemoryOps): Promise<{ noted: MemoryBrief[]; forgotten: string[] }> {
    const forgotten: string[] = [];
    for (const id of ops.forget) if (await this.remove(id)) forgotten.push(id);
    const noted: MemoryBrief[] = [];
    for (const fact of ops.remember) {
      const saved = await this.create(fact.text, fact.category, 'AUTO');
      if (saved) noted.push(saved);
    }
    return { noted, forgotten };
  }
}
