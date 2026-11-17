-- U97 : Rollback of V97__legal_document_structured_content.sql
-- JSON_V1 rows inserted since V97 stay in place and become indistinguishable from plain text.

ALTER TABLE legal_document DROP COLUMN content_sha256;
ALTER TABLE legal_document DROP COLUMN content_format;
