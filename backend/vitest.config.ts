import { defineConfig } from 'vitest/config';
import { config } from 'dotenv';

config({ path: '.env.test', override: true });

export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    // Integration tests share one Postgres database, so run files one at a time.
    fileParallelism: false,
    setupFiles: ['test/setup.ts'],
  },
});
