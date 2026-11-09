import { describe, expect, it } from 'vitest';
import { parseEnv } from '../src/config/env.js';

describe('parseEnv', () => {
  it('applies defaults and coerces the port', () => {
    const env = parseEnv({ DATABASE_URL: 'postgresql://u@localhost:5432/db', PORT: '4000' });
    expect(env).toMatchObject({ NODE_ENV: 'development', HOST: '127.0.0.1', PORT: 4000, LOG_LEVEL: 'info' });
  });

  it('rejects a missing database url with a readable message', () => {
    expect(() => parseEnv({})).toThrow(/DATABASE_URL/);
  });

  it('rejects an unknown log level', () => {
    expect(() => parseEnv({ DATABASE_URL: 'postgresql://u@localhost/db', LOG_LEVEL: 'loud' })).toThrow(/LOG_LEVEL/);
  });

  it('has sensible defaults for the AI settings and treats an empty key as unset', () => {
    const base = { DATABASE_URL: 'postgresql://u@localhost/db' };
    expect(parseEnv(base).XAI_API_KEY).toBeUndefined();
    expect(parseEnv(base)).toMatchObject({ XAI_BASE_URL: 'https://api.x.ai/v1', XAI_MODEL: 'grok-4.7', XAI_TIMEOUT_MS: 60000 });
    expect(parseEnv({ ...base, XAI_API_KEY: '' }).XAI_API_KEY).toBeUndefined();
    expect(parseEnv({ ...base, XAI_API_KEY: ' k ', XAI_MODEL: 'grok-x', XAI_TIMEOUT_MS: '5000' })).toMatchObject({ XAI_API_KEY: 'k', XAI_MODEL: 'grok-x', XAI_TIMEOUT_MS: 5000 });
  });
});

describe('API_KEY', () => {
  const base = { DATABASE_URL: 'postgresql://u@localhost/db' };
  it('is off by default and treats an empty value as unset', () => {
    expect(parseEnv(base).API_KEY).toBeUndefined();
    expect(parseEnv({ ...base, API_KEY: '' }).API_KEY).toBeUndefined();
  });
  it('accepts a long key and refuses a short one', () => {
    expect(parseEnv({ ...base, API_KEY: ' 0123456789abcdef ' }).API_KEY).toBe('0123456789abcdef');
    expect(() => parseEnv({ ...base, API_KEY: 'short' })).toThrow(/API_KEY/);
  });
});
