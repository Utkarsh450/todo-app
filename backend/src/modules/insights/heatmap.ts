export interface DayCount { date: string; count: number }
export interface HeatmapSummary { total: number; streak: number; best: number; rhythm: number[] }

const DAY_MS = 86_400_000;
const fmt = (ms: number) => new Date(ms).toISOString().slice(0, 10);
const parse = (s: string) => Date.parse(`${s}T00:00:00Z`);

/** Local calendar day (`YYYY-MM-DD`) of an instant, for a zone `tzOffsetMinutes` ahead of UTC (IST = 330). */
export const localDay = (instant: Date, tzOffsetMinutes: number): string => fmt(instant.getTime() + tzOffsetMinutes * 60_000);

/** UTC instant at which the local day `day` begins. */
export const localDayStart = (day: string, tzOffsetMinutes: number): Date => new Date(parse(day) - tzOffsetMinutes * 60_000);

/** One entry per day from `from` to `to` inclusive, zero-filled. */
export function fillDays(from: string, to: string, counts: ReadonlyMap<string, number>): DayCount[] {
  const days: DayCount[] = [];
  for (let t = parse(from); t <= parse(to); t += DAY_MS) days.push({ date: fmt(t), count: counts.get(fmt(t)) ?? 0 });
  return days;
}

export function countByDay(dates: readonly string[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const d of dates) counts.set(d, (counts.get(d) ?? 0) + 1);
  return counts;
}

/**
 * Totals plus the "weekly rhythm": average count per weekday (index 0 = Sunday),
 * and the current streak of consecutive active days ending at the last day.
 */
export function summarize(days: readonly DayCount[]): HeatmapSummary {
  let streak = 0;
  for (let i = days.length - 1; i >= 0 && (days[i]?.count ?? 0) > 0; i--) streak++;

  const sums = Array<number>(7).fill(0);
  const seen = Array<number>(7).fill(0);
  for (const { date, count } of days) {
    const weekday = new Date(parse(date)).getUTCDay();
    sums[weekday]! += count;
    seen[weekday]! += 1;
  }
  return {
    total: days.reduce((n, d) => n + d.count, 0),
    streak,
    best: days.reduce((m, d) => Math.max(m, d.count), 0),
    rhythm: sums.map((s, i) => (seen[i] ? Math.round((s / seen[i]!) * 100) / 100 : 0)),
  };
}
