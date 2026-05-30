CREATE TABLE app_config (
    id      BIGSERIAL PRIMARY KEY,
    key     VARCHAR(100) NOT NULL UNIQUE,
    value   TEXT         NOT NULL
);

INSERT INTO app_config (key, value) VALUES ('accentColor', '#ffffff');
