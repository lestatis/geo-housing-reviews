package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.PropertyRef;
import java.util.Optional;

/**
 * Outbound port asking the properties module about a property a verification case is about. The
 * verification module never reads the properties tables (ARCHITECTURE: modules own their data); the
 * adapter that implements this calls the properties module's published api.
 *
 * <p>The only question verification needs answered is: does the property exist, and — since
 * properties can be merged — which property should the case actually attach to. Whether the
 * property currently accepts reviews is not relevant: an account may verify a past relationship to
 * a property that is now withheld.
 */
public interface PropertyLookup {

  /**
   * Resolves a property to the record that survived any merges, or empty if no such property
   * exists. The returned reference is the surviving property, which may differ from the requested
   * one.
   */
  Optional<PropertyRef> resolve(PropertyRef propertyRef);
}
