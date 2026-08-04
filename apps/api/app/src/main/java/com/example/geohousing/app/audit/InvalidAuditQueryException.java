package com.example.geohousing.app.audit;

import java.util.Objects;

/**
 * A timeline request that cannot be answered as asked.
 *
 * <p>Carries which parameter is at fault so the caller can point at the field rather than reprint
 * the whole query. An {@link IllegalArgumentException} because that is what it is — the subtype
 * exists to add the field, not to change the meaning.
 */
public class InvalidAuditQueryException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String field;
  private final String code;

  public InvalidAuditQueryException(String field, String code, String detail) {
    super(detail);
    this.field = Objects.requireNonNull(field, "field");
    this.code = Objects.requireNonNull(code, "code");
  }

  public String field() {
    return field;
  }

  public String code() {
    return code;
  }
}
