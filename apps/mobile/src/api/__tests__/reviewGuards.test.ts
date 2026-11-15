import { isReviewPage } from "../reviewGuards";

const validRating = {
  category: "NOISE",
  value: 4,
  notApplicable: false,
  note: "Quiet at night",
  categorySetVersion: 1,
};

const validContent = {
  versionNumber: 1,
  locale: "en",
  body: "A calm place to live.",
  pros: "Near the sea",
  cons: null,
  recommendation: "RECOMMEND",
  ratings: [validRating],
  createdAt: "2024-09-30T10:00:00Z",
};

const validReview = {
  reviewId: "review-1",
  propertyId: "property-1",
  relationshipType: "CURRENT_RESIDENT",
  residenceFrom: "2023-05-12",
  residenceTo: null,
  status: "PUBLISHED",
  verificationTier: "RELATIONSHIP_SIGNAL",
  content: validContent,
  publishedAt: "2024-09-30T10:00:00Z",
  createdAt: "2024-09-30T09:00:00Z",
  updatedAt: "2024-09-30T10:00:00Z",
  version: 2,
  helpfulCount: 5,
};

describe("isReviewPage", () => {
  it("accepts a page and ignores an authorAccountId of any shape", () => {
    // The guard never reads the removed public field, so even a nonsensical extra value is not a
    // reason to declare the body malformed.
    expect(isReviewPage({ items: [{ ...validReview, authorAccountId: 12345 }], nextCursor: "c1" })).toBe(
      true,
    );
  });

  it("accepts a last page with a null cursor and a review with no content", () => {
    expect(isReviewPage({ items: [{ ...validReview, content: null }], nextCursor: null })).toBe(true);
  });

  it.each([
    ["a missing nextCursor", { items: [validReview] }],
    ["items not an array", { items: "review", nextCursor: null }],
    ["a review missing reviewId", { items: [{ ...validReview, reviewId: undefined }], nextCursor: null }],
    [
      "a missing residenceFrom key",
      { items: [{ ...validReview, residenceFrom: undefined }], nextCursor: null },
    ],
    [
      "a missing publishedAt key",
      { items: [{ ...validReview, publishedAt: undefined }], nextCursor: null },
    ],
    [
      "a rating without notApplicable",
      {
        items: [
          {
            ...validReview,
            content: { ...validContent, ratings: [{ category: "NOISE", value: 4, note: null }] },
          },
        ],
        nextCursor: null,
      },
    ],
    [
      "a content body that is not a string",
      { items: [{ ...validReview, content: { ...validContent, body: 4 } }], nextCursor: null },
    ],
    ["not an object", null],
  ])("rejects a page with %s", (_label, value) => {
    expect(isReviewPage(value)).toBe(false);
  });
});
