package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AdminAuditEvent;

/** Application port for appending to the admin audit trail. Insert-only by contract. */
public interface AdminAuditEventRepository {

  void record(AdminAuditEvent event);
}
