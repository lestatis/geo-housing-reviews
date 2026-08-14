package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.identity.api.RestrictedAccountException;
import com.example.geohousing.moderation.application.AppealAlreadyFiledException;
import com.example.geohousing.moderation.application.AppealNotFoundException;
import com.example.geohousing.moderation.application.DuplicateReportException;
import com.example.geohousing.moderation.application.ModerationCaseNotFoundException;
import com.example.geohousing.moderation.application.ModerationEffectConflictException;
import com.example.geohousing.moderation.application.ModerationTargetNotFoundException;
import com.example.geohousing.moderation.application.NotTheAffectedAuthorException;
import com.example.geohousing.moderation.application.NothingToAppealException;
import com.example.geohousing.moderation.application.ReportNotFoundException;
import com.example.geohousing.moderation.application.SelfReportNotAllowedException;
import com.example.geohousing.moderation.domain.AppealDeciderConflictException;
import com.example.geohousing.moderation.domain.IllegalModerationStateTransitionException;
import com.example.geohousing.moderation.domain.StaleModerationWriteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps moderation errors to RFC 7807 Problem Details (see {@code docs/API_GUIDELINES.md}).
 *
 * <p>Scoped to this module's web package, so one module's advice never answers for another's
 * exceptions. 401 stays with the security chain.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.moderation.infrastructure.web")
class ModerationExceptionHandler {

  /**
   * Content the caller may not see is reported as missing. Anything else would turn the reporting
   * endpoint into a way to discover unpublished or removed reviews by probing identifiers.
   */
  /**
   * A restricted account tried to contribute.
   *
   * <p>Identity owns the restriction and the reason for it; this only reports that one is in force.
   * The detail deliberately says nothing about why or until when — that belongs in one place, not
   * repeated by every module that has to refuse.
   */
  @ExceptionHandler(RestrictedAccountException.class)
  ProblemDetail handleRestrictedAccount(RestrictedAccountException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Account restricted",
        "ACCOUNT_RESTRICTED",
        "This account is currently restricted and cannot contribute.");
  }

  @ExceptionHandler(ModerationTargetNotFoundException.class)
  ProblemDetail handleTargetNotFound(ModerationTargetNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Content not found",
        "REPORT_TARGET_NOT_FOUND",
        "No reportable content was found for this identifier.");
  }

  /**
   * Someone else's report is missing rather than forbidden, for the same reason: a 403 would
   * confirm that a report exists for that identifier.
   */
  @ExceptionHandler(ReportNotFoundException.class)
  ProblemDetail handleReportNotFound(ReportNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Report not found",
        "REPORT_NOT_FOUND",
        "No report was found for this identifier.");
  }

  /**
   * Forbidden rather than hidden: the author wrote the content, so they already know it exists and
   * explaining the refusal discloses nothing they could not see.
   */
  @ExceptionHandler(SelfReportNotAllowedException.class)
  ProblemDetail handleSelfReport(SelfReportNotAllowedException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Cannot report your own content",
        "SELF_REPORT_NOT_ALLOWED",
        "An author cannot report their own content. Edit or remove it instead.");
  }

  @ExceptionHandler(DuplicateReportException.class)
  ProblemDetail handleDuplicateReport(DuplicateReportException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Report already open",
        "REPORT_ALREADY_EXISTS",
        "You already have an open report about this content.");
  }

  @ExceptionHandler(ModerationCaseNotFoundException.class)
  ProblemDetail handleCaseNotFound(ModerationCaseNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Case not found",
        "MODERATION_CASE_NOT_FOUND",
        "No moderation case was found for this identifier.");
  }

  @ExceptionHandler(StaleModerationWriteException.class)
  ProblemDetail handleStaleWrite(StaleModerationWriteException exception) {
    // 409, not 500: the moderator did nothing wrong, and the answer is "read it again and decide",
    // which is a conflict rather than a fault.
    return problem(
        HttpStatus.CONFLICT,
        "Changed since you read it",
        "STALE_MODERATION_WRITE",
        exception.getMessage());
  }

  @ExceptionHandler(ModerationEffectConflictException.class)
  ProblemDetail handleEffectConflict(ModerationEffectConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Content changed",
        "MODERATION_TARGET_CHANGED",
        "The content changed since it was read. Review it again before deciding.");
  }

  @ExceptionHandler(IllegalModerationStateTransitionException.class)
  ProblemDetail handleIllegalTransition(IllegalModerationStateTransitionException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Not allowed in this state",
        "MODERATION_STATE_CONFLICT",
        exception.getMessage());
  }

  /**
   * Forbidden, not hidden: the caller can see their content was acted on, so refusing plainly
   * discloses nothing they could not already tell.
   */
  @ExceptionHandler(NotTheAffectedAuthorException.class)
  ProblemDetail handleNotTheAuthor(NotTheAffectedAuthorException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Not your decision to appeal",
        "NOT_THE_AFFECTED_AUTHOR",
        "Only the author a decision was made against may appeal it.");
  }

  /** Due process, not a malformed request — hence 403 rather than 400. */
  @ExceptionHandler(AppealDeciderConflictException.class)
  ProblemDetail handleAppealDeciderConflict(AppealDeciderConflictException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Cannot hear this appeal",
        "APPEAL_DECIDER_CONFLICT",
        "An appeal must be decided by someone other than the moderator who decided the case.");
  }

  @ExceptionHandler(AppealAlreadyFiledException.class)
  ProblemDetail handleAppealAlreadyFiled(AppealAlreadyFiledException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Already appealed",
        "APPEAL_ALREADY_FILED",
        "This decision has already been appealed.");
  }

  @ExceptionHandler(NothingToAppealException.class)
  ProblemDetail handleNothingToAppeal(NothingToAppealException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Nothing to appeal",
        "NOTHING_TO_APPEAL",
        "No decision that took anything away was made about this content.");
  }

  @ExceptionHandler(AppealNotFoundException.class)
  ProblemDetail handleAppealNotFound(AppealNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Appeal not found",
        "APPEAL_NOT_FOUND",
        "No appeal was found for this identifier.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "INVALID_REQUEST", exception.getMessage());
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setProperty("code", code);
    return problem;
  }
}
