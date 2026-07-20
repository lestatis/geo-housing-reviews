package com.example.geohousing.properties.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyTest {

  private static final Clock CREATED =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock LATER =
      Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
  private static final CreatorId CREATOR = CreatorId.of(UUID.randomUUID());

  private static Property draft() {
    return Property.create(
        PropertyId.of(UUID.randomUUID()), PropertyType.BUILDING, "Vake Tower", CREATOR, CREATED);
  }

  @Test
  void createsADraftWithTimestampsAndNoAliasesOrSources() {
    Property property = draft();

    assertThat(property.status()).isEqualTo(PropertyStatus.DRAFT);
    assertThat(property.canonicalName()).isEqualTo("Vake Tower");
    assertThat(property.createdBy()).isEqualTo(CREATOR);
    assertThat(property.createdAt()).isEqualTo(CREATED.instant());
    assertThat(property.updatedAt()).isEqualTo(CREATED.instant());
    assertThat(property.version()).isZero();
    assertThat(property.aliases()).isEmpty();
    assertThat(property.sources()).isEmpty();
    assertThat(property.mergedIntoPropertyId()).isEmpty();
  }

  @Test
  void rejectsABlankCanonicalName() {
    assertThatThrownBy(
            () ->
                Property.create(
                    PropertyId.of(UUID.randomUUID()),
                    PropertyType.BUILDING,
                    "  ",
                    CREATOR,
                    CREATED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void activatesADraftAndBumpsUpdatedAt() {
    Property property = draft();

    property.activate(LATER);

    assertThat(property.status()).isEqualTo(PropertyStatus.ACTIVE);
    assertThat(property.updatedAt()).isEqualTo(LATER.instant());
  }

  @Test
  void cannotActivateANonDraft() {
    Property property = draft();
    property.activate(LATER);

    assertThatThrownBy(() -> property.activate(LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
  }

  @Test
  void hidesADraftOrActiveProperty() {
    Property fromDraft = draft();
    fromDraft.hide(LATER);
    assertThat(fromDraft.status()).isEqualTo(PropertyStatus.HIDDEN);

    Property fromActive = draft();
    fromActive.activate(LATER);
    fromActive.hide(LATER);
    assertThat(fromActive.status()).isEqualTo(PropertyStatus.HIDDEN);
  }

  @Test
  void mergesIntoAnotherPropertyRecordingTheTarget() {
    Property property = draft();
    PropertyId target = PropertyId.of(UUID.randomUUID());

    property.mergeInto(target, LATER);

    assertThat(property.status()).isEqualTo(PropertyStatus.MERGED);
    assertThat(property.isMerged()).isTrue();
    assertThat(property.mergedIntoPropertyId()).contains(target);
  }

  @Test
  void cannotMergeAPropertyIntoItself() {
    Property property = draft();

    assertThatThrownBy(() -> property.mergeInto(property.id(), LATER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aMergedPropertyIsTerminalAndRejectsAllChanges() {
    Property property = draft();
    property.mergeInto(PropertyId.of(UUID.randomUUID()), LATER);

    assertThatThrownBy(() -> property.mergeInto(PropertyId.of(UUID.randomUUID()), LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
    assertThatThrownBy(() -> property.hide(LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
    assertThatThrownBy(() -> property.rename("New", LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
    assertThatThrownBy(() -> property.addAlias(alias(), LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
    assertThatThrownBy(() -> property.setCoordinates(Coordinates.of(41.7, 44.8), LATER))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
  }

  @Test
  void addsAliasesAndSourcesAndExposesUnmodifiableCopies() {
    Property property = draft();
    property.addAlias(alias(), LATER);
    property.addSource(
        new PropertySource("city-registry", Instant.parse("2026-01-01T00:00:00Z"), null), LATER);

    assertThat(property.aliases()).hasSize(1);
    assertThat(property.sources()).hasSize(1);
    assertThatThrownBy(() -> property.aliases().add(alias()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsSelfParent() {
    Property property = draft();

    assertThatThrownBy(() -> property.setParent(property.id(), LATER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsAMergedStatusWithoutATarget() {
    assertThatThrownBy(
            () ->
                Property.reconstitute(
                    PropertyId.of(UUID.randomUUID()),
                    PropertyType.BUILDING,
                    PropertyStatus.MERGED,
                    "X",
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    CREATOR,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituteRejectsAMergeTargetWithoutMergedStatus() {
    assertThatThrownBy(
            () ->
                Property.reconstitute(
                    PropertyId.of(UUID.randomUUID()),
                    PropertyType.BUILDING,
                    PropertyStatus.ACTIVE,
                    "X",
                    null,
                    null,
                    null,
                    PropertyId.of(UUID.randomUUID()),
                    List.of(),
                    List.of(),
                    CREATOR,
                    CREATED.instant(),
                    CREATED.instant(),
                    0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static PropertyAlias alias() {
    return new PropertyAlias("ka", "ვაკე თაუერი", AliasSource.USER_SUBMITTED, 0.9);
  }
}
