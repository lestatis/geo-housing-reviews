# Plan 019 — lift the restriction an overturned decision created

## Objective

When an appeal overturns a `RESTRICT_ACCOUNT` moderation decision, end the exact identity
restriction that decision created. An appeal must never end a restriction created by another case.

## Acceptance criteria

1. A restricting decision records the identity restriction ID only when it successfully creates a
   new restriction.
2. Overturning that decision lifts the recorded restriction through identity's published API, even
   when the review is no longer available to provide its author's account ID.
3. If the account was already restricted, the later decision records no ID and its appeal leaves
   the pre-existing restriction alone.
4. Existing decisions remain readable; their absent link is a deliberate no-op on appeal.
5. The moderation migration adds the nullable link without a cross-module database foreign key.
6. Unit and migration integration tests cover the positive and isolation paths.

## Non-goals

- Publishing the decision explanation and policy version to an author-facing endpoint. That is the
  other Group C P1 and needs its own API-contract task.
- Changing administrator-initiated restriction lifting or the one-active-restriction invariant.
- Retrofitting historical decisions: no truthful link can be reconstructed for them.

## Design

Identity remains the owner of restrictions. Its `AccountRestraint` contract returns the new
restriction ID or empty when an active restriction already exists, and lifts by that linked ID.
The internal restriction use case resolves the owning account from its own row, retaining the
admin endpoint's account-plus-ID safeguard while avoiding a new dependency on a potentially deleted
review. Moderation persists the opaque ID in its own table, but creates no foreign key into
identity. This keeps the module boundary while making the appeal reversal precise.

The migration is additive and nullable. Rollback is a forward fix: deploy code that ignores the
column, then remove it only after all code and retained decisions no longer need the link; do not
delete the identity restriction history.

## Steps

1. Recover and verify the existing implementation and test coverage. **Completed**
2. Add the migration integration assertion and regression coverage, including a deleted-target
   reversal. **Completed**
3. Run scoped module/app checks and the repository gate; update the handoff. **Completed**
4. Request independent read-only review in a separate session. Pending.
