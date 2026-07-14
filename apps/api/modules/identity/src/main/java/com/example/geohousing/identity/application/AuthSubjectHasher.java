package com.example.geohousing.identity.application;

/**
 * Boundary port for turning a raw external authentication subject into its deterministic,
 * non-reversible stored representation. The HMAC implementation belongs to infrastructure.
 */
public interface AuthSubjectHasher {

  String hash(String rawAuthSubject);
}
