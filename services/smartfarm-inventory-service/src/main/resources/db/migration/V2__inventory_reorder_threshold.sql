-- V2: add reorder_threshold to inv_item (for low-stock detection).
-- Decimal stored as string (same unit as the item); '0' = no threshold (never low).
-- Backfilled to '0' for existing rows; matches the ItemEntity mapping.

ALTER TABLE inv_item ADD COLUMN IF NOT EXISTS reorder_threshold VARCHAR(40) NOT NULL DEFAULT '0';
