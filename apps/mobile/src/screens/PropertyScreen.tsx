import { useLocalSearchParams, useRouter } from "expo-router";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import type { ApiFailureOutcome } from "../api/client";
import type { Property } from "../api/propertyGuards";
import { ReviewCard } from "../components/ReviewCard";
import type { MessageKey } from "../i18n/en";
import { useMessages } from "../i18n/LocaleProvider";
import { matchLocaleTag, type Locale } from "../i18n/locale";
import { useProperty } from "../property/useProperty";
import { REVIEW_PAGE_SIZE, useReviewFeed } from "../reviews/useReviewFeed";
import { formatAddressSummary } from "../search/formatAddressSummary";
import { MIN_TOUCH_TARGET, theme } from "../theme";

const PROPERTY_TYPE_KEYS: Record<string, MessageKey> = {
  BUILDING: "propertyType.building",
  RESIDENTIAL_COMPLEX: "propertyType.residentialComplex",
  BLOCK: "propertyType.block",
  PHASE: "propertyType.phase",
};

function routeId(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value)?.trim() ?? "";
}

function failureMessageKey(outcome: ApiFailureOutcome, offline: MessageKey, temporary: MessageKey) {
  return outcome === "offline" ? offline : temporary;
}

function aliasNames(property: Property, locale: Locale): string[] {
  const seen = new Set<string>([property.canonicalName.trim().toLocaleLowerCase()]);
  const preferred: string[] = [];
  const rest: string[] = [];

  for (const alias of property.aliases) {
    const name = alias.name.trim();
    const key = name.toLocaleLowerCase();
    if (name.length === 0 || seen.has(key)) {
      continue;
    }
    seen.add(key);
    const matchesLocale = matchLocaleTag(alias.locale) === locale;
    (matchesLocale ? preferred : rest).push(name);
  }

  return [...preferred, ...rest];
}

function SkeletonLine({ tall }: { tall?: boolean }) {
  return <View style={tall ? styles.skeletonTall : styles.skeletonLine} />;
}

export default function PropertyScreen() {
  const params = useLocalSearchParams<{ id?: string | string[] }>();
  const router = useRouter();
  const { t, locale } = useMessages();
  const propertyId = routeId(params.id);
  const property = useProperty(propertyId);
  const feed = useReviewFeed(propertyId);

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace("/");
    }
  };

  function renderHeader() {
    if (property.state.status === "loading") {
      return (
        <View
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={t("property.loading")}
          style={styles.headerBlock}
          testID="property-loading"
        >
          <Text accessibilityLiveRegion="polite" style={styles.stateText}>
            {t("property.loading")}
          </Text>
          <SkeletonLine tall />
          <SkeletonLine />
          <SkeletonLine />
        </View>
      );
    }

    if (property.state.status === "notFound") {
      return (
        <View style={styles.stateBlock}>
          <Text accessibilityRole="header" style={styles.stateTitle}>
            {t("property.notFoundTitle")}
          </Text>
        </View>
      );
    }

    if (property.state.status === "error") {
      return (
        <View style={styles.stateBlock}>
          <Text
            accessibilityRole="alert"
            accessibilityLiveRegion="polite"
            style={styles.stateText}
          >
            {t(
              failureMessageKey(
                property.state.outcome,
                "property.offlineError",
                "property.temporaryError",
              ),
            )}
          </Text>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={t("property.retry")}
            onPress={property.retry}
            style={styles.actionButton}
            testID="property-retry"
          >
            <Text style={styles.actionText}>{t("property.retry")}</Text>
          </TouchableOpacity>
        </View>
      );
    }

    const value = property.state.property;
    const aliases = aliasNames(value, locale);
    const address = formatAddressSummary(value.address);
    const type = t(
      (value.type ? PROPERTY_TYPE_KEYS[value.type] : undefined) ?? "propertyType.unknown",
    );

    return (
      <View style={styles.headerBlock}>
        <Text accessibilityRole="header" style={styles.title}>
          {value.canonicalName}
        </Text>
        {aliases.length > 0 ? (
          <Text style={styles.address}>{t("property.aliases", { aliases: aliases.join(", ") })}</Text>
        ) : null}
        {address ? <Text style={styles.address}>{address}</Text> : null}
        <Text style={styles.type}>{type}</Text>
        {value.status === "DRAFT" ? (
          <View style={styles.notice}>
            <Text style={styles.noticeText}>{t("property.draftNotice")}</Text>
          </View>
        ) : null}
      </View>
    );
  }

  function renderFeed() {
    if (feed.state.status === "loading") {
      return (
        <View
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={t("reviews.loading")}
          style={styles.feed}
          testID="reviews-loading"
        >
          <Text accessibilityLiveRegion="polite" style={styles.stateText}>
            {t("reviews.loading")}
          </Text>
          <SkeletonLine tall />
          <SkeletonLine />
        </View>
      );
    }

    if (feed.state.status === "error") {
      return (
        <View style={styles.stateBlock}>
          <Text
            accessibilityRole="alert"
            accessibilityLiveRegion="polite"
            style={styles.stateText}
          >
            {t(
              failureMessageKey(
                feed.state.outcome,
                "reviews.offlineError",
                "reviews.temporaryError",
              ),
            )}
          </Text>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={t("reviews.retry")}
            onPress={feed.retry}
            style={styles.actionButton}
            testID="reviews-retry"
          >
            <Text style={styles.actionText}>{t("reviews.retry")}</Text>
          </TouchableOpacity>
        </View>
      );
    }

    const { reviews, nextCursor, paging, pagingOutcome } = feed.state;
    if (reviews.length === 0 && paging === "idle") {
      return <Text style={styles.stateText}>{t("reviews.empty")}</Text>;
    }

    const showFirstPageNotice = reviews.length === REVIEW_PAGE_SIZE && nextCursor !== null;
    const pagingFailed = paging === "error";
    const loadLabel = pagingFailed ? t("reviews.retry") : t("reviews.loadMore");

    return (
      <View style={styles.feed}>
        {showFirstPageNotice ? (
          <Text style={styles.pageNotice}>
            {t("reviews.firstPageNotice", { limit: REVIEW_PAGE_SIZE })}
          </Text>
        ) : null}

        {reviews.map((review) => (
          <ReviewCard key={review.reviewId} review={review} />
        ))}

        {paging === "loading" ? (
          <View
            accessible
            accessibilityRole="progressbar"
            accessibilityLabel={t("reviews.loadingMore")}
            style={styles.stateBlock}
            testID="reviews-loading-more"
          >
            <Text accessibilityLiveRegion="polite" style={styles.stateText}>
              {t("reviews.loadingMore")}
            </Text>
          </View>
        ) : null}

        {paging === "error" && pagingOutcome !== null ? (
          <Text
            accessibilityRole="alert"
            accessibilityLiveRegion="polite"
            style={styles.stateText}
          >
            {t(
              failureMessageKey(
                pagingOutcome,
                "reviews.offlineError",
                "reviews.temporaryError",
              ),
            )}
          </Text>
        ) : null}

        {nextCursor !== null && paging !== "loading" ? (
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={loadLabel}
            onPress={feed.loadMore}
            style={styles.actionButton}
            testID="reviews-load-more"
          >
            <Text style={styles.actionText}>{loadLabel}</Text>
          </TouchableOpacity>
        ) : null}
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel={t("property.back")}
          onPress={goBack}
          style={styles.backButton}
          testID="property-back"
        >
          <Text style={styles.actionText}>{t("property.back")}</Text>
        </TouchableOpacity>

        {renderHeader()}
        {property.state.status === "loading" || property.state.status === "loaded"
          ? renderFeed()
          : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: theme.background,
    flex: 1,
  },
  content: {
    gap: 20,
    padding: 20,
  },
  backButton: {
    alignItems: "center",
    alignSelf: "flex-start",
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: 1,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 18,
  },
  headerBlock: {
    gap: 8,
  },
  title: {
    color: theme.text,
    fontSize: 26,
    fontWeight: "700",
    lineHeight: 34,
  },
  address: {
    color: theme.mutedText,
    fontSize: 16,
    lineHeight: 24,
  },
  type: {
    color: theme.accent,
    fontSize: 15,
    lineHeight: 21,
  },
  notice: {
    backgroundColor: theme.selectedSurface,
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: StyleSheet.hairlineWidth,
    marginTop: 4,
    padding: 12,
  },
  noticeText: {
    color: theme.text,
    fontSize: 15,
    lineHeight: 22,
  },
  feed: {
    gap: 12,
  },
  stateBlock: {
    gap: 14,
  },
  stateTitle: {
    color: theme.text,
    fontSize: 19,
    fontWeight: "600",
    lineHeight: 26,
  },
  stateText: {
    color: theme.mutedText,
    fontSize: 16,
    lineHeight: 24,
  },
  actionButton: {
    alignItems: "center",
    alignSelf: "flex-start",
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: 1,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    minWidth: 120,
    paddingHorizontal: 18,
  },
  actionText: {
    color: theme.accent,
    fontSize: 16,
    fontWeight: "600",
  },
  pageNotice: {
    color: theme.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  skeletonLine: {
    backgroundColor: theme.border,
    borderRadius: 6,
    height: 16,
    width: "80%",
  },
  skeletonTall: {
    backgroundColor: theme.border,
    borderRadius: 8,
    height: 28,
    width: "65%",
  },
});
