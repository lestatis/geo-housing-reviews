-- V2.5 pinned the audit vocabulary to the single action that existed then, and said a new action
-- would arrive by a later migration. This is that migration: roles can now be granted and removed
-- through the API rather than by editing this database, and each attempt is recorded.
--
-- REFUSED is deliberately part of the vocabulary. An attempt to demote the last administrator, or
-- to change one's own role, is refused — and a refusal that leaves no trace is exactly the kind of
-- attempt worth being able to look back at.
ALTER TABLE identity.admin_audit_event
    DROP CONSTRAINT admin_audit_event_action_check;

ALTER TABLE identity.admin_audit_event
    ADD CONSTRAINT admin_audit_event_action_check
    CHECK (action IN ('VIEW_ACCOUNT', 'GRANT_ADMIN', 'REVOKE_ADMIN'));

ALTER TABLE identity.admin_audit_event
    DROP CONSTRAINT admin_audit_event_outcome_check;

ALTER TABLE identity.admin_audit_event
    ADD CONSTRAINT admin_audit_event_outcome_check
    CHECK (outcome IN ('FOUND', 'NOT_FOUND', 'APPLIED', 'REFUSED'));
