/**
 * What the assistant may remember about the owner, and the rules a fact must pass before it is saved.
 * Everything here is pure: no database, no model. A fact that does not hold up is dropped quietly, never an error,
 * and a broken `remember` part of a reply can never stop the reply itself.
 */

export const MAX_MEMORY_TEXT = 200;
export const MAX_MEMORIES = 100;
export const MAX_NEW_FACTS = 3;
/** Most facts one reply may forget: text planted in a task or a message must not be able to wipe the memory. */
export const MAX_FORGET = 3;

export const MEMORY_CATEGORIES = ['ROUTINE', 'PREFERENCE', 'PERSON', 'COMMITMENT', 'OTHER'] as const;
export type MemoryCategory = (typeof MEMORY_CATEGORIES)[number];

/** A saved fact as the model is shown it (the id lets it ask to forget one). */
export interface MemoryBrief {
  id: string;
  text: string;
  category: MemoryCategory;
}

export interface NewFact {
  text: string;
  category: MemoryCategory;
}

export interface MemoryOps {
  remember: NewFact[];
  forget: string[];
}

export type FactCheck = { ok: true; fact: NewFact } | { ok: false; reason: 'empty' | 'too_long' | 'secret' | 'command' };

const SECRET_PATTERNS: RegExp[] = [
  /\b(pass(word|code)|pin|api[ _-]?key|secret|token|bearer|otp|cvv)\b/i,
  /\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b/, // 16-digit card number
  /\b\d{4}[ -]?\d{4}[ -]?\d{4}\b/, // 12-digit id number
  /\b(gsk_|sk-|xai-)[A-Za-z0-9_-]{10,}/, // provider key prefixes
  /\b[A-Za-z0-9_-]{32,}\b/, // a long unbroken random-looking string
];

const COMMAND_PATTERNS: RegExp[] = [
  /\bwithout (asking|confirm\w*|my approval|my permission)\b/i,
  /\b(never|don'?t|do not|stop) ask(ing)?\b/i,
  /\b(ignore|disregard|forget) (all |the |your |any )?(rules|instructions|guidelines|previous)\b/i,
  /\bauto[- ]?(accept|approve|apply)\b/i,
  /\bfrom now on\b.*\b(you|assistant)\b/i,
];

function asCategory(value: unknown): MemoryCategory {
  const upper = typeof value === 'string' ? value.trim().toUpperCase() : '';
  return (MEMORY_CATEGORIES as readonly string[]).includes(upper) ? (upper as MemoryCategory) : 'OTHER';
}

/** Checks one fact. The text is trimmed and its white space collapsed; secrets and orders to the AI are refused. */
export function validateFact(text: unknown, category?: unknown): FactCheck {
  if (typeof text !== 'string') return { ok: false, reason: 'empty' };
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean === '') return { ok: false, reason: 'empty' };
  if (clean.length > MAX_MEMORY_TEXT) return { ok: false, reason: 'too_long' };
  if (SECRET_PATTERNS.some((p) => p.test(clean))) return { ok: false, reason: 'secret' };
  if (COMMAND_PATTERNS.some((p) => p.test(clean))) return { ok: false, reason: 'command' };
  return { ok: true, fact: { text: clean, category: asCategory(category) } };
}

function words(text: string): Set<string> {
  return new Set(text.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(Boolean));
}

/** The same fact said again: same words ignoring case, punctuation and order (overlap of at least 0.8). */
export function isNearDuplicate(a: string, b: string): boolean {
  const left = words(a);
  const right = words(b);
  if (left.size === 0 || right.size === 0) return false;
  let shared = 0;
  for (const w of left) if (right.has(w)) shared += 1;
  return shared / (left.size + right.size - shared) >= 0.8;
}

/** The JSON object inside a model answer (it may sit in code fences or chatter), or null. */
export function extractJsonObject(raw: string): Record<string, unknown> | null {
  const text = raw.replace(/```(?:json)?/gi, '');
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start === -1 || end <= start) return null;
  try {
    const value: unknown = JSON.parse(text.slice(start, end + 1));
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Reads `remember` and `forget` out of a model answer. Never throws; anything unusable is left out. */
export function parseMemoryOps(raw: string, known: MemoryBrief[]): MemoryOps {
  const obj = extractJsonObject(raw);
  const ops: MemoryOps = { remember: [], forget: [] };
  if (!obj) return ops;

  if (Array.isArray(obj.remember)) {
    const seen = known.map((k) => k.text);
    for (const item of obj.remember) {
      if (ops.remember.length === MAX_NEW_FACTS) break;
      if (typeof item !== 'object' || item === null) continue;
      const entry = item as { text?: unknown; category?: unknown };
      const check = validateFact(entry.text, entry.category);
      if (!check.ok) continue;
      if (seen.some((s) => isNearDuplicate(s, check.fact.text))) continue;
      seen.push(check.fact.text);
      ops.remember.push(check.fact);
    }
  }

  if (Array.isArray(obj.forget)) {
    const ids = new Set(known.map((k) => k.id));
    for (const id of obj.forget) {
      if (ops.forget.length === MAX_FORGET) break;
      if (typeof id === 'string' && ids.has(id) && !ops.forget.includes(id)) ops.forget.push(id);
    }
  }
  return ops;
}
