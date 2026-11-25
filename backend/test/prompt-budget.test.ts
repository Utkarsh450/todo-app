import { describe, expect, it } from 'vitest';
import type { ChatMessage } from '../src/modules/ai/ai.client.js';
import { buildAssistantMessages, MAX_MEMORY_PROMPT_CHARS, type PromptMemory, type TaskBrief } from '../src/modules/ai/assistant.js';

const NOW = new Date('2026-11-08T08:30:00Z');
const size = (messages: ChatMessage[]) => messages.reduce((n, m) => n + m.content.length, 0);

/** Words, so a "fact" of a given length is realistic text and not one long string. */
const words = (length: number, seed: string) => `${seed} ${'ab '.repeat(length)}`.slice(0, length).trimEnd();

const tasks = (count: number, titleLength: number): TaskBrief[] =>
  Array.from({ length: count }, (_, i) => ({
    id: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
    title: words(titleLength, `Task${i}`),
    priority: 'NORMAL',
    project: 'Engineering',
    dueAt: new Date(NOW.getTime() + i * 3_600_000),
    openSubtasks: 1,
  }));

const extras = {
  events: Array.from({ length: 30 }, (_, i) => ({ title: words(40, `Event${i}`), startAt: new Date(NOW.getTime() + i * 7_200_000), endAt: new Date(NOW.getTime() + i * 7_200_000 + 3_600_000), allDay: false })),
  reminders: Array.from({ length: 20 }, (_, i) => ({ title: words(40, `Remind${i}`), remindAt: new Date(NOW.getTime() + i * 3_600_000), repeatRule: null })),
  projects: Array.from({ length: 10 }, (_, i) => ({ id: `p${i}`, name: `Project ${i}` })),
};

const memoryWith = (facts: number, factLength: number): PromptMemory => ({
  facts: Array.from({ length: facts }, (_, i) => ({ id: `id${i}`, text: words(factLength, String(i).padStart(3, '0')), category: 'OTHER' as const })),
  summary: words(1200, 'Summary'),
  recent: Array.from({ length: 10 }, (_, i) => ({ who: i % 2 ? ('assistant' as const) : ('owner' as const), text: words(400, `Turn${i}`) })),
  actions: Array.from({ length: 8 }, (_, i) => ({ kind: 'CREATE_TASK', title: words(40, `Action${i}`), outcome: 'pending' as const })),
});

describe('prompt budget', () => {
  it('keeps the facts inside their character budget, newest first, even with 100 maximum-length facts', () => {
    const [, user] = buildAssistantMessages('q', [], NOW, 'UTC', extras, memoryWith(100, 200));
    const memory = JSON.parse(user!.content).memory as { id: string; text: string }[];

    const total = memory.reduce((n, f) => n + f.text.length, 0);
    expect(total).toBeLessThanOrEqual(MAX_MEMORY_PROMPT_CHARS);
    expect(memory.length).toBeGreaterThan(20);
    expect(memory.length).toBeLessThan(100);
    expect(memory.map((f) => f.id)).toEqual(Array.from({ length: memory.length }, (_, i) => `id${i}`)); // the front of the list: the newest
  });

  it('shows every fact when they fit the budget', () => {
    const [, user] = buildAssistantMessages('q', [], NOW, 'UTC', extras, memoryWith(40, 60));
    expect(JSON.parse(user!.content).memory).toHaveLength(40);
  });

  it('a realistic worst case (100 facts of 80 characters, a full summary, 10 long turns, every list at its cap) stays under 32,000 characters (about 8,000 tokens)', () => {
    const messages = buildAssistantMessages('What should I do first today?', tasks(40, 40), NOW, 'Asia/Kolkata', extras, memoryWith(100, 80));
    expect(size(messages)).toBeLessThan(32_000);
  });

  it('even with maximum-length facts and 120-character task titles the prompt stays under 40,000 characters', () => {
    const messages = buildAssistantMessages('q', tasks(40, 120), NOW, 'UTC', extras, memoryWith(100, 200));
    expect(size(messages)).toBeLessThan(40_000);
  });

  it('starts with the same system text every time, whatever the memory', () => {
    const a = buildAssistantMessages('one', tasks(2, 20), NOW, 'UTC', extras, memoryWith(3, 50));
    const b = buildAssistantMessages('two', tasks(5, 30), NOW, 'Asia/Kolkata', extras, memoryWith(80, 150));
    expect(a[0]!.role).toBe('system');
    expect(a[0]!.content).toBe(b[0]!.content);
  });
});
