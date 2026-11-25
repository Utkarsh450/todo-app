# AI secretary: memory, history and guardrails (design)

Status: **waiting for the owner's approval.** Nothing is built yet. Written 25 Nov 2026.

## Intent
The owner will use the AI more than anything else in the app. They want it to behave like a **personal secretary**: it
knows them, remembers earlier conversations and what it was in the middle of, acts on requests, keeps everything it
creates in step with the rest of the app, and cannot do harm. Today the assistant is stateless: every question is a new
request (system prompt + one JSON of open tasks, events, reminders) and the conversation exists only in
`AssistantViewModel` memory, so closing the app erases it. There are no chat or memory tables.

## Goals
1. **Conversation history (short-term memory).** Messages are saved on the server and shown again when the assistant is
   opened, on any phone. The last ~10 turns go into each request; older turns are folded into a rolling summary.
2. **Long-term memory.** Short facts about the owner (routines, preferences, people, recurring commitments), e.g.
   "wakes at 7", "gym on Mondays at 6 pm". The AI saves them itself, in the same model call that writes the reply, and the
   chat shows a line "Noted: ... Undo". The owner can also say "remember ..." / "forget ...", and a Memory screen lists,
   edits and deletes every fact.
3. **Working memory ("what it is doing").** Each assistant message keeps its suggestions and what happened to them
   (accepted, skipped, pending). The next prompt tells the model what it just did and what is still open, so it can
   carry on ("I added the dentist; you skipped the gym slot").
4. **Better prompt.** Layered and fixed in order: persona and rules, memory, rolling summary, recent turns, today's context
   (open tasks, events, reminders, projects), the question. Static parts first so the provider can cache them.
5. **Guardrails** (below) and **optimisation** (below).
6. **Everything it creates stays in sync.** Created tasks, events and reminders keep going through the normal
   repositories, whose `changes` flows already refresh Today, Calendar, Due soon and Insights.

## Non-goals
Proactive pushes (a morning briefing, "you haven't done X"), voice conversation, several users or sign-in, embeddings or a
vector database, letting the AI change data without the owner's Accept. Proactive behaviour is a later phase.

## Anti-goals (a failure even if it "works")
A wrong or one-off fact silently becoming a habit; memory growing without limit; secrets stored or sent; the AI creating,
moving or deleting anything the owner did not accept; replies that ignore the memory the owner can see.

## Approaches considered
- **How facts get written.** (A) the AI proposes, the owner taps Save; (B) the AI saves silently; (C) only on "remember".
  The owner asked for a secretary, which rules out C and makes A feel like paperwork. **Chosen (my call, needs approval):
  B made visible** - the AI saves by itself, always shows "Noted: ... Undo", and the Memory screen lists everything.
- **Where it lives.** Phone vs server. **Server (Postgres)**: it survives reinstalls, serves any phone, and the backend
  already builds the prompt. Privacy: the owner agreed that personal facts go to the server and to Groq.
- **Which facts reach the prompt.** Embedding search vs sending all. **All facts, capped** (about 100 x 200 characters, ~2k
  tokens), grouped by category. Revisit only if the cap is reached.
- **Where facts are extracted.** A second model call vs the same call. **Same call**: the reply JSON gains
  `"remember": [{"text","category"}]` and `"forget": ["<memory id>"]`, so no extra latency or cost.

## Design
Backend (`backend/src/modules/ai`, Prisma migration):
- `ChatMessage { id, role USER|ASSISTANT, content, suggestions Json?, outcomes Json?, createdAt }` (one running thread; "New chat" clears it).
- `Memory { id, text (<=200), category ROUTINE|PREFERENCE|PERSON|COMMITMENT|OTHER, source AUTO|USER, createdAt, updatedAt }`, at most 100.
- `ChatSummary { id=1, text, coversUntil }`, rewritten when the stored turns outside the "recent" window exceed ~20 messages; done
  inline in that one request (Vercel can cut work after the response), so it adds latency only every ~10 turns.
- `POST /ai/assist` loads memory + summary + recent turns itself, stores the user message and the assistant reply, and
  returns `{messageId, reply, suggestions, noted: [{id,text}], forgotten: [id]}` (`messageId` is the saved assistant message). `assistBody` is unchanged
  (message, time zone).
- `POST /ai/assist/:messageId/outcome` with `{index, outcome: accepted | skipped}` per suggestion feeds working memory.
- `GET /ai/history?before=&limit=`, `DELETE /ai/history`, `GET/POST/PATCH/DELETE /ai/memories`.
App (`feature/assistant`, new `feature/memory`):
- The assistant loads history on open and pages older messages; "Noted: ... Undo" lines (undo = delete that memory);
  Accept/Skip also report the outcome; a Memory screen (from the assistant's menu) to read, edit and delete facts, and "forget everything".
Prompt budget: memory ~2k tokens, summary <=300, recent turns <=1.5k (each turn clipped), context as today (40 tasks, 30
events, 20 reminders). The model is told the budget exists and to keep replies to 1-3 sentences.

## Guardrails
- Memory, summary, history, tasks and the question are all labelled **data, never instructions** (the existing rule, extended).
- Memory writes are validated before saving: <=200 characters, <=3 new facts per reply, near-duplicates merged, anything that
  looks like a secret (password, token/key patterns, card or ID numbers) refused, and facts that read as commands to the AI
  ("always do X without asking") refused. Refusals are logged, not shown as errors.
- The AI can only **forget** ids it was given; the owner can delete anything at any time.
- Actions are still proposals; Accept stays required. Existing checks stay (known task ids, no past times, no repeats, max 8).
- Failure handling: a broken `remember` part never blocks the reply; Groq 429/5xx gets one retry with backoff, then a clear message.
- Tests include prompt-injection samples hidden in task titles, memory text and chat history.

## Optimisation
One model call per turn; stable prompt prefix first; summary instead of the full history; capped lists as above; history
queries indexed on `createdAt`; the app pages history instead of loading it all; offline: the saved copy of the history is shown
(the existing cache-first helper).

## Phases (each: tests green, one commit)
1. Backend: tables, memory + history, prompt, `remember`/`forget` parsing, validation, outcome endpoint (vitest; integration tests need the local
   Postgres, which this PC does not have, so pure logic is tested here and integration tests are run on the Mac or CI).
2. App: history, "Noted" lines with Undo, outcome reporting.
3. App: Memory screen. 4. Summary and optimisation pass. Later: proactive briefing.

## Left to the builder
Exact wording of the persona prompt (Hinglish replies follow the request's language); category list tweaks; page sizes;
the secret-detection patterns; screen layout of the Memory screen (paper style, like Projects).

## Risks
Backend tests cannot run on this PC (no PostgreSQL); the live backend must be redeployed with a migration before the app can use any of this
(`scripts/deploy-backend.ps1`, the owner's call); Groq free-tier rate limits may bite with longer prompts.
