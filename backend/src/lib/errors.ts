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
