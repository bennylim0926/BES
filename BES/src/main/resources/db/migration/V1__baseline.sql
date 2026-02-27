-- ===========================================================================
-- Flyway Baseline Migration: V1__baseline.sql
-- Generated from JPA entity classes on 2026-02-27
-- This captures the existing schema so Flyway can manage future migrations.
-- ===========================================================================

-- Run this ONLY on a fresh database, or use `flyway baseline` on existing DBs.

-- ===========================
-- Independent tables (no FKs)
-- ===========================

CREATE TABLE IF NOT EXISTS event (
    event_id    BIGSERIAL PRIMARY KEY,
    event_name  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS participant (
    participant_id      BIGSERIAL PRIMARY KEY,
    participant_name    VARCHAR(255),
    participant_email   VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS genre (
    genre_id    BIGSERIAL PRIMARY KEY,
    genre_name  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS judge (
    judge_id    BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)
);

-- ===========================
-- Join / Association tables
-- ===========================

-- Event <-> Judge (ManyToMany)
CREATE TABLE IF NOT EXISTS event_judge (
    event_id    BIGINT NOT NULL REFERENCES event(event_id),
    judge_id    BIGINT NOT NULL REFERENCES judge(judge_id),
    PRIMARY KEY (event_id, judge_id)
);

-- Event <-> Genre (composite PK)
CREATE TABLE IF NOT EXISTS event_genre (
    event_id    BIGINT NOT NULL REFERENCES event(event_id),
    genre_id    BIGINT NOT NULL REFERENCES genre(genre_id),
    PRIMARY KEY (event_id, genre_id)
);

-- Event <-> Participant (with extra fields)
CREATE TABLE IF NOT EXISTS event_participant (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES event(event_id),
    participant_id  BIGINT NOT NULL REFERENCES participant(participant_id),
    residency       VARCHAR(255),
    genre           VARCHAR(255)
);

-- Event + Genre + Participant (composite PK, with audition number & judge)
CREATE TABLE IF NOT EXISTS event_genre_participant (
    event_id        BIGINT NOT NULL,
    genre_id        BIGINT NOT NULL,
    participant_id  BIGINT NOT NULL REFERENCES participant(participant_id),
    audition_number INTEGER,
    judge_id        BIGINT REFERENCES judge(judge_id),
    PRIMARY KEY (event_id, genre_id, participant_id),
    FOREIGN KEY (event_id, genre_id) REFERENCES event_genre(event_id, genre_id)
);

-- Score (linked to EventGenreParticipant + Judge)
CREATE TABLE IF NOT EXISTS score (
    id              BIGSERIAL PRIMARY KEY,
    aspect          VARCHAR(255),
    value           DOUBLE PRECISION,
    event_id        BIGINT,
    genre_id        BIGINT,
    participant_id  BIGINT,
    judge_id        BIGINT REFERENCES judge(judge_id),
    FOREIGN KEY (event_id, genre_id, participant_id)
        REFERENCES event_genre_participant(event_id, genre_id, participant_id)
);
