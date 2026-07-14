package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.PublicProfile;

/**
 * Persistence boundary for first-login provisioning. Implementations must create the account and
 * its default profile atomically.
 */
public interface IdentityProvisioningRepository {

  void create(Account account, PublicProfile profile);
}
