-- Monerium integration removed: zero associations ever linked a wallet (0 rows in either table
-- in staging, no prod deployment yet). No data migration needed.
DROP TABLE IF EXISTS monerium_oauth_states;
DROP TABLE IF EXISTS monerium_connections;
