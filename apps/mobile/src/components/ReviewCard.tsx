import { StyleSheet, Text, View } from "react-native";

import type { Review, ReviewRating } from "../api/reviewGuards";
import type { MessageKey } from "../i18n/en";
import { formatRelativeDate } from "../i18n/format";
import { useMessages } from "../i18n/LocaleProvider";
import { matchLocaleTag } from "../i18n/locale";
import { theme } from "../theme";

const RELATIONSHIP_KEYS: Record<string, MessageKey> = {
  CURRENT_RESIDENT: "reviews.relationship.currentResident",
  FORMER_RESIDENT: "reviews.relationship.formerResident",
  OWNER: "reviews.relationship.owner",
  OTHER: "reviews.relationship.other",
};

const VERIFICATION_KEYS: Record<string, MessageKey> = {
  UNVERIFIED: "reviews.verification.unverified",
  RELATIONSHIP_SIGNAL: "reviews.verification.relationshipSignal",
  DOCUMENT_VERIFIED: "reviews.verification.documentVerified",
};

const VERIFICATION_ICONS: Record<string, string> = {
  UNVERIFIED: "?",
  RELATIONSHIP_SIGNAL: "✓",
  DOCUMENT_VERIFIED: "✓",
};

/**
 * Tiers where the platform actually checked relationship evidence. Only these may be described as
 * checked: UNVERIFIED and unknown tiers must not claim a check happened (TRUST_VERIFICATION.md).
 */
const CHECKED_VERIFICATION_TIERS = new Set(["RELATIONSHIP_SIGNAL", "DOCUMENT_VERIFIED"]);

const RECOMMENDATION_KEYS: Record<string, MessageKey> = {
  RECOMMEND: "reviews.recommendation.recommend",
  NEUTRAL: "reviews.recommendation.neutral",
  NOT_RECOMMEND: "reviews.recommendation.notRecommend",
};

const RECOMMENDATION_ICONS: Record<string, string> = {
  RECOMMEND: "✓",
  NEUTRAL: "–",
  NOT_RECOMMEND: "✕",
};

/** P-019's canonical category ids; anything else is a generic label, never the raw string. */
const CATEGORY_KEYS: Record<string, MessageKey> = {
  NOISE: "reviews.rating.noise",
  UTILITIES: "reviews.rating.utilities",
  HEATING: "reviews.rating.heating",
  LIFTS_COMMON_AREAS: "reviews.rating.liftsCommonAreas",
  CLEANLINESS: "reviews.rating.cleanliness",
  MANAGEMENT: "reviews.rating.management",
  SAFETY: "reviews.rating.safety",
  NEIGHBOURHOOD: "reviews.rating.neighbourhood",
  CONSTRUCTION: "reviews.rating.construction",
  VALUE: "reviews.rating.value",
};

function yearOf(date: string | null): string | null {
  const match = date ? /^(\d{4})/.exec(date) : null;
  return match?.[1] ?? null;
}

export interface ReviewCardProps {
  review: Review;
  /** Injectable clock for deterministic relative-date tests; production uses the real time. */
  now?: number;
}

/**
 * One published review.
 *
 * Only the public data mapping is rendered. There is no author identifier, no total review count
 * and no aggregate rating here: the helpful count is the one public aggregate, and residence dates
 * are reduced to years because a month plus a building narrows towards a person.
 */
export function ReviewCard({ review, now }: ReviewCardProps) {
  const { t, count, locale } = useMessages();
  const content = review.content;

  const relationship = t(
    RELATIONSHIP_KEYS[review.relationshipType] ?? "reviews.relationship.unknown",
  );
  const residence = residenceText(review, t);
  const verificationKey =
    VERIFICATION_KEYS[review.verificationTier] ?? "reviews.verification.unknown";
  const verification = t(verificationKey);
  const verificationIcon = VERIFICATION_ICONS[review.verificationTier] ?? "?";
  const verificationLabel = CHECKED_VERIFICATION_TIERS.has(review.verificationTier)
    ? `${verification}. ${t("reviews.verification.tooltip")}`
    : verification;

  const recommendationKey = content
    ? (RECOMMENDATION_KEYS[content.recommendation] ?? "reviews.recommendation.unknown")
    : null;
  const recommendation = recommendationKey ? t(recommendationKey) : null;
  const recommendationIcon =
    content && recommendationKey
      ? (RECOMMENDATION_ICONS[content.recommendation] ?? "?")
      : null;

  const ratings = content?.ratings.filter((rating) => !rating.notApplicable) ?? [];
  const pros = content?.pros?.trim() ?? "";
  const cons = content?.cons?.trim() ?? "";
  const body = content?.body.trim() ?? "";
  const contentLocale = content?.locale.trim() ?? "";
  const showLanguage =
    contentLocale.length > 0 && matchLocaleTag(contentLocale) !== locale;
  const publishedAt = review.publishedAt;
  const publishedLabel =
    publishedAt !== null ? formatRelativeDate(locale, publishedAt, now) : "";

  return (
    <View style={styles.card}>
      <Text style={styles.relationship}>{relationship}</Text>
      {residence ? <Text style={styles.residence}>{residence}</Text> : null}

      <View style={styles.badgeRow}>
        <View
          accessible
          accessibilityLabel={verificationLabel}
          style={styles.badge}
        >
          <Text
            accessibilityElementsHidden
            importantForAccessibility="no"
            style={styles.badgeIcon}
          >
            {verificationIcon}
          </Text>
          <Text style={styles.badgeText}>{verification}</Text>
        </View>
        {recommendation && recommendationIcon ? (
          <View accessible accessibilityLabel={recommendation} style={styles.recommendation}>
            <Text
              accessibilityElementsHidden
              importantForAccessibility="no"
              style={styles.recommendationIcon}
            >
              {recommendationIcon}
            </Text>
            <Text style={styles.recommendationText}>{recommendation}</Text>
          </View>
        ) : null}
      </View>

      {ratings.length > 0 ? (
        <View style={styles.chips}>
          {ratings.map((rating, index) => (
            <RatingChip key={`${rating.category}-${index}`} rating={rating} />
          ))}
        </View>
      ) : null}

      {pros ? (
        <View style={styles.textBlock}>
          <Text style={styles.blockLabel}>{t("reviews.pros")}</Text>
          <Text style={styles.blockText}>{pros}</Text>
        </View>
      ) : null}
      {cons ? (
        <View style={styles.textBlock}>
          <Text style={styles.blockLabel}>{t("reviews.cons")}</Text>
          <Text style={styles.blockText}>{cons}</Text>
        </View>
      ) : null}
      {body ? (
        <View style={styles.textBlock}>
          <Text style={styles.blockText}>{body}</Text>
        </View>
      ) : null}

      <View style={styles.meta}>
        {review.helpfulCount > 0 ? (
          <Text style={styles.metaText}>{count("reviews.helpful", review.helpfulCount)}</Text>
        ) : null}
        {publishedLabel ? (
          <Text style={styles.metaText}>{publishedLabel}</Text>
        ) : null}
        {showLanguage ? (
          <Text
            style={styles.metaText}
            accessibilityLabel={t("reviews.languageLabel", { language: contentLocale })}
          >
            {contentLocale}
          </Text>
        ) : null}
      </View>
    </View>
  );
}

function residenceText(
  review: Review,
  t: (key: MessageKey, params?: Record<string, string | number>) => string,
): string | null {
  const from = yearOf(review.residenceFrom);
  const to = yearOf(review.residenceTo);
  if (from && to) {
    return from === to
      ? t("reviews.residenceYear", { year: from })
      : t("reviews.residenceRange", { from, to });
  }
  if (from) {
    return t("reviews.residenceSince", { year: from });
  }
  if (to) {
    return t("reviews.residenceUntil", { year: to });
  }
  return null;
}

function RatingChip({ rating }: { rating: ReviewRating }) {
  const { t } = useMessages();
  const category = t(CATEGORY_KEYS[rating.category] ?? "reviews.rating.otherAspect");
  const label =
    typeof rating.value === "number"
      ? t("reviews.rating.withValue", { category, value: rating.value })
      : t("reviews.rating.withoutValue", { category });
  const note = rating.note?.trim() ?? "";

  return (
    <View style={styles.chip}>
      <Text style={styles.chipValue}>{label}</Text>
      {note ? <Text style={styles.chipNote}>{note}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 12,
    padding: 16,
  },
  relationship: {
    color: theme.text,
    fontSize: 19,
    fontWeight: "700",
    lineHeight: 26,
  },
  residence: {
    color: theme.mutedText,
    fontSize: 15,
    lineHeight: 21,
  },
  badgeRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  badge: {
    alignItems: "center",
    backgroundColor: theme.selectedSurface,
    borderRadius: 999,
    flexDirection: "row",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  badgeIcon: {
    color: theme.accent,
    fontSize: 15,
    fontWeight: "700",
  },
  badgeText: {
    color: theme.accent,
    fontSize: 14,
    lineHeight: 20,
  },
  recommendation: {
    alignItems: "center",
    borderColor: theme.border,
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  recommendationIcon: {
    color: theme.text,
    fontSize: 15,
    fontWeight: "700",
  },
  recommendationText: {
    color: theme.text,
    fontSize: 14,
    lineHeight: 20,
  },
  chips: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chip: {
    backgroundColor: theme.background,
    borderColor: theme.border,
    borderRadius: 10,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 2,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  chipValue: {
    color: theme.text,
    fontSize: 14,
    lineHeight: 20,
  },
  chipNote: {
    color: theme.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  textBlock: {
    gap: 4,
  },
  blockLabel: {
    color: theme.text,
    fontSize: 15,
    fontWeight: "600",
    lineHeight: 21,
  },
  blockText: {
    color: theme.text,
    fontSize: 16,
    lineHeight: 24,
  },
  meta: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  metaText: {
    color: theme.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
});
