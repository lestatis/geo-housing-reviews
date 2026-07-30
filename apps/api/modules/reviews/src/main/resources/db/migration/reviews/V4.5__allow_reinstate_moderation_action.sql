-- Widen the audited action vocabulary to include REINSTATE.
--
-- A review that was rejected or removed is terminal for every ordinary path: ensureMutable() in the
-- Review aggregate refuses to touch it, and that is what makes "removed" mean removed. Appeals are
-- the one case where a terminal decision may have been wrong, and MODERATION.md requires an appeal
-- to be able to change an outcome. Without a way back, a takedown demand that succeeds and then
-- loses on appeal still gets what it wanted — the exact capture the anti-capture rules exist to
-- prevent.
--
-- So the door is narrow and audited: REINSTATE is reachable only through the moderation appeal
-- path, and lands here like every other action, with its moderator and reason code.
ALTER TABLE reviews.review_moderation_audit_event
    DROP CONSTRAINT review_moderation_audit_event_action_check;

ALTER TABLE reviews.review_moderation_audit_event
    ADD CONSTRAINT review_moderation_audit_event_action_check
        CHECK (action IN ('PUBLISH', 'REJECT', 'HIDE', 'RESTORE', 'REMOVE', 'REINSTATE'));
