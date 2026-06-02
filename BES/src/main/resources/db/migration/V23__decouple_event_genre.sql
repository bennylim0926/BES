-- V23: Decouple event_genre from global genre table

-- Phase A: Upgrade event_genre table
ALTER TABLE event_genre ADD COLUMN id BIGSERIAL;

ALTER TABLE event_genre ADD COLUMN name VARCHAR(255);
UPDATE event_genre eg SET name = g.genre_name FROM genre g WHERE eg.genre_id = g.genre_id;
ALTER TABLE event_genre ALTER COLUMN name SET NOT NULL;

ALTER TABLE event_genre ADD COLUMN sheet_aliases TEXT;

ALTER TABLE event_genre DROP CONSTRAINT event_genre_pkey CASCADE;
ALTER TABLE event_genre ADD PRIMARY KEY (id);
ALTER TABLE event_genre ALTER COLUMN genre_id DROP NOT NULL;
ALTER TABLE event_genre ADD CONSTRAINT event_genre_event_name_unique UNIQUE (event_id, name);

-- Phase B: Add event_genre_id to all dependent tables (BEFORE dropping any genre_id)

-- event_genre_participant
ALTER TABLE event_genre_participant ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_participant egp
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE egp.event_id = eg.event_id AND egp.genre_id = eg.genre_id;
ALTER TABLE event_genre_participant ALTER COLUMN event_genre_id SET NOT NULL;

-- score
ALTER TABLE score ADD COLUMN event_genre_id BIGINT;
UPDATE score s
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE s.event_id = eg.event_id AND s.genre_id = eg.genre_id;

-- audition_feedback
ALTER TABLE audition_feedback ADD COLUMN event_genre_id BIGINT;
UPDATE audition_feedback af
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE af.event_id = eg.event_id AND af.genre_id = eg.genre_id;

-- event_genre_participant_member
ALTER TABLE event_genre_participant_member ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_participant_member m
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE m.event_id = eg.event_id AND m.genre_id = eg.genre_id;

-- event_genre_battle_guest
ALTER TABLE event_genre_battle_guest ADD COLUMN event_genre_id BIGINT;
UPDATE event_genre_battle_guest bg
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE bg.event_id = eg.event_id AND bg.genre_id = eg.genre_id;
ALTER TABLE event_genre_battle_guest ALTER COLUMN event_genre_id SET NOT NULL;

-- scoring_criteria
ALTER TABLE scoring_criteria ADD COLUMN event_genre_id BIGINT;
UPDATE scoring_criteria sc
    SET event_genre_id = eg.id
    FROM event_genre eg
    WHERE sc.event_id = eg.event_id AND sc.genre_id = eg.genre_id;

-- Phase C: Drop old constraints and genre_id columns

-- event_genre_participant: rebuild PK and FK
ALTER TABLE event_genre_participant
    DROP CONSTRAINT IF EXISTS event_genre_participant_event_id_genre_id_fkey;
ALTER TABLE event_genre_participant DROP CONSTRAINT event_genre_participant_pkey CASCADE;
ALTER TABLE event_genre_participant DROP COLUMN genre_id;
ALTER TABLE event_genre_participant
    ADD CONSTRAINT egp_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id);
ALTER TABLE event_genre_participant
    ADD PRIMARY KEY (event_id, event_genre_id, participant_id);

-- score
ALTER TABLE score DROP COLUMN genre_id;

-- audition_feedback
ALTER TABLE audition_feedback DROP CONSTRAINT IF EXISTS audition_feedback_event_id_genre_id_participant_id_judge_id_key;
ALTER TABLE audition_feedback DROP COLUMN genre_id;
ALTER TABLE audition_feedback
    ADD CONSTRAINT audition_feedback_unique
    UNIQUE (event_id, event_genre_id, participant_id, judge_id);

-- event_genre_participant_member
ALTER TABLE event_genre_participant_member DROP CONSTRAINT IF EXISTS fk_egpm_egp;
ALTER TABLE event_genre_participant_member DROP COLUMN genre_id;
ALTER TABLE event_genre_participant_member
    ADD CONSTRAINT fk_egpm_egp
    FOREIGN KEY (event_id, event_genre_id, participant_id)
    REFERENCES event_genre_participant (event_id, event_genre_id, participant_id)
    ON DELETE CASCADE;

-- event_genre_battle_guest
ALTER TABLE event_genre_battle_guest
    DROP CONSTRAINT IF EXISTS event_genre_battle_guest_genre_id_fkey;
ALTER TABLE event_genre_battle_guest DROP COLUMN genre_id;
ALTER TABLE event_genre_battle_guest
    ADD CONSTRAINT egbg_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id) ON DELETE CASCADE;

-- scoring_criteria
ALTER TABLE scoring_criteria
    DROP CONSTRAINT IF EXISTS scoring_criteria_genre_id_fkey;
ALTER TABLE scoring_criteria DROP COLUMN genre_id;
ALTER TABLE scoring_criteria
    ADD CONSTRAINT sc_event_genre_id_fkey
    FOREIGN KEY (event_genre_id) REFERENCES event_genre(id);
