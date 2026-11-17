-- =====================================================================
-- Report types (Florida): full home inspection, 4-Point and wind mitigation.
-- Existing reports and catalog rows become FULL.
-- =====================================================================

ALTER TABLE report ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'FULL'
    CHECK (report_type IN ('FULL', 'FOUR_POINT', 'WIND_MITIGATION'));
CREATE INDEX report_type_idx ON report (report_type);

-- Numbers per type and year: 2026-0001 (full), 4P-2026-0001, WM-2026-0001
ALTER TABLE report_number_seq ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'FULL';
ALTER TABLE report_number_seq DROP CONSTRAINT report_number_seq_pkey;
ALTER TABLE report_number_seq ADD PRIMARY KEY (report_type, year);

-- Each report type has its own catalog; section codes are unique per type
ALTER TABLE section_def ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'FULL';
ALTER TABLE section_def DROP CONSTRAINT section_def_code_key;
ALTER TABLE section_def ADD CONSTRAINT section_def_type_code_key UNIQUE (report_type, code);

-- =====================================================================
-- Header adapted to Florida: county, insurance data, square feet and °F
-- =====================================================================

ALTER TABLE report RENAME COLUMN province TO state;
ALTER TABLE report ADD COLUMN county VARCHAR(120);
ALTER TABLE report ADD COLUMN insurance_company VARCHAR(200);
ALTER TABLE report ADD COLUMN policy_number VARCHAR(100);

ALTER TABLE report ADD COLUMN area_sqft INTEGER;
UPDATE report SET area_sqft = ROUND(area_m2 * 10.7639) WHERE area_m2 IS NOT NULL;
ALTER TABLE report DROP COLUMN area_m2;

ALTER TABLE report ADD COLUMN temperature_f NUMERIC(4, 1);
UPDATE report SET temperature_f = ROUND(temperature_c * 9 / 5 + 32, 1) WHERE temperature_c IS NOT NULL;
ALTER TABLE report DROP COLUMN temperature_c;

-- =====================================================================
-- Users: interface language and Florida licenses
-- =====================================================================

-- NULL = the browser language
ALTER TABLE user_profile ADD COLUMN language VARCHAR(5) CHECK (language IN ('en', 'es'));

-- Licenses shown next to the signature (the forms ask for license type and number)
CREATE TABLE inspector_license (
    id           BIGSERIAL PRIMARY KEY,
    profile_id   BIGINT       NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    license_type VARCHAR(40)  NOT NULL,
    number       VARCHAR(50)  NOT NULL,
    position     INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (profile_id, license_type, number)
);
CREATE INDEX inspector_license_profile_idx ON inspector_license (profile_id, position);

-- Company license / registration number (printed with the company data)
ALTER TABLE company_settings ADD COLUMN license_number VARCHAR(100);
