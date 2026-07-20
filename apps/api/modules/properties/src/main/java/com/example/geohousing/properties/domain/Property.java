package com.example.geohousing.properties.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A canonical reviewable object — a building, complex, block or phase (see {@code
 * docs/DOMAIN_MODEL.md}). Created as a {@code DRAFT}; a moderator later {@code activate}s, {@code
 * hide}s, or {@code merge}s it. {@code MERGED} is terminal: a merged property records the property
 * it was folded into and rejects all further changes. Invariants (a merge target only when merged,
 * no self-parent, no self-merge) mirror the check constraints on {@code properties.property}.
 */
public final class Property {

  private final PropertyId id;
  private final PropertyType type;
  private PropertyStatus status;
  private String canonicalName;
  private Address address;
  private Coordinates coordinates;
  private PropertyId parentPropertyId;
  private PropertyId mergedIntoPropertyId;
  private final List<PropertyAlias> aliases;
  private final List<PropertySource> sources;
  private final CreatorId createdBy;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private Property(
      PropertyId id,
      PropertyType type,
      PropertyStatus status,
      String canonicalName,
      Address address,
      Coordinates coordinates,
      PropertyId parentPropertyId,
      PropertyId mergedIntoPropertyId,
      List<PropertyAlias> aliases,
      List<PropertySource> sources,
      CreatorId createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.type = Objects.requireNonNull(type, "type");
    this.status = Objects.requireNonNull(status, "status");
    this.canonicalName = requireText(canonicalName, "canonicalName");
    this.address = address;
    this.coordinates = coordinates;
    this.parentPropertyId = parentPropertyId;
    this.mergedIntoPropertyId = mergedIntoPropertyId;
    this.aliases = new ArrayList<>(Objects.requireNonNull(aliases, "aliases"));
    this.sources = new ArrayList<>(Objects.requireNonNull(sources, "sources"));
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
    checkInvariants();
  }

  /** Creates a new {@code DRAFT} property owned by the given creator. */
  public static Property create(
      PropertyId id, PropertyType type, String canonicalName, CreatorId createdBy, Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    return new Property(
        id,
        type,
        PropertyStatus.DRAFT,
        canonicalName,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        createdBy,
        now,
        now,
        0L);
  }

  /** Rebuilds a property from persisted state. Intended for persistence adapters only. */
  public static Property reconstitute(
      PropertyId id,
      PropertyType type,
      PropertyStatus status,
      String canonicalName,
      Address address,
      Coordinates coordinates,
      PropertyId parentPropertyId,
      PropertyId mergedIntoPropertyId,
      List<PropertyAlias> aliases,
      List<PropertySource> sources,
      CreatorId createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new Property(
        id,
        type,
        status,
        canonicalName,
        address,
        coordinates,
        parentPropertyId,
        mergedIntoPropertyId,
        aliases,
        sources,
        createdBy,
        createdAt,
        updatedAt,
        version);
  }

  /** Publishes a draft. */
  public void activate(Clock clock) {
    if (status != PropertyStatus.DRAFT) {
      throw new IllegalPropertyStateTransitionException(
          "only a DRAFT property can be activated, was " + status);
    }
    this.status = PropertyStatus.ACTIVE;
    touch(clock);
  }

  /** Withholds a draft or active property from public view. */
  public void hide(Clock clock) {
    if (status != PropertyStatus.DRAFT && status != PropertyStatus.ACTIVE) {
      throw new IllegalPropertyStateTransitionException("cannot hide a " + status + " property");
    }
    this.status = PropertyStatus.HIDDEN;
    touch(clock);
  }

  /** Folds this property into another (terminal). */
  public void mergeInto(PropertyId target, Clock clock) {
    Objects.requireNonNull(target, "target");
    if (status == PropertyStatus.MERGED) {
      throw new IllegalPropertyStateTransitionException("property is already merged");
    }
    if (target.equals(id)) {
      throw new IllegalArgumentException("a property cannot be merged into itself");
    }
    this.status = PropertyStatus.MERGED;
    this.mergedIntoPropertyId = target;
    touch(clock);
  }

  public void rename(String newCanonicalName, Clock clock) {
    ensureMutable();
    this.canonicalName = requireText(newCanonicalName, "canonicalName");
    touch(clock);
  }

  public void setAddress(Address newAddress, Clock clock) {
    ensureMutable();
    this.address = newAddress;
    touch(clock);
  }

  public void setCoordinates(Coordinates newCoordinates, Clock clock) {
    ensureMutable();
    this.coordinates = newCoordinates;
    touch(clock);
  }

  public void setParent(PropertyId newParent, Clock clock) {
    ensureMutable();
    if (newParent != null && newParent.equals(id)) {
      throw new IllegalArgumentException("a property cannot be its own parent");
    }
    this.parentPropertyId = newParent;
    touch(clock);
  }

  public void addAlias(PropertyAlias alias, Clock clock) {
    ensureMutable();
    this.aliases.add(Objects.requireNonNull(alias, "alias"));
    touch(clock);
  }

  public void addSource(PropertySource source, Clock clock) {
    ensureMutable();
    this.sources.add(Objects.requireNonNull(source, "source"));
    touch(clock);
  }

  public boolean isMerged() {
    return status == PropertyStatus.MERGED;
  }

  public PropertyId id() {
    return id;
  }

  public PropertyType type() {
    return type;
  }

  public PropertyStatus status() {
    return status;
  }

  public String canonicalName() {
    return canonicalName;
  }

  public Optional<Address> address() {
    return Optional.ofNullable(address);
  }

  public Optional<Coordinates> coordinates() {
    return Optional.ofNullable(coordinates);
  }

  public Optional<PropertyId> parentPropertyId() {
    return Optional.ofNullable(parentPropertyId);
  }

  public Optional<PropertyId> mergedIntoPropertyId() {
    return Optional.ofNullable(mergedIntoPropertyId);
  }

  public List<PropertyAlias> aliases() {
    return List.copyOf(aliases);
  }

  public List<PropertySource> sources() {
    return List.copyOf(sources);
  }

  public CreatorId createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }

  private void ensureMutable() {
    if (status == PropertyStatus.MERGED) {
      throw new IllegalPropertyStateTransitionException("a merged property cannot be modified");
    }
  }

  private void checkInvariants() {
    boolean hasMergeTarget = mergedIntoPropertyId != null;
    if (hasMergeTarget != (status == PropertyStatus.MERGED)) {
      throw new IllegalArgumentException(
          "a merge target must be set exactly when the status is MERGED");
    }
    if (parentPropertyId != null && parentPropertyId.equals(id)) {
      throw new IllegalArgumentException("a property cannot be its own parent");
    }
    if (mergedIntoPropertyId != null && mergedIntoPropertyId.equals(id)) {
      throw new IllegalArgumentException("a property cannot be merged into itself");
    }
  }

  private void touch(Clock clock) {
    this.updatedAt = Objects.requireNonNull(clock, "clock").instant();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
