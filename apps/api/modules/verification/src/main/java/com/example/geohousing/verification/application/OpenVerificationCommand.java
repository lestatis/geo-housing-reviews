package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.util.Objects;

/**
 * Request to open a verification case. The account comes from the authenticated caller, never from
 * the body, so an account cannot open a case as somebody else.
 */
public record OpenVerificationCommand(
    AccountRef accountRef,
    PropertyRef propertyRef,
    RelationshipClaim relationshipClaim,
    VerificationMethod method) {

  public OpenVerificationCommand {
    Objects.requireNonNull(accountRef, "accountRef");
    Objects.requireNonNull(propertyRef, "propertyRef");
    Objects.requireNonNull(relationshipClaim, "relationshipClaim");
    Objects.requireNonNull(method, "method");
  }
}
