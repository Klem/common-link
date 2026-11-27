-- A HelloAsso organization belongs to one CommonLink association for good (CEO decision 2026-11-26): once a row holds
-- its slug — ACTIVE, BROKEN or DISCONNECTED — no other association can link it. NOT_LINKED rows hold no slug: NULLs are
-- distinct for a UNIQUE constraint, so they stay unconstrained. The row's own association can still re-link it, or
-- link another organization (which frees the first one). Mirrored on HelloAssoConnection.organizationSlug.

-- Fails loudly when two rows already hold the same slug, naming it.
DO $$
DECLARE
    duplicate_slug VARCHAR;
BEGIN
    SELECT organization_slug INTO duplicate_slug
    FROM helloasso_connections
    WHERE organization_slug IS NOT NULL
    GROUP BY organization_slug
    HAVING COUNT(*) > 1
    LIMIT 1;
    IF duplicate_slug IS NOT NULL THEN
        RAISE EXCEPTION 'HelloAsso organization % is linked to several associations: resolve before V101', duplicate_slug;
    END IF;
END $$;

-- Also backs the "already linked" lookup (existsByOrganizationSlugAndAssociationIdNot).
ALTER TABLE helloasso_connections
    ADD CONSTRAINT uq_helloasso_connections_organization_slug UNIQUE (organization_slug);
