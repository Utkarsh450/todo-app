export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

/**
 * Keeps `completedAt` consistent with `status`: stamped when a task becomes DONE
 * (or stays DONE) and cleared once it is reopened.
 */
export function statusFields(status: TaskStatus, now: Date, previousCompletedAt: Date | null = null) {
  if (status === 'DONE') return { status, completedAt: previousCompletedAt ?? now };
  return { status, completedAt: null };
}
