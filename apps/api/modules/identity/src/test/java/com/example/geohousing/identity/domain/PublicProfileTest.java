package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicProfileTest {

  private static final Clock CREATED =
      Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC);
  private static final Clock UPDATED =
      Clock.fixed(Instant.parse("2026-07-12T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  private PublicProfile defaultProfile() {
    return PublicProfile.createDefault(
        ACCOUNT_ID, PseudonymFormatter.defaultFrom("a3f9"), "en", CREATED);
  }

  @Test
  void createDefaultSetsPseudonymTimestampsAndNoAvatar() {
    PublicProfile profile = defaultProfile();

    assertThat(profile.accountId()).isEqualTo(ACCOUNT_ID);
    assertThat(profile.pseudonym().value()).isEqualTo("Reviewer-a3f9");
    assertThat(profile.avatarUrl()).isEmpty();
    assertThat(profile.locale()).isEqualTo("en");
    assertThat(profile.createdAt()).isEqualTo(CREATED.instant());
    assertThat(profile.updatedAt()).isEqualTo(CREATED.instant());
    assertThat(profile.version()).isZero();
  }

  @Test
  void updateProfileChangesFieldsAndStampsUpdatedAt() {
    PublicProfile profile = defaultProfile();

    profile.updateProfile(new Pseudonym("Anna K"), "https://cdn.example/a.png", "ka", UPDATED);

    assertThat(profile.pseudonym().value()).isEqualTo("Anna K");
    assertThat(profile.avatarUrl()).contains("https://cdn.example/a.png");
    assertThat(profile.locale()).isEqualTo("ka");
    assertThat(profile.updatedAt()).isEqualTo(UPDATED.instant());
  }

  @Test
  void updateProfileRejectsBlankLocale() {
    PublicProfile profile = defaultProfile();

    assertThatThrownBy(() -> profile.updateProfile(new Pseudonym("Anna K"), null, "  ", UPDATED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anonymizeReplacesPseudonymWithUniqueTombstoneAndDropsAvatar() {
    PublicProfile profile = defaultProfile();
    profile.updateProfile(new Pseudonym("Anna K"), "https://cdn.example/a.png", "ka", CREATED);

    profile.anonymize(UPDATED);

    assertThat(profile.pseudonym().value())
        .startsWith("del-")
        .isNotEqualTo("Anna K")
        .hasSizeLessThanOrEqualTo(Pseudonym.MAX_LENGTH);
    assertThat(profile.avatarUrl()).isEmpty();
    assertThat(profile.updatedAt()).isEqualTo(UPDATED.instant());
  }

  @Test
  void anonymizeTombstoneDiffersPerAccount() {
    PublicProfile first = defaultProfile();
    PublicProfile second =
        PublicProfile.createDefault(
            AccountId.of(UUID.randomUUID()), PseudonymFormatter.defaultFrom("b7c2"), "en", CREATED);

    first.anonymize(UPDATED);
    second.anonymize(UPDATED);

    assertThat(first.pseudonym().value()).isNotEqualTo(second.pseudonym().value());
  }
}
