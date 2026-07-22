-- review.current_version_id and review_version.review_id point at each other, so neither row can be
-- written first while both constraints are checked immediately: the review needs a version to point
-- at, and the version needs a review to belong to.
--
-- Deferring the review -> current version check to commit time lets one transaction write the whole
-- aggregate in a single flush. The constraint is still enforced — a transaction that leaves
-- current_version_id dangling fails at COMMIT — but the writer no longer has to insert the review,
-- flush, and update it again afterwards. That second UPDATE would also have bumped the optimistic
-- lock version, so a freshly created review would already look stale to its own creator.
--
-- Only this one direction is deferred. review_version.review_id stays immediate: nothing needs it
-- relaxed, and an immediate check catches a mistake at the statement that made it.

ALTER TABLE reviews.review
    ALTER CONSTRAINT review_current_version_fkey DEFERRABLE INITIALLY DEFERRED;
