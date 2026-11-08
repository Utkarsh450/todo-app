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

export const notFound = (what: string) => new AppError(404, `${what} not found`, 'NOT_FOUND');

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
