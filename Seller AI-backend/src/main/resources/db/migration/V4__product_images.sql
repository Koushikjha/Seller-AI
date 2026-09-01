-- =====================================================================
-- V4 — Product photography.
--
-- A JSON array of image URLs or paths, in display order. Empty or null is
-- the normal state: the storefront draws a generated placeholder so a
-- product with no photo still looks deliberate rather than broken.
--
-- Deliberately not a separate laptop_image table. Ordering, captions and
-- per-image metadata would justify one; a list of URLs does not, and the
-- join would buy nothing at this scale.
-- =====================================================================

ALTER TABLE laptop ADD COLUMN images JSON;