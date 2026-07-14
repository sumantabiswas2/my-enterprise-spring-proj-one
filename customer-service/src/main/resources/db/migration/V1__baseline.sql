-- Customer Service baseline schema (Flyway, FR-CUST-*, AGENTS.md → Database Rules).
-- The customer profile is keyed by the immutable Keycloak subject; this service never assumes the
-- Keycloak subject equals the database primary key (FR-CUST-07). Full profile/address tables and
-- the outbox arrive with the M1 implementation; this baseline establishes the schema version.

CREATE TABLE IF NOT EXISTS schema_metadata (
    key         VARCHAR(64) PRIMARY KEY,
    value       VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_metadata (key, value)
VALUES ('baseline', 'M0')
ON CONFLICT (key) DO NOTHING;
