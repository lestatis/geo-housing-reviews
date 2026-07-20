package com.example.geohousing.properties.infrastructure.persistence;

import java.util.UUID;

/** Spring Data projection for the native duplicate-candidate query (id + name only). */
interface PropertyCandidateProjection {

  UUID getId();

  String getCanonicalName();
}
