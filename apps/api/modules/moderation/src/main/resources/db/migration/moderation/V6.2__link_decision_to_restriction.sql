-- Which restriction a decision created, so overturning it on appeal can lift that one and no other.
--
-- A decision does not always own a restriction: identity does nothing when the account is already
-- restricted, and the decision's intended outcome already holds. Null therefore means "restricted
-- nobody", which is the truthful answer for every decision recorded before this column existed as
-- well as for the overwhelming majority recorded after it.
--
-- No foreign key: identity owns identity.user_restriction, and a module does not reference another
-- module's tables (ARCHITECTURE.md, boundary rules). The link is carried, not enforced.
ALTER TABLE moderation.moderation_decision
    ADD COLUMN created_restriction_id UUID;

COMMENT ON COLUMN moderation.moderation_decision.created_restriction_id IS
    'The identity restriction this decision created, if any. Lifted when the decision is overturned.';
