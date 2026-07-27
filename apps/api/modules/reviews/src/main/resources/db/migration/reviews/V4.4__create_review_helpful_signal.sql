-- A helpful signal is a private, positive vote from one account on one review. The account id stays
-- opaque: identity owns accounts, and reviews must not create cross-module foreign keys. The review
-- id is a local foreign key because an orphaned signal is meaningless inside this module.
--
-- Withdrawal preserves the fact that a signal existed for future aggregate reconciliation and abuse
-- review. The partial unique index permits one active signal, while allowing the same voter to signal
-- again after a withdrawal. Voter eligibility (published review and not its author) belongs to the
-- application layer because it requires inspecting the review row and authorization context.
CREATE TABLE reviews.review_helpful_signal (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id        UUID        NOT NULL REFERENCES reviews.review(id),
    voter_account_id UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at     TIMESTAMPTZ,
    CHECK (withdrawn_at IS NULL OR withdrawn_at >= created_at)
);

CREATE UNIQUE INDEX review_helpful_signal_one_active_voter_idx
    ON reviews.review_helpful_signal (review_id, voter_account_id)
    WHERE withdrawn_at IS NULL;

-- Supports a future public aggregate count without reading voter identities into a review response.
CREATE INDEX review_helpful_signal_active_review_idx
    ON reviews.review_helpful_signal (review_id)
    WHERE withdrawn_at IS NULL;
