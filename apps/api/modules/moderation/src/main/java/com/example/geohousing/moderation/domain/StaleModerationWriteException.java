package com.example.geohousing.moderation.domain;

/**
 * A moderator acted on a case or appeal that has moved on since they read it.
 *
 * <p>Two moderators opening the same case both see it awaiting a decision. Without this, the second
 * one's decision is applied on top of the first's — a second decision row on a case that already
 * had one, or an appeal recorded as upheld after another moderator restored the content. Both are
 * silent: each moderator sees success, and the case history contains an outcome nobody chose.
 *
 * <p>Refused rather than merged, because a moderation decision is a judgement about a specific
 * state of the content. Once that state has changed, the judgement has to be made again.
 */
public class StaleModerationWriteException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StaleModerationWriteException(String message) {
    super(message);
  }
}
