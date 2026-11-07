import { describe, expect, it } from 'vitest';
import { parseEnv } from '../src/config/env.js';

describe('parseEnv', () => {
  it('applies defaults and coerces the port', () => {
    const env = parseEnv({ DATABASE_URL: 'postgresql://u@localhost:5432/db', PORT: '4000' });
    expect(env).toMatchObject({ NODE_ENV: 'development', HOST: '0.0.0.0', PORT: 4000, LOG_LEVEL: 'info' });
  });

  it('rejects a missing database url with a readable message', () => {
    expect(() => parseEnv({})).toThrow(/DATABASE_URL/);
  });

  it('rejects an unknown log level', () => {
    expect(() => parseEnv({ DATABASE_URL: 'postgresql://u@localhost/db', LOG_LEVEL: 'loud' })).toThrow(/LOG_LEVEL/);
  });
});
