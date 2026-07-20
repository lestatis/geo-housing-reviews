package com.example.geohousing.properties.domain;

/** Raised when a property is asked to make a lifecycle transition its current status forbids. */
public class IllegalPropertyStateTransitionException extends RuntimeException {

  public IllegalPropertyStateTransitionException(String message) {
    super(message);
  }
}
