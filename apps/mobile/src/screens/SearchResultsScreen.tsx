import { useEffect, useState } from "react";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useApiClient } from "../api/ApiProvider";
import type { ApiFailureOutcome } from "../api/client";
import { searchProperties, SEARCH_LIMIT } from "../api/search";
import { useMessages } from "../i18n/LocaleProvider";
import {
  propertyTypeKey,
  toSearchRows,
  type PropertySearchRow,
} from "../properties/searchRows";
import { MIN_TOUCH_TARGET, theme } from "../theme";

/**
 * Ranked matches for one query, rendered from the route's `q`.
 *
 * Every networked screen implements loading, empty, error and loaded; a screen that is only
 * "spinner or content" is incomplete (plan 023, "UI states"). Search serves one page with no
 * cursor, so when exactly the limit comes back the screen says so rather than implying the list is
 * complete.
 */

export interface SearchResultsScreenProps {
  query: string;
}

type LoadState =
  | { query: string; attempt: number; status: "loading" }
  | { query: string; attempt: number; status: "loaded"; rows: PropertySearchRow[] }
  | { query: string; attempt: number; status: "failed"; outcome: ApiFailureOutcome };

export default function SearchResultsScreen({ query }: SearchResultsScreenProps) {
  const client = useApiClient();
  const { t } = useMessages();
  const [loaded, setLoaded] = useState<LoadState | null>(null);
  const [attempt, setAttempt] = useState(0);
  const trimmed = query.trim();

  useEffect(() => {
    if (trimmed === "") {
      // A search needs text or a point and the app has no location UI, so a blank route parameter
      // is the empty state, not a request that would come back 400.
      return;
    }
    let active = true;
    searchProperties(client, trimmed).then((result) => {
      if (!active) {
        return;
      }
      setLoaded(
        result.outcome === "ok"
          ? { query: trimmed, attempt, status: "loaded", rows: toSearchRows(result.data) }
          : { query: trimmed, attempt, status: "failed", outcome: result.outcome },
      );
    });
    return () => {
      active = false;
    };
  }, [client, trimmed, attempt]);

  // The state on screen belongs to the request that produced it; a different query, or a retry, is
  // loading until its own answer arrives. Deriving it avoids a second render just to say "loading".
  const state: LoadState =
    trimmed === ""
      ? { query: "", attempt, status: "loaded", rows: [] }
      : loaded && loaded.query === trimmed && loaded.attempt === attempt
        ? loaded
        : { query: trimmed, attempt, status: "loading" };

  return (
    // The route shows the native stack header, which already pads the top inset.
    <SafeAreaView edges={["left", "right", "bottom"]} style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        {trimmed === "" ? null : (
          <Text accessibilityRole="header" style={styles.title}>
            {t("search.heading", { query: trimmed })}
          </Text>
        )}

        {state.status === "loading" ? (
          <View
            accessibilityRole="progressbar"
            accessibilityLabel={t("search.loading")}
            style={styles.stateBlock}
            testID="search-loading"
          >
            <View style={styles.skeleton} />
            <View style={styles.skeleton} />
            <View style={styles.skeleton} />
          </View>
        ) : null}

        {state.status === "failed" ? (
          <View
            accessibilityLiveRegion="polite"
            style={styles.stateBlock}
            testID="search-error"
          >
            <Text style={styles.stateText}>
              {state.outcome === "offline"
                ? t("search.errorOffline")
                : t("search.errorTemporary")}
            </Text>
            <TouchableOpacity
              accessibilityRole="button"
              accessibilityLabel={t("search.retry")}
              onPress={() => setAttempt((value) => value + 1)}
              style={styles.retryButton}
              testID="search-retry"
            >
              <Text style={styles.retryButtonText}>{t("search.retry")}</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        {state.status === "loaded" && state.rows.length === 0 ? (
          <View accessibilityLiveRegion="polite" style={styles.stateBlock} testID="search-empty">
            {trimmed === "" ? null : (
              <Text accessibilityRole="header" style={styles.stateTitle}>
                {t("search.emptyTitle", { query: trimmed })}
              </Text>
            )}
            <Text style={styles.stateText}>{t("search.emptyHint")}</Text>
          </View>
        ) : null}

        {state.status === "loaded" && state.rows.length > 0 ? (
          <View style={styles.rows}>
            {state.rows.map((row) => {
              const addressLabel = row.address ?? t("search.noAddress");
              const typeKey = propertyTypeKey(row.type);
              const typeLabel = typeKey ? t(typeKey) : t("search.unknownType");
              // Rows are not pressable in 023-B: the property detail route is 023-C scope, and a
              // tap would land on an unmatched route. 023-C makes the row open details and adds
              // the "opens the property" accessibility hint.
              return (
                <View
                  key={row.propertyId}
                  accessible
                  accessibilityLabel={t("search.rowLabel", {
                    name: row.name,
                    address: addressLabel,
                    type: typeLabel,
                  })}
                  style={styles.row}
                  testID={`result-${row.propertyId}`}
                >
                  <Text style={styles.rowName}>{row.name}</Text>
                  <Text style={styles.rowMeta}>{addressLabel}</Text>
                  <Text style={styles.rowMeta}>{typeLabel}</Text>
                </View>
              );
            })}
            {state.rows.length === SEARCH_LIMIT ? (
              <Text style={styles.limitNote}>
                {t("search.showingFirst", { count: state.rows.length })}
              </Text>
            ) : null}
          </View>
        ) : null}
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
    gap: 16,
    padding: 20,
  },
  title: {
    color: theme.text,
    fontSize: 22,
    fontWeight: "700",
  },
  stateBlock: {
    gap: 12,
  },
  stateTitle: {
    color: theme.text,
    fontSize: 18,
    fontWeight: "600",
  },
  stateText: {
    color: theme.mutedText,
    fontSize: 16,
    lineHeight: 23,
  },
  skeleton: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    height: 76,
  },
  retryButton: {
    alignItems: "center",
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: 1,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 16,
  },
  retryButtonText: {
    color: theme.accent,
    fontSize: 16,
    fontWeight: "600",
  },
  rows: {
    gap: 12,
  },
  row: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 4,
    minHeight: MIN_TOUCH_TARGET,
    padding: 16,
  },
  rowName: {
    color: theme.text,
    fontSize: 18,
    fontWeight: "600",
  },
  rowMeta: {
    color: theme.mutedText,
    fontSize: 15,
    lineHeight: 21,
  },
  limitNote: {
    color: theme.mutedText,
    fontSize: 14,
  },
});
