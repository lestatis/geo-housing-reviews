import { render, screen } from "@testing-library/react-native";
import type { Review } from "../../api/reviewGuards";
import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import type { Locale } from "../../i18n/locale";
import { ReviewCard } from "../ReviewCard";

const NOW = new Date(2026, 8, 15, 12).getTime();

function makeReview(overrides: Partial<Review> = {}): Review {
  return {
    reviewId: "review-1",
    propertyId: "property-1",
    relationshipType: "CURRENT_RESIDENT",
    residenceFrom: "2023-05-12",
    residenceTo: "2024-09-30",
    status: "PUBLISHED",
    verificationTier: "RELATIONSHIP_SIGNAL",
    content: {
      versionNumber: 1,
      locale: "en",
      body: "A calm place to live with a short walk to the sea.",
      pros: "Near the sea",
      cons: "Thin walls",
      recommendation: "RECOMMEND",
      ratings: [
        {
          category: "NOISE",
          value: 4,
          notApplicable: false,
          note: "Quiet at night",
          categorySetVersion: 1,
        },
      ],
      createdAt: "2024-09-30T10:00:00Z",
    },
    publishedAt: new Date(2026, 8, 12, 10).toISOString(),
    createdAt: "2024-09-30T09:00:00Z",
    updatedAt: "2024-09-30T10:00:00Z",
    version: 2,
    helpfulCount: 5,
    ...overrides,
  };
}

function renderCard(review: Review, locale: Locale = "en"): void {
  render(
    <LocaleProvider store={createMemoryLocaleStore(locale)} deviceLocale={`${locale}-GE`}>
      <ReviewCard review={review} now={NOW} />
    </LocaleProvider>,
  );
}

describe("ReviewCard", () => {
  it("renders residence years only and the relationship", () => {
    renderCard(makeReview());

    expect(screen.getByText("2023–2024")).toBeTruthy();
    expect(screen.getByText("Current resident")).toBeTruthy();
    expect(screen.queryByText(/May|September/)).toBeNull();
  });

  it("never renders an internal account identifier even when the body carries one", () => {
    renderCard({ ...makeReview(), authorAccountId: "account-internal-123" } as Review);

    expect(JSON.stringify(screen.toJSON())).not.toContain("account-internal-123");
  });

  it("renders no total review count or aggregate rating", () => {
    renderCard(makeReview());

    const tree = JSON.stringify(screen.toJSON());
    expect(tree).not.toContain("reviewCount");
    expect(tree).not.toContain("averageRating");
    expect(tree).not.toContain("4.5");
  });

  it.each([
    ["RELATIONSHIP_SIGNAL", "Relationship signal confirmed"],
    ["DOCUMENT_VERIFIED", "Relationship checked: document"],
  ])("words the %s badge as a checked relationship", (tier, expected) => {
    renderCard(makeReview({ verificationTier: tier }));

    expect(screen.getByText(expected)).toBeTruthy();
    expect(screen.getByLabelText(/does not certify every statement/)).toBeTruthy();
  });

  it.each([
    ["UNVERIFIED", "Relationship not verified"],
    ["FUTURE_TIER", "Relationship verification unavailable"],
  ])("does not claim a check happened on the %s badge", (tier, expected) => {
    renderCard(makeReview({ verificationTier: tier }));

    expect(screen.getByText(expected)).toBeTruthy();
    expect(screen.getByLabelText(expected)).toBeTruthy();
    expect(screen.queryByLabelText(/checked evidence/)).toBeNull();
    expect(screen.queryByLabelText(/does not certify every statement/)).toBeNull();
  });

  it("shows a language tag only when the content language differs", () => {
    renderCard(makeReview({ content: { ...makeReview().content!, locale: "ru" } }));
    expect(screen.getByText("ru")).toBeTruthy();
    expect(screen.getByLabelText("Review language: ru")).toBeTruthy();
  });

  it("treats a regional variant of the UI language as the same language", () => {
    renderCard(makeReview({ content: { ...makeReview().content!, locale: "en-US" } }));
    expect(screen.queryByLabelText(/Review language/)).toBeNull();
  });

  it("skips notApplicable ratings, shows notes and never prints an unknown raw category", () => {
    renderCard(
      makeReview({
        content: {
          ...makeReview().content!,
          ratings: [
            {
              category: "NOISE",
              value: 4,
              notApplicable: false,
              note: "Quiet at night",
              categorySetVersion: 1,
            },
            {
              category: "HEATING",
              value: null,
              notApplicable: true,
              note: "No heating data",
              categorySetVersion: 1,
            },
            {
              category: "BALCONY",
              value: null,
              notApplicable: false,
              note: null,
              categorySetVersion: 1,
            },
          ],
        },
      }),
    );

    expect(screen.getByText("Noise: 4/5")).toBeTruthy();
    expect(screen.getByText("Quiet at night")).toBeTruthy();
    expect(screen.queryByText(/Heating/)).toBeNull();
    expect(screen.queryByText("No heating data")).toBeNull();
    expect(screen.getByText("Other aspect")).toBeTruthy();
    expect(JSON.stringify(screen.toJSON())).not.toContain("BALCONY");
  });

  it("shows the recommendation as an icon plus text", () => {
    renderCard(makeReview());

    expect(screen.getByText("Recommends")).toBeTruthy();
    expect(screen.getAllByText("✓", { includeHiddenElements: true }).length).toBeGreaterThanOrEqual(
      2,
    );
  });

  it("shows the helpful count and a relative publication date", () => {
    renderCard(makeReview());

    expect(screen.getByText("5 people found this helpful")).toBeTruthy();
    expect(screen.getByText("3 days ago")).toBeTruthy();
  });

  it("uses Russian plural forms for helpful and age", () => {
    renderCard(makeReview({ helpfulCount: 2 }), "ru");

    expect(screen.getByText("2 человека считают отзыв полезным")).toBeTruthy();
    expect(screen.getByText("3 дня назад")).toBeTruthy();
  });

  it("still renders the public relationship facts when content is absent", () => {
    renderCard(makeReview({ content: null }));

    expect(screen.getByText("Current resident")).toBeTruthy();
    expect(screen.queryByText("Pros")).toBeNull();
    expect(screen.queryByText("Recommends")).toBeNull();
  });

  it("renders a single residence year when only one end is known", () => {
    renderCard(makeReview({ residenceFrom: "2023-05-12", residenceTo: null }));
    expect(screen.getByText("Since 2023")).toBeTruthy();
  });
});
