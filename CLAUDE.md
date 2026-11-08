# Paper Todo — working notes for Claude sessions

Android (Kotlin + Jetpack Compose) todo app with a Fastify/TypeScript/Prisma/PostgreSQL backend.
Visual design is the "Paper System" (see **Design** below). Read this file first, then `README.md`.

## Hard rules from the owner
- **Commit identity:** `Utkarhs450 <ubarnwal0802@gmail.com>`. The repo already has this set locally
  (`git config user.name/user.email`); verify with `git log -1 --format='%an <%ae>'` before committing.
- **One commit per finished feature, with its tests.** A feature is not done until its **unit tests, integration
  tests, and (for UI) emulator tests all pass**. Do not commit untested features.
- Commit messages: `type(scope): summary` + short body. End with the attribution line the session instructs.
- Best practices + **feature-based folder structure** (below). Backend is **pnpm + Fastify + TypeScript 7 + Prisma +
  winston + local PostgreSQL**. Tests for the app run **on the Android emulator** as well as the JVM.
- Do not push/force-push or touch other databases without being asked. Remote: `origin` = `Utkarsh450/todo-app`.

## Layout
```
app/src/main/java/com/utkarsh/todo/
  core/            common (Clock, DayRange, resultOf), designsystem (theme/, component/, icon/), navigation, network
  feature/<name>/  domain/ (models, repository interfaces, pure logic)  data/ (Retrofit api, repo impl, Hilt module)  ui/ (ViewModel + Compose)
    projects tasks quickadd today taskdetail reminders calendar habits notes insights customize
backend/src/
  app.ts server.ts  config/env.ts  lib/ (logger=winston, prisma, errors)
  modules/<name>/ (*.schema.ts zod, *.service.ts, *.routes.ts)   health projects tasks reminders calendar habits notes insights
backend/prisma/   schema.prisma, migrations/, seed.ts   backend/test/  vitest (unit + integration via app.inject)
```
Pattern to follow for a new feature: domain interface -> data layer (Retrofit + `resultOf`) -> `@HiltViewModel` exposing a
`StateFlow<UiState>` -> stateless `XScreen(state, callbacks)` + thin `XRoute` that calls `hiltViewModel()`.
Keep screens Hilt-free so they can be tested with fake state; wire extra blocks through slots (see `TodayScreen`).

## Environment (this Mac)
- **Use JDK 21** for Gradle: `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13+9/Contents/Home"`.
  Android Studio's bundled JBR is JDK 25 and **Gradle 8.14 fails on it** (`IllegalArgumentException: 25.0.3`).
- Android SDK: `~/Library/Android/sdk` (compileSdk 36). Emulator AVD `Pixel_6` is normally already running; `adb devices`.
- The emulator reaches the host backend at `http://10.0.2.2:3000/api/` (BuildConfig `API_BASE_URL`; override `-PapiBaseUrl=`).
- Kotlin warnings are errors (`allWarningsAsErrors`). Fix the warning, don't suppress it.
- The disk was nearly full once (a build failed with "No space left on device"). If a Gradle step dies oddly, run `df -h`.

## Commands
```bash
# Android (from repo root)
./gradlew :app:testDebugUnitTest            # JVM unit + MockWebServer integration tests
./gradlew :app:connectedDebugAndroidTest    # Compose UI + on-device AlarmManager/notification tests (emulator must be up)
./gradlew :app:installDebug                 # install on the emulator
# Backend (from backend/)
pnpm install && pnpm prisma:generate
pnpm dev                                    # tsx watch, http://localhost:3000/api/health
pnpm typecheck && pnpm test                 # tsc (TypeScript 7) + vitest against paper_todo_test
pnpm build && node --env-file=.env dist/server.js
pnpm prisma:migrate / prisma:deploy / prisma:seed
```
Result files: `app/build/test-results/testDebugUnitTest/*.xml`, `app/build/outputs/androidTest-results/connected/debug/*.xml`.
Test counts at last run: backend 57, Android unit 156, emulator 71 — all green.

## Database
Local PostgreSQL 16 (Homebrew), browse with pgAdmin. Databases: `paper_todo` (dev) and `paper_todo_test` (tests; the
test helper **refuses to run unless the URL contains `_test`**). User `etech`, no password (`backend/.env`, not committed;
`.env.example` / `.env.test` are). A different `todoapp` database exists on this server and belongs to something else — leave it alone.
Prisma 7: client is generated to `backend/src/generated/prisma` (git-ignored; `pnpm prisma:generate` after install), uses the
`@prisma/adapter-pg` driver adapter and `prisma.config.ts`. Node 26 works despite Prisma's "supported versions" notice.
Dates in API: ISO strings; day-only values `YYYY-MM-DD`. Day boundaries are computed **by the client** (timezone-safe).

## Design (Paper System)
Source file was a private claude.ai design ("Todo Components.dc.html"), unpacked from a shared bundle. Tokens live in
`core/designsystem/theme`: paper `#F4F0E7`, card `#FFFDF8`, rule `#E3DCCD`, ink `#2B2824`, muted `#8A8377`, accent
vermilion `#C4532F`. Fonts: Newsreader (serif), Schibsted Grotesk (sans), IBM Plex Mono (labels) — currently system
fallbacks (`PaperFonts` in `Type.kt`); bundle the real fonts or use `ui-text-google-fonts` to finish the look.
Screens: Today, Task page, Quick add, Planner (= Calendar "Week"), Calendar, Reminders, Insights, Customize.

## Status
Done + tested + pushed: backend (all modules), design system, networking, projects, tasks, quick add (Task only), Today
(blocks/plan, swipe+undo, habits, scratchpad), task detail, reminders (scheduling, repeat rules, boot receiver, UI),
calendar (month + week timeline), insights, **customize** (Today block show/hide + reorder, paper background
dots/lines/plain, cozy/compact density; DataStore-backed `SettingsRepository`; gear icon on Today opens it).

**Remaining work (suggested order):**
1. Quick add **Note** and **Event** types (design has the Task/Note/Event segmented control): Event -> `CalendarRepository.createEvent`
   (already implemented), Note -> `NotesRepository.save`.
2. Task detail: REMIND row (create a reminder 1h before due), notes editing, delete confirmation dialog, focus timer ("Focus · 25:00").
3. Real fonts (see Design), launcher icon, release signing/R8 check, CI workflow.
4. Offline support (currently every screen needs the backend), auth if the API is ever exposed (it is unauthenticated, single-user).
5. Project management UI (API + repository exist; no screen yet).
6. A true end-to-end instrumented test (Hilt test app + real backend) — today E2E was verified manually with `adb` + screenshots.

## Gotchas learned
- Compose `performClick` on a tiny non-clickable node can hit a clickable neighbour (min touch target expansion); assert click actions instead.
- `PendingIntent.cancel()` is needed in addition to `AlarmManager.cancel()` or a stale token lingers.
- Notifications post asynchronously — poll in tests; use distinct ids per test.
- `MonoLabel` uppercases text, so tests must look for the uppercase string.
- Material `SwipeToDismissBox(confirmValueChange=…)` is deprecated; `SwipeActionRow` fires from `currentValue` instead.
- `paperSurface()` replaces a flat `background(colors.paper)` on full-screen surfaces; use it on any new screen so the Customize
  paper pattern shows. `PaperTheme(density, pattern)` is fed from `SettingsRepository` in `MainActivity`.
- `AppSettings.blockOrder` must list every `TodayBlock` exactly once (enforced in `init`); hidden blocks keep their slot.
  `hiddenBlocks` absent in DataStore means "never saved" (design default: scratchpad off), an empty set means "show all".
- DataStore files must be opened once per process: in tests cancel the first store's scope before reopening the same file.
- A failed action must not have its error message cleared by the recovery reload (`TodayViewModel.load(clearError=false)`).
- `adb shell input text '#x'` types `%23` unless quoted as `"input text '#x'"`.
