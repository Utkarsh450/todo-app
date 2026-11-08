# Paper Todo

A todo app with reminders, a calendar planner, habits and insights, styled after the "Paper System" design.

- **App:** Kotlin, Jetpack Compose, Hilt, Retrofit + kotlinx.serialization, AlarmManager reminders. Feature-based packages.
- **Backend:** Node, Fastify 5, TypeScript 7, Prisma 7, PostgreSQL, winston, zod. Managed with pnpm.

## Features
Today (pinned card, task list with swipe to move/archive and undo, Plan view, habit tracker, scratchpad) ·
Task page with subtasks · Quick add (Task, Note or Event) that parses `fri 3pm #Design !high every monday` · Reminders (grouped, snooze, repeat,
real device alarms, notifications, survive reboot) · Calendar month grid + week timeline · Insights heat-map, streak and
weekly rhythm · Customize (choose, reorder and hide Today blocks, paper texture, row density) · **Break it down**
(on a task page, Grok suggests subtasks; you untick the ones you don't want, nothing is added until you confirm).

## AI (Grok)
AI calls go through the backend, so the key never lives on the phone. Get a key at <https://console.x.ai>, then put it
in `backend/.env` and restart the backend:
```
XAI_API_KEY=your-key
# optional: XAI_MODEL=grok-4.7   XAI_BASE_URL=https://api.x.ai/v1   XAI_TIMEOUT_MS=60000
```
Without a key everything else works; the Break it down sheet just says Grok isn't set up. If xAI renames the model,
change `XAI_MODEL`. Never commit `.env`.

## Run it
1. **Database** — PostgreSQL running locally (view it in pgAdmin). Create the databases once:
   `createdb paper_todo && createdb paper_todo_test`
2. **Backend**
   ```bash
   cd backend
   cp .env.example .env            # adjust DATABASE_URL if your Postgres user differs
   pnpm install
   pnpm prisma:deploy && pnpm prisma:seed
   pnpm dev                        # http://localhost:3000/api/health
   ```
3. **Android** — JDK 17–21 (not 25), Android SDK 36, a running emulator.
   ```bash
   export JAVA_HOME=/path/to/jdk21
   ./gradlew :app:installDebug     # the emulator reaches the backend at http://10.0.2.2:3000/api/
   ```
   For a physical device: `./gradlew :app:installDebug -PapiBaseUrl=http://<your-LAN-ip>:3000/api/`.

## Test it
```bash
cd backend && pnpm typecheck && pnpm test                 # unit + integration (paper_todo_test)
./gradlew :app:testDebugUnitTest                          # JVM unit + MockWebServer integration
./gradlew :app:connectedDebugAndroidTest                  # Compose UI + AlarmManager/notification tests on the emulator
```

## API (prefix `/api`)
`GET /health` · `projects` · `tasks` (+ `/complete`, `/reopen`, `/subtasks`) · `subtasks/:id` · `reminders`
(+ `/grouped`, `/:id/snooze`) · `events` · `calendar/agenda` · `habits` (+ `/:id/logs/:date`) · `notes/:date` ·
`insights/heatmap` · `ai/status` · `ai/breakdown`. Request/response shapes are the zod schemas in `backend/src/modules/*/*.schema.ts`.

## Layout
See `CLAUDE.md` for the architecture, conventions, environment gotchas and the remaining-work list.
