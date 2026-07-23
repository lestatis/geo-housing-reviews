/**
 * Infrastructure layer of the verification module. Currently the outbound adapters to other
 * modules' published apis: {@code properties} (property resolution) and {@code reviews} (tier
 * projection). The application services and persistence adapters are wired once the JPA
 * repositories exist.
 */
package com.example.geohousing.verification.infrastructure;
