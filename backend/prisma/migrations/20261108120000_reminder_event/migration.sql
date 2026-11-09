-- A reminder can belong to a calendar event (its "alert before"); deleting the event deletes the alert.
ALTER TABLE "Reminder" ADD COLUMN "eventId" UUID;

CREATE INDEX "Reminder_eventId_idx" ON "Reminder"("eventId");

ALTER TABLE "Reminder" ADD CONSTRAINT "Reminder_eventId_fkey" FOREIGN KEY ("eventId") REFERENCES "CalendarEvent"("id") ON DELETE CASCADE ON UPDATE CASCADE;
