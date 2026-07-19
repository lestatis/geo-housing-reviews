package com.example.geohousing.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the HMAC pepper used to hash external auth subjects before storage (ADR-0006). The value is
 * injected from the environment ({@code IDENTITY_AUTH_SUBJECT_PEPPER}) and is never committed; a
 * blank value fails fast at startup in {@code HmacAuthSubjectHasher} rather than hashing with an
 * empty key.
 */
@ConfigurationProperties(prefix = "identity.auth")
public record IdentitySecurityProperties(String subjectPepper) {}
