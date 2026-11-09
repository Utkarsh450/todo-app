import { timingSafeEqual } from 'node:crypto';

/** Compares the `X-Api-Key` header with the configured key without leaking, through timing, how much of it was right. */
export function apiKeyMatches(header: string | string[] | undefined, expected: string): boolean {
  if (typeof header !== 'string') return false;
  const given = Buffer.from(header);
  const wanted = Buffer.from(expected);
  return given.length === wanted.length && timingSafeEqual(given, wanted);
}
