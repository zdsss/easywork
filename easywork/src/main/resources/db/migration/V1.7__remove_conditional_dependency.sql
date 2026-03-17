-- C-1: Remove CONDITIONAL dependency type and condition_expression column
-- The CONDITIONAL type was never used in production; removing it simplifies
-- the data model and eliminates dead code.

-- 1. Drop the condition_expression column
ALTER TABLE operation_dependencies DROP COLUMN IF EXISTS condition_expression;

-- 2. Update any stale CONDITIONAL rows to SERIAL (defensive: should not exist in practice)
UPDATE operation_dependencies SET dependency_type = 'SERIAL' WHERE dependency_type = 'CONDITIONAL';

-- 3. Drop the old CHECK constraint and add the updated one
ALTER TABLE operation_dependencies DROP CONSTRAINT IF EXISTS operation_dependencies_dependency_type_check;
ALTER TABLE operation_dependencies
    ADD CONSTRAINT operation_dependencies_dependency_type_check
    CHECK (dependency_type IN ('SERIAL', 'PARALLEL'));
