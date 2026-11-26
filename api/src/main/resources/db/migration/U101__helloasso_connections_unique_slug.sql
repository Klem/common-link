-- U101: Rollback of V101__helloasso_connections_unique_slug.sql
ALTER TABLE helloasso_connections DROP CONSTRAINT IF EXISTS uq_helloasso_connections_organization_slug;
