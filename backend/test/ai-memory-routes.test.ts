import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import type { Db } from '../src/lib/prisma.js';
import type { AiClient } from '../src/modules/ai/ai.client.js';
import { MAX_MEMORIES } from '../src/modules/ai/memory.js';
import { createTestApp, resetDatabase } from './helpers.js';

// Needs the local PostgreSQL test database (paper_todo_test): run on the Mac or in CI.

/** Answers "Reply to <the question>" so each turn is recognisable in the history. */
const echo: AiClient = {
  configured: true,
  model: 'fake',
  complete: async (messages) => {
    const question = (JSON.parse(messages[1]!.content) as { question: string }).question;
    return JSON.stringify({ reply: `Reply to ${question}`, suggestions: [] });
  },
};

let app: FastifyInstance;
let db: Db;
const UNKNOWN = '00000000-0000-4000-8000-000000000000';

beforeAll(async () => {
  ({ app, db } = await createTestApp(echo));
});
afterAll(() => app.close());
beforeEach(() => resetDatabase(db));

const ask = (message: string) => app.inject({ method: 'POST', url: '/api/ai/assist', payload: { message } });
const history = (query = '') => app.inject({ method: 'GET', url: `/api/ai/history${query}` });

describe('history', () => {
  it('is empty at first, pages newest first, and the cursor continues to the end', async () => {
    expect((await history()).json()).toEqual({ messages: [], nextBefore: null });

    for (const q of ['q1', 'q2', 'q3']) expect((await ask(q)).statusCode).toBe(200);

    const first = (await history('?limit=4')).json();
    expect(first.messages.map((m: { content: string }) => m.content)).toEqual(['Reply to q3', 'q3', 'Reply to q2', 'q2']);
    expect(first.messages[0]).toMatchObject({ role: 'ASSISTANT', suggestions: [], outcomes: null });
    expect(first.messages[1]).toMatchObject({ role: 'USER', suggestions: null });
    expect(first.nextBefore).toBe(first.messages[3].id);

    const second = (await history(`?limit=4&before=${first.nextBefore}`)).json();
    expect(second.messages.map((m: { content: string }) => m.content)).toEqual(['Reply to q1', 'q1']);
    expect(second.nextBefore).toBeNull();
  });

  it('rejects a bad limit or cursor with 400', async () => {
    for (const query of ['?limit=0', '?limit=51', '?limit=abc', '?before=not-a-uuid']) {
      expect((await history(query)).statusCode).toBe(400);
    }
  });

  it('two asks at once give two distinct pairs, each reply right after its own question', async () => {
    const [a, b] = await Promise.all([ask('first'), ask('second')]);
    expect(a.json().messageId).not.toBe(b.json().messageId);

    const all = (await history('?limit=50')).json().messages.reverse() as { id: string; role: string; content: string; createdAt: string }[];
    expect(all).toHaveLength(4);
    expect(new Set(all.map((m) => m.id)).size).toBe(4);
    expect(new Set(all.map((m) => m.createdAt)).size).toBe(4);
    for (let i = 0; i < 4; i += 2) {
      expect(all[i]!.role).toBe('USER');
      expect(all[i + 1]).toMatchObject({ role: 'ASSISTANT', content: `Reply to ${all[i]!.content}` });
    }
  });

  it('DELETE clears the messages and the summary but keeps the memory', async () => {
    await ask('hello');
    await db.chatSummary.create({ data: { id: 1, text: 'Old notes' } });
    await db.memory.create({ data: { text: 'Wakes at 7', source: 'AUTO' } });

    expect((await app.inject({ method: 'DELETE', url: '/api/ai/history' })).statusCode).toBe(204);

    expect((await history()).json()).toEqual({ messages: [], nextBefore: null });
    expect(await db.chatSummary.count()).toBe(0);
    expect(await db.memory.count()).toBe(1);
  });
});

describe('outcome of a suggestion', () => {
  const outcome = (id: string, payload: unknown) => app.inject({ method: 'POST', url: `/api/ai/assist/${id}/outcome`, payload: payload as object });

  it('is stored for a real suggestion and 404 for an unknown message or index', async () => {
    const message = await db.chatMessage.create({ data: { role: 'ASSISTANT', content: 'Split it.', suggestions: [{ kind: 'SPLIT' }] } });

    expect((await outcome(message.id, { index: 0, outcome: 'accepted' })).statusCode).toBe(204);
    expect((await history()).json().messages[0].outcomes).toEqual({ '0': 'accepted' });

    expect((await outcome(message.id, { index: 3, outcome: 'skipped' })).statusCode).toBe(404);
    expect((await outcome(UNKNOWN, { index: 0, outcome: 'skipped' })).statusCode).toBe(404);
  });

  it('rejects a malformed body with 400', async () => {
    const message = await db.chatMessage.create({ data: { role: 'ASSISTANT', content: 'x', suggestions: [{ kind: 'SPLIT' }] } });
    for (const payload of [{}, { index: -1, outcome: 'accepted' }, { index: 0, outcome: 'maybe' }, { index: 1.5, outcome: 'skipped' }]) {
      expect((await outcome(message.id, payload)).statusCode).toBe(400);
    }
  });
});

describe('memories', () => {
  const post = (text: unknown) => app.inject({ method: 'POST', url: '/api/ai/memories', payload: { text } });

  it('can be added, listed, edited and deleted', async () => {
    const created = await post('Wakes at 7');
    expect(created.statusCode).toBe(201);
    expect(created.json()).toEqual({ id: expect.any(String), text: 'Wakes at 7', category: 'OTHER' });
    const id = created.json().id as string;

    const list = (await app.inject({ method: 'GET', url: '/api/ai/memories' })).json();
    expect(list.memories).toEqual([{ id, text: 'Wakes at 7', category: 'OTHER', source: 'USER', updatedAt: expect.any(String) }]);

    const patched = await app.inject({ method: 'PATCH', url: `/api/ai/memories/${id}`, payload: { text: 'Wakes at 6' } });
    expect(patched.statusCode).toBe(200);
    expect(patched.json().text).toBe('Wakes at 6');

    expect((await app.inject({ method: 'DELETE', url: `/api/ai/memories/${id}` })).statusCode).toBe(204);
    expect((await app.inject({ method: 'DELETE', url: `/api/ai/memories/${id}` })).statusCode).toBe(404);
    expect((await app.inject({ method: 'PATCH', url: `/api/ai/memories/${UNKNOWN}`, payload: { text: 'x y' } })).statusCode).toBe(404);
  });

  it('refuses a secret with 422 whether it is added or edited', async () => {
    const refused = await post('The wifi password is hunter2');
    expect(refused.statusCode).toBe(422);
    expect(refused.json().error).toBe('MEMORY_REFUSED');

    const id = (await post('Wakes at 7')).json().id as string;
    const edit = await app.inject({ method: 'PATCH', url: `/api/ai/memories/${id}`, payload: { text: 'Use Bearer abcdef123456' } });
    expect(edit.statusCode).toBe(422);
    expect(await db.memory.findUniqueOrThrow({ where: { id } })).toMatchObject({ text: 'Wakes at 7' });
  });

  it('refuses the 101st fact with 422', async () => {
    await db.memory.createMany({ data: Array.from({ length: MAX_MEMORIES }, (_, i) => ({ text: `Fact number${i} about subject${i}`, source: 'AUTO' as const })) });
    const res = await post('Something completely different');
    expect(res.statusCode).toBe(422);
    expect(await db.memory.count()).toBe(MAX_MEMORIES);
  });

  it('can all be forgotten at once', async () => {
    await post('Wakes at 7');
    await post('Likes tea');
    expect((await app.inject({ method: 'DELETE', url: '/api/ai/memories' })).statusCode).toBe(204);
    expect((await app.inject({ method: 'GET', url: '/api/ai/memories' })).json()).toEqual({ memories: [] });
  });

  it('rejects an empty or missing text with 400', async () => {
    for (const text of ['', '   ', undefined, 5]) expect((await post(text)).statusCode).toBe(400);
  });
});
