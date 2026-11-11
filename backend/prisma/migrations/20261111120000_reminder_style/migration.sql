-- How an alert shows up: an ordinary notification (Done / Snooze) or an alarm that takes over the screen.
-- Alerts made before this existed rang like alarms, so that stays their style.
CREATE TYPE "AlertStyle" AS ENUM ('NOTIFICATION', 'ALARM');

ALTER TABLE "Reminder" ADD COLUMN "style" "AlertStyle" NOT NULL DEFAULT 'ALARM';
