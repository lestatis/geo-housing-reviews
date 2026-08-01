-- Restrictions can now be placed and lifted through the API rather than by editing this database,
-- so the audit vocabulary grows again. Append-only: a new action arrives by a new migration, as
-- V2.5 said it would.
--
-- A lift is its own action rather than a RESTRICT_ACCOUNT with a different outcome. Ending somebody
-- else's restriction early is a distinct decision from placing one, and a log that could not tell
-- them apart would answer "who let this account back in?" with a shrug.
ALTER TABLE identity.admin_audit_event
    DROP CONSTRAINT admin_audit_event_action_check;

ALTER TABLE identity.admin_audit_event
    ADD CONSTRAINT admin_audit_event_action_check
    CHECK (action IN ('VIEW_ACCOUNT', 'GRANT_ADMIN', 'REVOKE_ADMIN',
                      'RESTRICT_ACCOUNT', 'LIFT_RESTRICTION'));
