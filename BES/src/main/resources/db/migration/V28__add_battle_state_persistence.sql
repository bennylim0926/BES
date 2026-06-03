-- Battle state persistence tables for genre division switching and restart recovery
-- Stores the state of each active battle (bracket, pair, phase, judges) scoped per (event, genre)

CREATE TABLE battle_genre_state (
    id                          BIGSERIAL    PRIMARY KEY,
    event_name                  VARCHAR(255) NOT NULL,
    genre_name                  VARCHAR(255) NOT NULL,
    bracket_json                TEXT,
    top_size                    INTEGER,
    current_round_index         INTEGER      DEFAULT 0,
    current_pair_left           VARCHAR(255),
    current_pair_left_members   TEXT,
    current_pair_right          VARCHAR(255),
    current_pair_right_members  TEXT,
    is_final                    BOOLEAN      DEFAULT FALSE,
    battle_phase                VARCHAR(20)  DEFAULT 'IDLE',
    judges_json                 TEXT,
    updated_at                  TIMESTAMP    DEFAULT NOW(),
    UNIQUE (event_name, genre_name)
);

-- Singleton table tracking which event+genre is currently live on the backend
-- Always contains exactly one row with id=1; updated by genre switch and @PostConstruct
CREATE TABLE battle_active_genre (
    id         INTEGER PRIMARY KEY DEFAULT 1,
    event_name VARCHAR(255),
    genre_name VARCHAR(255)
);

INSERT INTO battle_active_genre (id, event_name, genre_name) VALUES (1, NULL, NULL);
