package com.example.geohousing.properties.domain;

/** The lifecycle action an admin performed. Mirrors the audit table's {@code action} check. */
public enum PropertyAdminAction {
  ACTIVATE,
  HIDE,
  MERGE
}
