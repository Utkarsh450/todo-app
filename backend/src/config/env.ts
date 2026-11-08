import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  /** Loopback by default: the Android emulator reaches it at 10.0.2.2. Use 0.0.0.0 to let a phone on your Wi-Fi connect. */
  HOST: z.string().default('127.0.0.1'),
  PORT: z.coerce.number().int().min(0).max(65535).default(3000),
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'http', 'debug']).default('info'),
  DATABASE_URL: z.string().url(),
  /** Browser origins allowed to call the API, comma separated. The Android app is not a browser, so the default allows none. */
  CORS_ORIGIN: z
    .string()
    .optional()
    .transform((v) => (v ?? '').split(',').map((o) => o.trim()).filter(Boolean)),
  /** Grok (xAI). Optional: without a key the AI features answer "not set up" and everything else works. */
  XAI_API_KEY: z.string().trim().optional().transform((v) => v || undefined),
  XAI_BASE_URL: z.string().url().default('https://api.x.ai/v1'),
  XAI_MODEL: z.string().trim().min(1).default('grok-4.7'),
  /** Reasoning models can take a while; this is the most we wait for one answer. */
  XAI_TIMEOUT_MS: z.coerce.number().int().min(1_000).max(300_000).default(60_000),
});

export type Env = z.infer<typeof envSchema>;

/** Validates raw environment variables once, failing fast with a readable message. */
export function parseEnv(source: Record<string, string | undefined>): Env {
  const result = envSchema.safeParse(source);
  if (!result.success) {
    const details = result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new Error(`Invalid environment configuration: ${details}`);
  }
  return result.data;
}
