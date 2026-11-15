import type { components } from "./generated/schema";
import { hasNumber, hasString, isRecord, type RuntimeGuard } from "./guards";

type GeneratedReview = components["schemas"]["ReviewResponse"];
type GeneratedReviewList = components["schemas"]["ReviewListResponse"];
type GeneratedContent = NonNullable<GeneratedReview["content"]>;
type GeneratedRating = components["schemas"]["RatingView"];

/**
 * A rating with the fields the card reads made required. `value` and `note` stay nullable; the
 * nullable fields the guard proves present are no longer optional so the card does not check twice.
 */
export type ReviewRating = Omit<
  GeneratedRating,
  "category" | "value" | "notApplicable" | "note"
> &
  Required<Pick<GeneratedRating, "category" | "value" | "notApplicable" | "note">>;

/** The review content the card renders, with its read fields present. */
export type ReviewContent = Omit<
  GeneratedContent,
  "body" | "pros" | "cons" | "recommendation" | "ratings" | "locale"
> & {
  body: string;
  pros: string | null;
  cons: string | null;
  recommendation: string;
  ratings: ReviewRating[];
  locale: string;
};

/**
 * A conforming review.
 *
 * `authorAccountId` is not part of the generated public schema and is never mentioned here: even if
 * a response carried one, the guard ignores it and the card has no field to read it from.
 */
export type Review = Omit<
  GeneratedReview,
  | "reviewId"
  | "relationshipType"
  | "residenceFrom"
  | "residenceTo"
  | "verificationTier"
  | "content"
  | "publishedAt"
  | "helpfulCount"
> & {
  reviewId: string;
  relationshipType: string;
  residenceFrom: string | null;
  residenceTo: string | null;
  verificationTier: string;
  content: ReviewContent | null;
  publishedAt: string | null;
  helpfulCount: number;
};

/** One page with the fields the feed depends on proved present. */
export type ReviewPage = Omit<GeneratedReviewList, "items" | "nextCursor"> & {
  items: Review[];
  nextCursor: string | null;
};

function hasNullableString(record: Record<string, unknown>, key: string): boolean {
  return (
    Object.prototype.hasOwnProperty.call(record, key) &&
    (record[key] === null || typeof record[key] === "string")
  );
}

function isRating(value: unknown): value is ReviewRating {
  if (
    !isRecord(value) ||
    !hasString(value, "category") ||
    typeof value.notApplicable !== "boolean" ||
    !Object.prototype.hasOwnProperty.call(value, "value") ||
    !Object.prototype.hasOwnProperty.call(value, "note")
  ) {
    return false;
  }
  return (
    (value.value === null || typeof value.value === "number") &&
    (value.note === null || typeof value.note === "string")
  );
}

function isContent(value: unknown): value is ReviewContent {
  if (
    !isRecord(value) ||
    !hasString(value, "body") ||
    !hasString(value, "recommendation") ||
    !hasString(value, "locale") ||
    !Array.isArray(value.ratings)
  ) {
    return false;
  }
  return (
    hasNullableString(value, "pros") &&
    hasNullableString(value, "cons") &&
    value.ratings.every(isRating)
  );
}

function isReview(value: unknown): value is Review {
  if (
    !isRecord(value) ||
    !hasString(value, "reviewId") ||
    !hasString(value, "relationshipType") ||
    !hasString(value, "verificationTier") ||
    !hasNumber(value, "helpfulCount") ||
    !Object.prototype.hasOwnProperty.call(value, "content") ||
    !Object.prototype.hasOwnProperty.call(value, "publishedAt")
  ) {
    return false;
  }
  return (
    hasNullableString(value, "residenceFrom") &&
    hasNullableString(value, "residenceTo") &&
    (value.publishedAt === null || typeof value.publishedAt === "string") &&
    (value.content === null || isContent(value.content))
  );
}

/**
 * Checks one public page.
 *
 * A missing `nextCursor` key is malformed rather than "last page": the contract represents the last
 * page as `null`, and accepting an absent key would hide a serialization regression while paging.
 */
export const isReviewPage: RuntimeGuard<ReviewPage> = (value): value is ReviewPage => {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    return false;
  }
  return (
    Object.prototype.hasOwnProperty.call(value, "nextCursor") &&
    (value.nextCursor === null || typeof value.nextCursor === "string") &&
    value.items.every(isReview)
  );
};
