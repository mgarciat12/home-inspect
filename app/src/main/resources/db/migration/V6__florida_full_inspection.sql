-- =====================================================================
-- Florida full home inspection (rules 61-30.801 to 61-30.811, F.A.C.)
-- =====================================================================

-- New rating: near the end of its service life (section 468.8323, F.S.)
ALTER TABLE report_item DROP CONSTRAINT report_item_rating_check;
ALTER TABLE report_item ADD CONSTRAINT report_item_rating_check
    CHECK (rating IN ('SATISFACTORY', 'MAINTENANCE', 'REPAIR', 'SAFETY_HAZARD', 'SPECIALIST', 'END_OF_LIFE'));

-- The comment library is seeded once per item from the catalog file. The catalog is now in
-- English: the Spanish phrases of the full inspection are replaced by the new ones at startup.
ALTER TABLE item_def ADD COLUMN comments_seeded BOOLEAN NOT NULL DEFAULT TRUE;
DELETE FROM comment_template
 WHERE item_id IN (SELECT i.id FROM item_def i JOIN section_def s ON s.id = i.section_id WHERE s.report_type = 'FULL');
UPDATE item_def SET comments_seeded = FALSE
 WHERE section_id IN (SELECT id FROM section_def WHERE report_type = 'FULL');

-- Limitations are now in English (the seeder recreates the list)
DELETE FROM limitation_def;

-- Default scope text in English, unless the administrator already changed it
UPDATE company_settings SET scope_text =
'This inspection was performed in accordance with the Florida Standards of Practice for home inspectors (rules 61-30.801 through 61-30.811, Florida Administrative Code). '
|| 'It is a visual, non-invasive examination of the readily accessible installed systems and components of the home, as they appeared at the time of the inspection. '
|| 'The inspector reports systems and components that, in the inspector''s professional opinion, are significantly deficient or near the end of their service lives, and states the reason when it is not self-evident. '
|| 'Systems and components that were present but not inspected are listed with the reason. '
|| 'The inspection does not predict future conditions, is not a code compliance inspection, and is not a warranty, guarantee or insurance policy. '
|| 'Items rated "Further evaluation by a specialist" should be evaluated by an appropriately licensed professional before closing.'
 WHERE scope_text LIKE 'Esta inspección es un examen visual%';
