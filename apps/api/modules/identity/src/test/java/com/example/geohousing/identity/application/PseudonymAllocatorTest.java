package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PseudonymAllocatorTest {

  @Test
  void retriesWhenGeneratedPseudonymIsAlreadyInUse() {
    Iterator<String> suffixes = List.of("taken", "available").iterator();
    PseudonymAllocator allocator =
        new PseudonymAllocator(
            new TakenPseudonymRepository(Set.of("Reviewer-taken")), suffixes::next);

    Pseudonym result = allocator.allocateDefault();

    assertThat(result.value()).isEqualTo("Reviewer-available");
  }

  @Test
  void failsInsteadOfLoopingForeverWhenNoCandidateIsAvailable() {
    PseudonymAllocator allocator =
        new PseudonymAllocator(
            new TakenPseudonymRepository(Set.of("Reviewer-taken")), () -> "taken");

    assertThatThrownBy(allocator::allocateDefault)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("could not allocate an unused default pseudonym");
  }

  private static final class TakenPseudonymRepository implements PublicProfileRepository {

    private final Set<String> takenPseudonyms;

    private TakenPseudonymRepository(Set<String> takenPseudonyms) {
      this.takenPseudonyms = takenPseudonyms;
    }

    @Override
    public Optional<PublicProfile> findByAccountId(AccountId accountId) {
      return Optional.empty();
    }

    @Override
    public boolean isPseudonymInUse(Pseudonym pseudonym) {
      return takenPseudonyms.contains(pseudonym.value());
    }

    @Override
    public PublicProfile save(PublicProfile profile, long expectedVersion) {
      return profile;
    }
  }
}
