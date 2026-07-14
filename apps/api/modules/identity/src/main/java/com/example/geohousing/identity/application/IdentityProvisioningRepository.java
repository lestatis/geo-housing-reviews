package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AuthSubjectAlreadyProvisionedException;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;

/**
 * Persistence boundary for first-login provisioning. Implementations must create the account and
 * its default profile atomically.
 */
public interface IdentityProvisioningRepository {

  /**
   * Creates the account and its profile, or neither.
   *
   * @throws AuthSubjectAlreadyProvisionedException if a concurrent first request already created an
   *     account for the same subject hash — the caller should re-read that account rather than
   *     retrying the insert
   * @throws PseudonymAlreadyInUseException if the allocated pseudonym was taken between allocation
   *     and insert; pseudonym allocation is a check-then-act, so the unique constraint is the real
   *     authority and the caller should allocate again
   */
  void create(Account account, PublicProfile profile);
}
