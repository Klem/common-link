-- Provisions the initial row of sanctions_sync_state for integration tests.
-- The table itself is created by Hibernate create-drop (SanctionSyncState is a @Entity).
-- This script only inserts the singleton row that V58 would normally insert via Flyway.
INSERT INTO sanctions_sync_state (id) SELECT 1 WHERE NOT EXISTS
    (SELECT 1 FROM sanctions_sync_state WHERE id = 1);
