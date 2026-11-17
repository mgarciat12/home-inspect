-- =====================================================================
-- Form questions: the 4-Point and wind mitigation reports reproduce official forms.
-- Their catalog items are questions (field_type not null) answered in report_item.answers;
-- standard inspection items keep field_type NULL and use status / rating.
-- =====================================================================

ALTER TABLE item_def ADD COLUMN field_type VARCHAR(20)
    CHECK (field_type IN ('TEXT', 'NUMBER', 'YEAR', 'DATE', 'YES_NO', 'YES_NO_NA', 'CHOICE', 'MULTI', 'CONDITION', 'PHOTO'));
ALTER TABLE item_def ADD COLUMN options       TEXT[]  NOT NULL DEFAULT '{}';
ALTER TABLE item_def ADD COLUMN required      BOOLEAN NOT NULL DEFAULT FALSE;
-- Answers that require an explanation (comments) before the report can be finalized
ALTER TABLE item_def ADD COLUMN explain_on    TEXT[]  NOT NULL DEFAULT '{}';
-- At least one photo is required (unless the question is answered "Not applicable")
ALTER TABLE item_def ADD COLUMN photo_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item_def ADD COLUMN help          TEXT;

ALTER TABLE report_item ADD COLUMN answers TEXT[] NOT NULL DEFAULT '{}';
