-- Comment library for every report type: the catalog files now have phrases for every item.
-- Items without any phrase (4-Point and wind mitigation questions, a few full inspection items)
-- get the phrases of the catalog at the next startup.
UPDATE item_def SET comments_seeded = FALSE
 WHERE NOT EXISTS (SELECT 1 FROM comment_template c WHERE c.item_id = item_def.id);
