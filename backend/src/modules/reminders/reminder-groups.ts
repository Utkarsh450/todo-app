export interface Schedulable {
  remindAt: Date;
  snoozedUntil: Date | null;
  enabled: boolean;
}

export interface ReminderGroups<T> {
  overdue: T[];
  today: T[];
  thisWeek: T[];
  later: T[];
}

/** A snoozed reminder fires at its snooze time, otherwise at its original time. */
export const effectiveTime = (r: Schedulable): Date => r.snoozedUntil ?? r.remindAt;

/**
 * Buckets reminders for the Reminders screen (Overdue / Today / This week / Later), each sorted by
 * effective time. Disabled reminders still appear (shown dimmed) but never count as overdue.
 */
export function groupReminders<T extends Schedulable>(
  reminders: readonly T[],
  bounds: { now: Date; endOfToday: Date; endOfWeek: Date },
): ReminderGroups<T> {
  const groups: ReminderGroups<T> = { overdue: [], today: [], thisWeek: [], later: [] };
  const sorted = [...reminders].sort((a, b) => effectiveTime(a).getTime() - effectiveTime(b).getTime());
  for (const r of sorted) {
    const at = effectiveTime(r);
    if (at < bounds.now) (r.enabled ? groups.overdue : groups.today).push(r);
    else if (at < bounds.endOfToday) groups.today.push(r);
    else if (at < bounds.endOfWeek) groups.thisWeek.push(r);
    else groups.later.push(r);
  }
  return groups;
}
