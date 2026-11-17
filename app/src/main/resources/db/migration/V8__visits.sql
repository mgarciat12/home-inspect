-- Reports created together for the same visit (e.g. full inspection + 4-Point + wind mitigation)
-- share the header; changes to the header are applied to the drafts of the same visit.
ALTER TABLE report ADD COLUMN visit_id UUID;
CREATE INDEX report_visit_idx ON report (visit_id) WHERE visit_id IS NOT NULL;
