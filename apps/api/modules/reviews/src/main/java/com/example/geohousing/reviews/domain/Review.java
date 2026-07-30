package com.example.geohousing.reviews.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A structured review of a property (see {@code docs/DOMAIN_MODEL.md} Review context).
 *
 * <p>Content lives in immutable {@link ReviewVersion}s: an edit appends a version rather than
 * changing one, so the edit trail survives for moderation. The aggregate holds only the <em>current
 * version</em> — the full history is stored data, not domain state: no state-machine decision ever
 * consults an old version, and loading a page of reviews must not drag every edit ever made along
 * with it. Reading the trail is a moderation read path against the store.
 *
 * <p>Version numbers stay dense (1, 2, 3…) through two guards: the aggregate numbers each appended
 * version from the current one and allows <em>one</em> append per loaded instance, and the
 * persistence adapter refuses any save whose version number does not follow directly from the
 * stored one.
 *
 * <p>The publication state machine is {@code DRAFT → PENDING_MODERATION → PUBLISHED}, with {@code
 * PUBLISHED ⇄ HIDDEN} for temporary withdrawal and two terminal states — {@code REJECTED} (from
 * moderation) and {@code REMOVED}. A terminal review rejects all further mutation; the author
 * starts a fresh review instead (the one-live-review index excludes terminal states). The single
 * exception is {@link #reinstate}, which an overturned appeal uses to undo a takedown (DECISION_LOG
 * {@code P-014}) — terminal therefore means terminal except by appeal.
 *
 * <p>Editing a published review sends it back to {@code PENDING_MODERATION}: pre-publication
 * moderation is the MVP default (docs/MODERATION.md), so changed content is re-checked before it is
 * public again. {@code publishedAt} records the first publication and is preserved across
 * re-moderation cycles.
 *
 * <p>The verification tier is a projection of the verification module's decision about the claimed
 * relationship — it never asserts the review's statements are true (docs/TRUST_VERIFICATION.md).
 */
public final class Review {

  private final ReviewId id;
  private final PropertyRef propertyRef;
  private final AuthorId authorId;
  private final RelationshipType relationshipType;
  private final ResidencePeriod residencePeriod;
  private ReviewStatus status;
  private ReviewVersion currentVersion;
  private VerificationTier verificationTier;
  private Instant publishedAt;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;
  private boolean versionAppended;

  private Review(
      ReviewId id,
      PropertyRef propertyRef,
      AuthorId authorId,
      RelationshipType relationshipType,
      ResidencePeriod residencePeriod,
      ReviewStatus status,
      ReviewVersion currentVersion,
      VerificationTier verificationTier,
      Instant publishedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.propertyRef = Objects.requireNonNull(propertyRef, "propertyRef");
    this.authorId = Objects.requireNonNull(authorId, "authorId");
    this.relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
    this.residencePeriod = residencePeriod;
    this.status = Objects.requireNonNull(status, "status");
    this.currentVersion = currentVersion;
    this.verificationTier = Objects.requireNonNull(verificationTier, "verificationTier");
    this.publishedAt = publishedAt;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
    checkInvariants();
  }

  /** Creates a new {@code DRAFT} review with no content yet. */
  public static Review create(
      ReviewId id,
      PropertyRef propertyRef,
      AuthorId authorId,
      RelationshipType relationshipType,
      ResidencePeriod residencePeriod,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    return new Review(
        id,
        propertyRef,
        authorId,
        relationshipType,
        residencePeriod,
        ReviewStatus.DRAFT,
        null,
        VerificationTier.UNVERIFIED,
        null,
        now,
        now,
        0L);
  }

  /** Rebuilds a review from persisted state. Intended for persistence adapters only. */
  public static Review reconstitute(
      ReviewId id,
      PropertyRef propertyRef,
      AuthorId authorId,
      RelationshipType relationshipType,
      ResidencePeriod residencePeriod,
      ReviewStatus status,
      ReviewVersion currentVersion,
      VerificationTier verificationTier,
      Instant publishedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Review(
        id,
        propertyRef,
        authorId,
        relationshipType,
        residencePeriod,
        status,
        currentVersion,
        verificationTier,
        publishedAt,
        createdAt,
        updatedAt,
        version);
  }

  /**
   * Appends a new immutable content version and makes it current. The first version needs no edit
   * reason; every later one must say why it exists. Editing a published review returns it to {@code
   * PENDING_MODERATION} so the changed content is re-checked before being public again.
   *
   * <p>One append per loaded instance: the aggregate no longer carries its history, so a second
   * in-memory append would silently discard the first from the stored trail. Save and reload to
   * append again.
   */
  public void appendVersion(
      String locale,
      String body,
      String pros,
      String cons,
      Recommendation recommendation,
      List<CategoryRating> ratings,
      String editReason,
      Clock clock) {
    ensureMutable();
    if (versionAppended) {
      throw new IllegalStateException(
          "a review appends at most one version per loaded instance; save it first");
    }
    boolean firstVersion = currentVersion == null;
    if (!firstVersion && (editReason == null || editReason.isBlank())) {
      throw new IllegalArgumentException("an edit must state its reason");
    }
    currentVersion =
        new ReviewVersion(
            UUID.randomUUID(),
            firstVersion ? 1 : currentVersion.versionNumber() + 1,
            locale,
            body,
            pros,
            cons,
            recommendation,
            ratings,
            firstVersion ? null : editReason.trim(),
            clock.instant());
    versionAppended = true;
    if (status == ReviewStatus.PUBLISHED) {
      status = ReviewStatus.PENDING_MODERATION;
    }
    touch(clock);
  }

  /** Submits a draft for moderation. Requires content — an empty review cannot be submitted. */
  public void submit(Clock clock) {
    if (status != ReviewStatus.DRAFT) {
      throw new IllegalReviewStateTransitionException(
          "only a DRAFT review can be submitted, was " + status);
    }
    if (currentVersion == null) {
      throw new IllegalReviewStateTransitionException(
          "a review without content cannot be submitted");
    }
    status = ReviewStatus.PENDING_MODERATION;
    touch(clock);
  }

  /** Publishes a moderated review. {@code publishedAt} keeps the first publication time. */
  public void publish(Clock clock) {
    if (status != ReviewStatus.PENDING_MODERATION) {
      throw new IllegalReviewStateTransitionException(
          "only a PENDING_MODERATION review can be published, was " + status);
    }
    status = ReviewStatus.PUBLISHED;
    if (publishedAt == null) {
      publishedAt = clock.instant();
    }
    touch(clock);
  }

  /** Rejects a review in moderation (terminal). */
  public void reject(Clock clock) {
    if (status != ReviewStatus.PENDING_MODERATION) {
      throw new IllegalReviewStateTransitionException(
          "only a PENDING_MODERATION review can be rejected, was " + status);
    }
    status = ReviewStatus.REJECTED;
    touch(clock);
  }

  /** Temporarily withholds a published review from public view. */
  public void hide(Clock clock) {
    if (status != ReviewStatus.PUBLISHED) {
      throw new IllegalReviewStateTransitionException("cannot hide a " + status + " review");
    }
    status = ReviewStatus.HIDDEN;
    touch(clock);
  }

  /** Restores a hidden review to public view. */
  public void restore(Clock clock) {
    if (status != ReviewStatus.HIDDEN) {
      throw new IllegalReviewStateTransitionException("cannot restore a " + status + " review");
    }
    status = ReviewStatus.PUBLISHED;
    touch(clock);
  }

  /** Removes a review permanently (terminal). */
  public void remove(Clock clock) {
    ensureMutable();
    status = ReviewStatus.REMOVED;
    touch(clock);
  }

  /** Applies the verification module's latest decision about the claimed relationship. */
  public void updateVerificationTier(VerificationTier tier, Clock clock) {
    ensureMutable();
    this.verificationTier = Objects.requireNonNull(tier, "tier");
    touch(clock);
  }

  /**
   * Brings a rejected or removed review back into public view after an appeal overturned the
   * decision that took it down.
   *
   * <p>This is the one way out of a terminal state, and it exists because the alternative is worse.
   * MODERATION.md requires an appeal to be able to change an outcome; without a way back, a
   * takedown demand that succeeds and then loses on appeal still gets exactly what it wanted, which
   * is the capture the anti-capture rules exist to prevent.
   *
   * <p>Narrow on purpose: it refuses anything that is not terminal, so it can never stand in for an
   * ordinary publish or restore. The caller reaching it is the moderation module's appeal path, and
   * the transition is audited like every other moderation action.
   *
   * @throws IllegalReviewStateTransitionException if the review was not taken down
   */
  public void reinstate(Clock clock) {
    if (!isTerminal()) {
      throw new IllegalReviewStateTransitionException(
          "only a review that was taken down can be reinstated, was " + status);
    }
    status = ReviewStatus.PUBLISHED;
    if (publishedAt == null) {
      publishedAt = clock.instant();
    }
    touch(clock);
  }

  public boolean isTerminal() {
    return status == ReviewStatus.REJECTED || status == ReviewStatus.REMOVED;
  }

  public Optional<ReviewVersion> currentVersion() {
    return Optional.ofNullable(currentVersion);
  }

  public ReviewId id() {
    return id;
  }

  public PropertyRef propertyRef() {
    return propertyRef;
  }

  public AuthorId authorId() {
    return authorId;
  }

  public RelationshipType relationshipType() {
    return relationshipType;
  }

  public Optional<ResidencePeriod> residencePeriod() {
    return Optional.ofNullable(residencePeriod);
  }

  public ReviewStatus status() {
    return status;
  }

  public VerificationTier verificationTier() {
    return verificationTier;
  }

  public Optional<Instant> publishedAt() {
    return Optional.ofNullable(publishedAt);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }

  private void ensureMutable() {
    if (isTerminal()) {
      throw new IllegalReviewStateTransitionException(
          "a " + status + " review is terminal and cannot be modified");
    }
  }

  private void checkInvariants() {
    if (status == ReviewStatus.PUBLISHED && publishedAt == null) {
      throw new IllegalArgumentException("a published review must record when it was published");
    }
    boolean requiresContent =
        status == ReviewStatus.PENDING_MODERATION
            || status == ReviewStatus.PUBLISHED
            || status == ReviewStatus.HIDDEN;
    if (requiresContent && currentVersion == null) {
      throw new IllegalArgumentException("a " + status + " review must have content");
    }
  }

  private void touch(Clock clock) {
    this.updatedAt = Objects.requireNonNull(clock, "clock").instant();
  }
}
