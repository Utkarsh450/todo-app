-- Event details from the Create screen: where, a video link, who, and whether the time shows as busy.
ALTER TABLE "CalendarEvent" ADD COLUMN "location" TEXT,
ADD COLUMN "videoLink" TEXT,
ADD COLUMN "busy" BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN "guests" TEXT[] DEFAULT ARRAY[]::TEXT[];
