CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS cards
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    canonical_name TEXT        NOT NULL UNIQUE,
    requested_name TEXT        NOT NULL,
    png_url        TEXT,
    status         VARCHAR(20) NOT NULL,
    fetched_at     TIMESTAMPTZ,
    fail_reason    TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cards_status ON cards (status);
CREATE INDEX IF NOT EXISTS idx_cards_canonical_name ON cards (canonical_name);
CREATE INDEX IF NOT EXISTS idx_cards_fetched_at ON cards (fetched_at);

CREATE TABLE IF NOT EXISTS operations
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    card_ids        UUID[]      NOT NULL DEFAULT '{}',
    requested_names TEXT[]      NOT NULL,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_operations_status ON operations (status);
