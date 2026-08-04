-- Reading the audit log is itself a privileged action, so it is recorded like the others.
--
-- The timeline answers "who has been looking at what", and an access review that cannot see its
-- own reviewers is only half a control. This does create a feedback loop — each read becomes a row
-- a later read will show — which is honest rather than a defect: the log says what happened, and
-- somebody reading it is something that happened.
--
-- identity's next free number. What the other modules have applied does not matter: each module has
-- its own history table (CONTRIBUTING.md, "Migrations").
ALTER TABLE identity.admin_audit_event
    DROP CONSTRAINT admin_audit_event_action_check;

ALTER TABLE identity.admin_audit_event
    ADD CONSTRAINT admin_audit_event_action_check
    CHECK (action IN ('VIEW_ACCOUNT', 'GRANT_ADMIN', 'REVOKE_ADMIN',
                      'RESTRICT_ACCOUNT', 'LIFT_RESTRICTION', 'VIEW_AUDIT'));
