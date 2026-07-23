package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationTier;

/**
 * Outbound port projecting a verification decision onto the account's review of a property. The
 * verification module never reads or writes the reviews tables (ARCHITECTURE: modules own their
 * data); the adapter that implements this calls the reviews module's inbound published api.
 *
 * <p>The projection is one-way: verification pushes, reviews never asks back. That is what keeps
 * the two modules acyclic.
 */
public interface ReviewProjection {

  /**
   * Sets the tier on the account's live review of the property, if one exists. A no-op when the
   * account has not (yet) reviewed the property — the badge lives on the case regardless.
   */
  void applyTier(AccountRef accountRef, PropertyRef propertyRef, VerificationTier tier);
}
