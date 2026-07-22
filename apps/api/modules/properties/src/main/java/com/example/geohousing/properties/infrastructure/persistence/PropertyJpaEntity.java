package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.domain.PropertyStatus;
import com.example.geohousing.properties.domain.PropertyType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The property aggregate root persisted across four tables. The address is a cascaded
 * {@code @ManyToOne} and the aliases/sources are cascaded unidirectional {@code @OneToMany}s keyed
 * by {@code property_id}, so saving the root writes the whole graph in one transaction.
 */
@Entity
@Table(schema = "properties", name = "property")
class PropertyJpaEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PropertyType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PropertyStatus status;

  @Column(name = "canonical_name", nullable = false, length = 300)
  private String canonicalName;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "address_id")
  private AddressJpaEntity address;

  @Column(name = "parent_property_id")
  private UUID parentPropertyId;

  @Column(name = "merged_into_property_id")
  private UUID mergedIntoPropertyId;

  private Double latitude;

  private Double longitude;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "property_id", nullable = false)
  private List<PropertyAliasJpaEntity> aliases = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "property_id", nullable = false)
  private List<PropertySourceJpaEntity> sources = new ArrayList<>();

  protected PropertyJpaEntity() {}

  PropertyJpaEntity(
      UUID id,
      PropertyType type,
      PropertyStatus status,
      String canonicalName,
      AddressJpaEntity address,
      UUID parentPropertyId,
      UUID mergedIntoPropertyId,
      Double latitude,
      Double longitude,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<PropertyAliasJpaEntity> aliases,
      List<PropertySourceJpaEntity> sources) {
    this.id = id;
    this.type = type;
    this.status = status;
    this.canonicalName = canonicalName;
    this.address = address;
    this.parentPropertyId = parentPropertyId;
    this.mergedIntoPropertyId = mergedIntoPropertyId;
    this.latitude = latitude;
    this.longitude = longitude;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
    this.aliases = new ArrayList<>(aliases);
    this.sources = new ArrayList<>(sources);
  }

  UUID id() {
    return id;
  }

  PropertyType type() {
    return type;
  }

  PropertyStatus status() {
    return status;
  }

  String canonicalName() {
    return canonicalName;
  }

  AddressJpaEntity address() {
    return address;
  }

  UUID parentPropertyId() {
    return parentPropertyId;
  }

  UUID mergedIntoPropertyId() {
    return mergedIntoPropertyId;
  }

  Double latitude() {
    return latitude;
  }

  Double longitude() {
    return longitude;
  }

  UUID createdBy() {
    return createdBy;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  long version() {
    return version;
  }

  List<PropertyAliasJpaEntity> aliases() {
    return aliases;
  }

  List<PropertySourceJpaEntity> sources() {
    return sources;
  }

  /**
   * Applies an admin lifecycle transition: status, merge target and the update timestamp — and
   * nothing else. Name, address, aliases and sources are deliberately untouched; editing those
   * needs its own update path.
   */
  void applyLifecycleChange(
      PropertyStatus newStatus, UUID newMergedIntoPropertyId, Instant newUpdatedAt) {
    this.status = newStatus;
    this.mergedIntoPropertyId = newMergedIntoPropertyId;
    this.updatedAt = newUpdatedAt;
  }
}
