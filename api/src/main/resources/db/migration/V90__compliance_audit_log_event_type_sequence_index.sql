-- Recent entries of a set of event types, newest first (ComplianceAuditLogRepository
-- .findTop50ByEventTypeInOrderBySequenceNoDesc: history of on-chain registry freezes and resumes).
-- The composite (event_type, sequence_no) covers every lookup of the narrow event_type index, which is dropped.
CREATE INDEX idx_compliance_audit_log_event_type_seq
    ON compliance_audit_log (event_type, sequence_no);

DROP INDEX IF EXISTS idx_compliance_audit_log_event_type;
