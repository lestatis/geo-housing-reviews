package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.PublicProfile;

/** Persistence boundary for applying an account deletion (closure + profile anonymization). */
public interface AccountDeletionRepository {

  /** Persists the closed account and its anonymized profile atomically. */
  void applyDeletion(Account closedAccount, PublicProfile anonymizedProfile);
}
