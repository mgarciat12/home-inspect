-- =====================================================================
-- Catalog: sections and items of the Standard of Practice (data, not code).
-- Filled / updated at startup from catalog/sop-catalog.yml (CatalogSeeder).
-- Rows are never deleted: elements removed from the YAML become inactive,
-- so existing reports keep them.
-- =====================================================================

CREATE TABLE section_def (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    position    INTEGER      NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    optional    BOOLEAN      NOT NULL DEFAULT FALSE,
    methods     TEXT[]       NOT NULL DEFAULT '{}',
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE item_def (
    id          BIGSERIAL PRIMARY KEY,
    section_id  BIGINT       NOT NULL REFERENCES section_def (id),
    code        VARCHAR(50)  NOT NULL,
    position    INTEGER      NOT NULL,
    title       VARCHAR(200) NOT NULL,
    materials   TEXT[]       NOT NULL DEFAULT '{}',
    limitations TEXT[]       NOT NULL DEFAULT '{}',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (section_id, code)
);

-- Limitations offered on every item (stored as text in the reports)
CREATE TABLE limitation_def (
    id       BIGSERIAL PRIMARY KEY,
    label    VARCHAR(200) NOT NULL UNIQUE,
    position INTEGER      NOT NULL
);

-- Comment library: one-click phrases for the observations, editable by the admin
CREATE TABLE comment_template (
    id       BIGSERIAL PRIMARY KEY,
    item_id  BIGINT  NOT NULL REFERENCES item_def (id),
    text     TEXT    NOT NULL,
    position INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX comment_template_item_idx ON comment_template (item_id, position);

-- =====================================================================
-- Reports
-- =====================================================================

-- Report numbers per year: 2026-0001, 2026-0002...
CREATE TABLE report_number_seq (
    year       INTEGER PRIMARY KEY,
    last_value INTEGER NOT NULL
);

CREATE TABLE report (
    id              BIGSERIAL PRIMARY KEY,
    number          VARCHAR(20)  NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'FINAL')),
    inspected_at    TIMESTAMPTZ  NOT NULL,
    inspector_id    BIGINT       NOT NULL REFERENCES user_profile (id),
    -- client
    client_name     VARCHAR(200),
    client_email    VARCHAR(255),
    client_phone    VARCHAR(50),
    -- property
    address         VARCHAR(300) NOT NULL,
    postal_code     VARCHAR(20),
    city            VARCHAR(120),
    province        VARCHAR(120),
    property_type   VARCHAR(30),
    year_built      INTEGER,
    area_m2         NUMERIC(10, 2),
    floors          INTEGER,
    occupancy       VARCHAR(30),
    people_present  VARCHAR(300),
    -- conditions
    weather         VARCHAR(30),
    temperature_c   NUMERIC(4, 1),
    soil_condition  VARCHAR(30),
    -- photo on the cover (set in the photos phase)
    cover_photo_id  BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finalized_at    TIMESTAMPTZ,
    version         INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX report_inspector_idx ON report (inspector_id);
CREATE INDEX report_inspected_at_idx ON report (inspected_at DESC);

CREATE TABLE report_section (
    id                  BIGSERIAL PRIMARY KEY,
    report_id           BIGINT      NOT NULL REFERENCES report (id) ON DELETE CASCADE,
    section_def_id      BIGINT      NOT NULL REFERENCES section_def (id),
    not_applicable      BOOLEAN     NOT NULL DEFAULT FALSE,
    inspection_method   VARCHAR(200),
    general_comments    TEXT,
    general_limitations TEXT,
    UNIQUE (report_id, section_def_id)
);

CREATE TABLE report_item (
    id                  BIGSERIAL PRIMARY KEY,
    report_section_id   BIGINT      NOT NULL REFERENCES report_section (id) ON DELETE CASCADE,
    item_def_id         BIGINT      NOT NULL REFERENCES item_def (id),
    status              VARCHAR(20) CHECK (status IN ('INSPECTED', 'NOT_INSPECTED', 'NOT_PRESENT')),
    rating              VARCHAR(20) CHECK (rating IN ('SATISFACTORY', 'MAINTENANCE', 'REPAIR', 'SAFETY_HAZARD', 'SPECIALIST')),
    materials           TEXT[]      NOT NULL DEFAULT '{}',
    material_other      VARCHAR(300),
    limitations         TEXT[]      NOT NULL DEFAULT '{}',
    limitation_text     TEXT,
    observations        TEXT,
    recommendation      VARCHAR(30) CHECK (recommendation IN ('NONE', 'MONITOR', 'REPAIR', 'REPLACE', 'QUOTE_REPLACEMENT', 'CONSULT_PROFESSIONAL')),
    recommendation_text TEXT,
    updated_at          TIMESTAMPTZ,
    UNIQUE (report_section_id, item_def_id)
);
