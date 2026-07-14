package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymFormatter;
import java.util.Objects;
import java.util.function.Supplier;

/** Allocates an unused generated pseudonym through the public-profile persistence boundary. */
public final class PseudonymAllocator {

  private static final int MAX_ATTEMPTS = 100;

  private final PublicProfileRepository publicProfileRepository;
  private final Supplier<String> suffixSupplier;

  public PseudonymAllocator(
      PublicProfileRepository publicProfileRepository, Supplier<String> suffixSupplier) {
    this.publicProfileRepository =
        Objects.requireNonNull(publicProfileRepository, "publicProfileRepository");
    this.suffixSupplier = Objects.requireNonNull(suffixSupplier, "suffixSupplier");
  }

  public Pseudonym allocateDefault() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      Pseudonym candidate = PseudonymFormatter.defaultFrom(suffixSupplier.get());
      if (!publicProfileRepository.isPseudonymInUse(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("could not allocate an unused default pseudonym");
  }
}
