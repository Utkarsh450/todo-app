import { describe, expect, it } from 'vitest';
import { jsonSafe } from '../src/modules/ai/chat.service.js';

describe('jsonSafe', () => {
  it('turns dates inside suggestions into ISO strings so they can be stored in a JSON column', () => {
    const when = new Date('2026-11-09T03:30:00.000Z');
    const safe = jsonSafe([{ kind: 'MOVE', taskId: 't', dueAt: when, steps: ['a'], nothing: undefined }]);
    expect(safe).toEqual([{ kind: 'MOVE', taskId: 't', dueAt: '2026-11-09T03:30:00.000Z', steps: ['a'] }]);
  });

  it('keeps an empty list empty', () => {
    expect(jsonSafe([])).toEqual([]);
  });
});
