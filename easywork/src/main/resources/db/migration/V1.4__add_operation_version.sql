-- Add version field for optimistic locking
ALTER TABLE operations ADD COLUMN version INTEGER DEFAULT 0;
