-- Scrape job definitions. cron is a Spring 6-field expression (sec min hour day month weekday); NULL = manual only.
CREATE TABLE scrape_job (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    source      VARCHAR(40)  NOT NULL,
    start_url   VARCHAR(2000) NOT NULL,
    max_pages   INT          NOT NULL DEFAULT 100 CHECK (max_pages BETWEEN 1 AND 10000),
    delay_ms    INT          NOT NULL DEFAULT 500 CHECK (delay_ms BETWEEN 0 AND 60000),
    cron        VARCHAR(120),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE scrape_run (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT      NOT NULL REFERENCES scrape_job (id) ON DELETE CASCADE,
    trigger         VARCHAR(16) NOT NULL CHECK (trigger IN ('MANUAL', 'SCHEDULE', 'API')),
    status          VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    pages_fetched   INT         NOT NULL DEFAULT 0,
    items_found     INT         NOT NULL DEFAULT 0,
    items_new       INT         NOT NULL DEFAULT 0,
    items_updated   INT         NOT NULL DEFAULT 0,
    items_unchanged INT         NOT NULL DEFAULT 0,
    retries         INT         NOT NULL DEFAULT 0,
    message         TEXT
);
CREATE INDEX scrape_run_job_idx ON scrape_run (job_id, id DESC);
-- At most one RUNNING run per job, even with several app instances: a second start fails on this index.
CREATE UNIQUE INDEX scrape_run_one_running ON scrape_run (job_id) WHERE status = 'RUNNING';

-- The current dataset of each job, deduplicated by a natural key. content_hash detects changed records.
CREATE TABLE scraped_item (
    id                 BIGSERIAL PRIMARY KEY,
    job_id             BIGINT       NOT NULL REFERENCES scrape_job (id) ON DELETE CASCADE,
    item_key           VARCHAR(500) NOT NULL,
    payload            JSONB        NOT NULL,
    content_hash       CHAR(64)     NOT NULL,
    first_seen_run_id  BIGINT       NOT NULL,
    last_seen_run_id   BIGINT       NOT NULL,
    first_seen_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (job_id, item_key)
);
CREATE INDEX scraped_item_first_run_idx ON scraped_item (job_id, first_seen_run_id);
