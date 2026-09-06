-- Phase 0 verifies Flyway connectivity and its schema history only.
-- No legacy or business tables are created or modified at this checkpoint.
-- Module 1 starts with V2; never change an applied versioned migration.
SELECT 1;
