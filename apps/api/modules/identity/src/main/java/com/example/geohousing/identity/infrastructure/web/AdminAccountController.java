package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.api.AccountRoleUseCase;
import com.example.geohousing.identity.application.AccountRestrictionService;
import com.example.geohousing.identity.application.AdminAccountService;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.RestrictionScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin account lookup. The {@code ROLE_ADMIN} gate is enforced by the security filter chain
 * ({@code /api/admin/**}); reaching this controller already implies an authenticated admin. Every
 * lookup is audited by {@link AdminAccountService}, including lookups of accounts that do not
 * exist.
 */
@RestController
@RequestMapping("/api/admin/accounts")
class AdminAccountController {

  private final AdminAccountService adminAccountService;
  private final AccountRoleUseCase accountRoleService;
  private final AccountRestrictionService accountRestrictionService;
  private final Clock clock;

  AdminAccountController(
      AdminAccountService adminAccountService,
      AccountRoleUseCase accountRoleService,
      AccountRestrictionService accountRestrictionService,
      Clock identityClock) {
    this.adminAccountService = adminAccountService;
    this.accountRoleService = accountRoleService;
    this.accountRestrictionService = accountRestrictionService;
    this.clock = identityClock;
  }

  /**
   * Finds the account behind a public pseudonym.
   *
   * <p>A moderator working a reported review has the pseudonym and nothing else. Audited exactly
   * like a lookup by id — this is the step that turns a public name into a private account.
   */
  @GetMapping(params = "pseudonym")
  AdminAccountView findByPseudonym(
      Authentication authentication, @RequestParam("pseudonym") String pseudonym) {
    return adminAccountService
        .viewAccountByPseudonym(
            WebAuthentication.accountId(authentication), Pseudonym.of(pseudonym))
        .map(AdminAccountView::from)
        .orElseThrow(() -> new AccountNotFoundException("no account for that pseudonym"));
  }

  @PostMapping("/{accountId}/restrictions")
  RestrictionView restrict(
      Authentication authentication,
      @PathVariable("accountId") String accountId,
      @RequestBody RestrictAccountRequest request) {
    return RestrictionView.from(
        accountRestrictionService.restrict(
            WebAuthentication.accountId(authentication),
            AccountId.of(UUID.fromString(accountId)),
            parseScope(request.scope()),
            request.reason(),
            request.endAt()),
        clock.instant());
  }

  @PostMapping("/{accountId}/restrictions/{restrictionId}/lift")
  RestrictionView lift(
      Authentication authentication,
      @PathVariable("accountId") String accountId,
      @PathVariable("restrictionId") String restrictionId) {
    return RestrictionView.from(
        accountRestrictionService.lift(
            WebAuthentication.accountId(authentication),
            AccountId.of(UUID.fromString(accountId)),
            UUID.fromString(restrictionId)),
        clock.instant());
  }

  /** Every restriction ever placed, so a moderator can judge a pattern rather than one incident. */
  @GetMapping("/{accountId}/restrictions")
  RestrictionListResponse restrictions(@PathVariable("accountId") String accountId) {
    Instant asOf = clock.instant();
    return new RestrictionListResponse(
        accountRestrictionService.history(AccountId.of(UUID.fromString(accountId))).stream()
            .map(restriction -> RestrictionView.from(restriction, asOf))
            .toList());
  }

  private static RestrictionScope parseScope(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    try {
      return RestrictionScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown restriction scope: " + scope);
    }
  }

  @GetMapping("/{accountId}")
  AdminAccountView getAccount(
      Authentication authentication, @PathVariable("accountId") String accountId) {
    // A malformed UUID throws IllegalArgumentException, mapped to 400 by IdentityExceptionHandler.
    AccountId target = AccountId.of(UUID.fromString(accountId));
    return adminAccountService
        .viewAccount(WebAuthentication.accountId(authentication), target)
        .map(AdminAccountView::from)
        .orElseThrow(() -> new AccountNotFoundException(target));
  }

  /**
   * Grants or removes administrative access.
   *
   * <p>Until this existed the only way to make an administrator was to edit the database, which is
   * incompatible with operating the platform without database access. Refusals — acting on
   * yourself, or demoting the last administrator — are recorded as attempts, not swallowed.
   */
  @PatchMapping("/{accountId}/role")
  AdminAccountView changeRole(
      Authentication authentication,
      @PathVariable("accountId") String accountId,
      @RequestBody ChangeRoleRequest request) {
    AccountId target = AccountId.of(UUID.fromString(accountId));
    if (request.version() == null) {
      throw new IllegalArgumentException("version is required so a stale view cannot be acted on");
    }
    return AdminAccountView.from(
        accountRoleService.changeRole(
            WebAuthentication.accountId(authentication),
            target,
            parseRole(request.role()),
            request.version()));
  }

  private static AccountRole parseRole(String role) {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role must not be blank");
    }
    try {
      return AccountRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown role: " + role);
    }
  }
}
