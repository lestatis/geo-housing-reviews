package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.application.AdminAccountService;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  AdminAccountController(AdminAccountService adminAccountService) {
    this.adminAccountService = adminAccountService;
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
}
