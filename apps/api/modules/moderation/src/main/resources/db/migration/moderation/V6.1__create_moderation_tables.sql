-- Moderation module core tables (docs/DOMAIN_MODEL.md Moderation context; docs/MODERATION.md).
--
-- The split with the reviews module: reviews owns a review's publication *state* and audits the
-- transitions it applies; moderation owns the *case workflow* — who reported what, which moderator
-- decided, under which policy version, and whether that decision was appealed. A decision's effect
-- on a review is applied through the reviews module's published contract, never by touching its
-- tables (ARCHITECTURE boundary rules).
--
-- target_id, reporter_account_id, appellant_account_id and every *_moderator/decider account id are
-- opaque UUIDs, NOT foreign keys: reviews and accounts belong to other modules.
--
-- target_type is a single-value CHECK today. Reports against representative replies, properties and
-- accounts arrive with later plans; keeping the accepted vocabulary explicit per migration makes
-- each widening a deliberate, reviewable change rather than a silent one.

CREATE SCHEMA IF NOT EXISTS moderation;

-- A case is the unit of work: one piece of content under review, however many people reported it.
CREATE TABLE moderation.moderation_case (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type                   VARCHAR(20) NOT NULL CHECK (target_type IN ('REVIEW')),
    target_id                     UUID        NOT NULL,
    -- LEGAL_REQUEST exists so an owner's or developer's takedown demand travels the same audited
    -- workflow as any other report (MODERATION.md anti-capture rules) rather than a private channel.
    trigger_source                VARCHAR(20) NOT NULL
                                  CHECK (trigger_source IN
                                         ('REPORT', 'PRE_MODERATION', 'AUTOMATED', 'LEGAL_REQUEST')),
    status                        VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                                  CHECK (status IN
                                         ('OPEN', 'IN_REVIEW', 'DECIDED', 'APPEALED', 'CLOSED')),
    risk_level                    VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
                                  CHECK (risk_level IN ('STANDARD', 'HIGH', 'LEGAL')),
    assigned_moderator_account_id UUID,
    opened_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- SLA measurement points. MODERATION.md requires moderation to be measurable; these are the
    -- two timestamps every queue metric needs.
    first_response_at             TIMESTAMPTZ,
    closed_at                     TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                       BIGINT      NOT NULL DEFAULT 0,
    -- A case being worked has someone accountable for it.
    CONSTRAINT moderation_case_in_review_has_assignee
        CHECK (status <> 'IN_REVIEW' OR assigned_moderator_account_id IS NOT NULL),
    CONSTRAINT moderation_case_closed_has_closed_at
        CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL),
    CONSTRAINT moderation_case_closed_at_after_opened
        CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

-- Twenty reports about one review must converge on one case, not open twenty. Without this a
-- coordinated group could bury the queue in duplicates and drown out unrelated content, which is
-- itself the brigading MODERATION.md asks the platform to resist. CLOSED is the only terminal
-- status, so a settled case does not block a fresh case if the content is reported again later.
CREATE UNIQUE INDEX moderation_case_one_live_per_target_idx
    ON moderation.moderation_case (target_type, target_id)
    WHERE status <> 'CLOSED';

-- The moderator queue read path: oldest untouched case first.
CREATE INDEX moderation_case_queue_idx
    ON moderation.moderation_case (status, opened_at);

-- A report is one person's account of what is wrong. It is evidence for a case, never a decision:
-- MODERATION.md is explicit that a "false claim" report does not automatically remove content.
CREATE TABLE moderation.report (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type         VARCHAR(20) NOT NULL CHECK (target_type IN ('REVIEW')),
    target_id           UUID        NOT NULL,
    -- The reporter is recorded so abuse of the reporting channel is traceable and so a reporter can
    -- read their own report's progress. It is never exposed to the reported author or in any public
    -- representation: SECURITY_PRIVACY.md treats attempts to identify critics as an abuse path.
    reporter_account_id UUID        NOT NULL,
    category            VARCHAR(40) NOT NULL
                        CHECK (category IN
                               ('PERSONAL_DATA', 'FALSE_OR_MISLEADING', 'HARASSMENT_OR_THREAT',
                                'CONFLICT_OF_INTEREST', 'NOT_ABOUT_THIS_PROPERTY',
                                'DUPLICATE_OR_SPAM', 'COPYRIGHT_OR_MEDIA', 'OUTDATED_OR_RESOLVED',
                                'OTHER')),
    description         TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'LINKED', 'RESOLVED', 'DISMISSED')),
    case_id             UUID        REFERENCES moderation.moderation_case (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- "Other" without an explanation gives a moderator nothing to act on.
    CONSTRAINT report_other_requires_description
        CHECK (category <> 'OTHER'
               OR (description IS NOT NULL AND length(btrim(description)) > 0)),
    -- Anything past intake has been attached to the case it is evidence for.
    CONSTRAINT report_triaged_has_case
        CHECK (status = 'OPEN' OR case_id IS NOT NULL)
);

-- One live report per account per target: an account may raise an issue, not raise it fifty times.
-- Re-reporting is allowed once the earlier report reached a terminal state, because a genuinely new
-- problem with the same content deserves to be heard.
CREATE UNIQUE INDEX report_one_live_per_reporter_target_idx
    ON moderation.report (reporter_account_id, target_type, target_id)
    WHERE status IN ('OPEN', 'LINKED');

CREATE INDEX report_target_idx
    ON moderation.report (target_type, target_id);

-- A reporter reading their own reports; also the only index that read path may use.
CREATE INDEX report_reporter_idx
    ON moderation.report (reporter_account_id, created_at DESC);

-- Decisions are append-only. An appeal must be able to show what was decided, by whom, and under
-- which policy the content was judged (MODERATION.md appeals: "preserve original decision and
-- policy version"), so a decision is never updated — a changed outcome is a new row. Hence no
-- updated_at and no optimistic-lock version column here.
CREATE TABLE moderation.moderation_decision (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id                 UUID        NOT NULL REFERENCES moderation.moderation_case (id),
    action                  VARCHAR(30) NOT NULL
                            CHECK (action IN
                                   ('APPROVE', 'APPROVE_WITH_REDACTION', 'REQUEST_CHANGES',
                                    'REJECT', 'HIDE', 'REMOVE', 'RESTRICT_ACCOUNT', 'ESCALATE')),
    reason_code             VARCHAR(64) NOT NULL,
    policy_version          INT         NOT NULL DEFAULT 1,
    -- Shown to the affected user. Deliberately separate from internal_note, which records abuse
    -- signals and must never reach the user: MODERATION.md wants an explanation specific enough to
    -- correct the issue without exposing how detection works.
    public_explanation      TEXT,
    internal_note           TEXT,
    -- The version of the content that was judged, so an edit after the decision is visibly a
    -- different thing from what the moderator read.
    affected_target_version BIGINT,
    decided_by_account_id   UUID        NOT NULL,
    decided_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT moderation_decision_reason_code_not_blank
        CHECK (length(btrim(reason_code)) > 0),
    -- Anything that costs the user something has to tell them why. APPROVE takes nothing away and
    -- ESCALATE is an internal handoff that has not yet decided anything.
    CONSTRAINT moderation_decision_adverse_has_public_explanation
        CHECK (action IN ('APPROVE', 'ESCALATE')
               OR (public_explanation IS NOT NULL AND length(btrim(public_explanation)) > 0))
);

CREATE INDEX moderation_decision_case_idx
    ON moderation.moderation_decision (case_id, decided_at DESC);

-- One structured appeal per decision (MODERATION.md), decided by someone other than the person
-- being appealed against.
CREATE TABLE moderation.appeal (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id                 UUID        NOT NULL UNIQUE
                                REFERENCES moderation.moderation_decision (id),
    appellant_account_id        UUID        NOT NULL,
    appeal_text                 TEXT        NOT NULL CHECK (length(btrim(appeal_text)) > 0),
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING', 'UPHELD', 'OVERTURNED')),
    outcome_explanation         TEXT,
    -- Copied from the decision being appealed so that "a different reviewer decides the appeal" is
    -- a single-row CHECK the database enforces, rather than a rule living only in a service that a
    -- future code path could forget to call. Due process is not something to leave to convention.
    original_decider_account_id UUID        NOT NULL,
    decided_by_account_id       UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at                  TIMESTAMPTZ,
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT appeal_different_decider
        CHECK (decided_by_account_id IS NULL
               OR decided_by_account_id <> original_decider_account_id),
    CONSTRAINT appeal_pending_iff_undecided
        CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT appeal_decided_has_decider
        CHECK (status = 'PENDING' OR decided_by_account_id IS NOT NULL),
    -- An overturned or upheld appeal owes the appellant a reason.
    CONSTRAINT appeal_decided_has_outcome
        CHECK (status = 'PENDING'
               OR (outcome_explanation IS NOT NULL AND length(btrim(outcome_explanation)) > 0))
);

-- The pending-appeals queue.
CREATE INDEX appeal_queue_idx
    ON moderation.appeal (status, created_at);
