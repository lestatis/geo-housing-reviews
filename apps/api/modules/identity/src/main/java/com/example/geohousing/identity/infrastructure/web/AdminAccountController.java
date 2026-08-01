package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.application.AccountRoleService;
import com.example.geohousing.identity.application.AdminAccountService;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  private final AccountRoleService accountRoleService;

  AdminAccountController(
      AdminAccountService adminAccountService, AccountRoleService accountRoleService) {
    this.adminAccountService = adminAccountService;
    this.accountRoleService = accountRoleService;
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
