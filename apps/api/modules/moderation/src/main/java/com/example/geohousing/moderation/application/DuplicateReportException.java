package com.example.geohousing.moderation.application;

/** This account already has a live report about this content. */
public class DuplicateReportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicateReportException(String message) {
    super(message);
  }
}
