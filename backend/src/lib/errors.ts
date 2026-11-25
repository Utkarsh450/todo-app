/** Domain error carrying the HTTP status the API should answer with. */
export class AppError extends Error {
  constructor(
    readonly statusCode: number,
    message: string,
    readonly code: string = 'APP_ERROR',
  ) {
    super(message);
    this.name = 'AppError';
  }
}

/** The API key is missing or wrong. Deliberately says nothing about which. */
export const unauthorized = () => new AppError(401, 'A valid API key is required.', 'UNAUTHORIZED');

export const notFound = (what: string) => new AppError(404, `${what} not found`, 'NOT_FOUND');

const MEMORY_REFUSALS = {
  empty: 'There is nothing to remember in that.',
  too_long: 'That is too long to remember (200 characters at most).',
  secret: 'I do not keep passwords, keys, tokens or card and ID numbers.',
  command: 'I keep facts about you, not rules for how I should behave.',
  full: 'Memory is full (100 facts). Delete one first.',
} as const;

/** A fact the assistant will not keep. The message says why. */
export const memoryRefused = (reason: keyof typeof MEMORY_REFUSALS) => new AppError(422, MEMORY_REFUSALS[reason], 'MEMORY_REFUSED');

/** The AI features need a key; say exactly how to add one. */
export const aiNotConfigured = () =>
  new AppError(503, 'AI is not set up. Add XAI_API_KEY to backend/.env and restart the server.', 'AI_NOT_CONFIGURED');
/** The AI service answered with an error or could not be reached. The message is safe to show. */
export const aiUpstream = (message: string) => new AppError(502, message, 'AI_UPSTREAM_ERROR');
/** The AI service answered, but not with something usable. */
export const aiBadResponse = (message: string) => new AppError(502, message, 'AI_BAD_RESPONSE');
export const aiTimeout = () => new AppError(504, 'The AI service took too long to answer. Try again.', 'AI_TIMEOUT');

/**
 * Turns well-known database errors into client errors instead of a 500: a reference to something that does not
 * exist (a project, task ... id that was never created or was deleted), a missing row, or a duplicate name.
 */
export function databaseProblem(error: unknown): { statusCode: number; code: string; message: string } | null {
  const code = (error as { code?: unknown } | null)?.code;
  if (code === 'P2003') return { statusCode: 400, code: 'INVALID_REFERENCE', message: 'That refers to something that does not exist' };
  if (code === 'P2025') return { statusCode: 404, code: 'NOT_FOUND', message: 'Not found' };
  if (code === 'P2002') return { statusCode: 409, code: 'CONFLICT', message: 'That already exists' };
  return null;
}
