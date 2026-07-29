-- Spring Modulith's event publication registry table, required by
-- spring-modulith-starter-jpa (module 2.1.0) to track application events
-- published via ApplicationEventPublisher / @ApplicationModuleListener.
-- Schema matches spring-modulith-events-jpa 2.1.0's official definition.
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER NOT NULL,
    status                 TEXT,
    PRIMARY KEY (id)
);

-- Modulith's recommended lookup indexes (incomplete-publication scans + dedupe)
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
