import { useLocalSearchParams, useRouter } from "expo-router";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import type { ApiFailureOutcome } from "../api/client";
import { ResultRow } from "../components/ResultRow";
import { useMessages } from "../i18n/LocaleProvider";
import { SEARCH_LIMIT, useSearch } from "../search/useSearch";
import { MIN_TOUCH_TARGET, theme } from "../theme";

function routeQuery(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value)?.trim() ?? "";
}

function errorMessageKey(outcome: ApiFailureOutcome) {
  return outcome === "offline" ? "results.offlineError" : "results.temporaryError";
}

function SkeletonRow() {
  return (
    <View accessible={false} style={styles.skeletonRow}>
      <View style={styles.skeletonTitle} />
      <View style={styles.skeletonLine} />
    </View>
  );
}

export default function ResultsScreen() {
  const params = useLocalSearchParams<{ q?: string | string[] }>();
  const router = useRouter();
  const { t } = useMessages();
  const query = routeQuery(params.q);
  const { state, retry } = useSearch(query);

  function renderState() {
    if (state.status === "idle") {
      return <Text style={styles.stateText}>{t("results.missingQuery")}</Text>;
    }
    if (state.status === "loading") {
      return (
        <View
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={t("results.searching")}
          style={styles.stateBlock}
        >
          <Text accessibilityLiveRegion="polite" style={styles.stateText}>
            {t("results.searching")}
          </Text>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </View>
      );
    }
    if (state.status === "empty") {
      return (
        <View style={styles.stateBlock}>
          <Text accessibilityRole="header" style={styles.stateTitle}>
            {t("results.emptyTitle", { query: state.query })}
          </Text>
          <Text style={styles.stateText}>{t("results.emptyHint")}</Text>
        </View>
      );
    }
    if (state.status === "error") {
      return (
        <View style={styles.stateBlock}>
          <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.stateText}>
            {t(errorMessageKey(state.outcome))}
          </Text>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={t("results.retry")}
            onPress={retry}
            style={styles.retryButton}
            testID="results-retry"
          >
            <Text style={styles.retryText}>{t("results.retry")}</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <View style={styles.results}>
        {state.items.length === SEARCH_LIMIT ? (
          <Text style={styles.pageNotice}>
            {t("results.firstPageNotice", { limit: SEARCH_LIMIT })}
          </Text>
        ) : null}
        {state.items.map((hit) => (
          <ResultRow
            key={hit.propertyId}
            hit={hit}
            onOpen={(propertyId) => router.push(`/property/${encodeURIComponent(propertyId)}`)}
          />
        ))}
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {query ? (
          <Text accessibilityRole="header" style={styles.title}>
            {t("results.heading", { query })}
          </Text>
        ) : null}
        {renderState()}
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
  title: {
    color: theme.text,
    fontSize: 24,
    fontWeight: "700",
    lineHeight: 32,
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
  retryButton: {
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
  retryText: {
    color: theme.accent,
    fontSize: 16,
    fontWeight: "600",
  },
  results: {
    gap: 12,
  },
  pageNotice: {
    color: theme.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  skeletonRow: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 10,
    padding: 16,
  },
  skeletonTitle: {
    backgroundColor: theme.border,
    borderRadius: 6,
    height: 18,
    width: "55%",
  },
  skeletonLine: {
    backgroundColor: theme.border,
    borderRadius: 6,
    height: 14,
    width: "80%",
  },
});
