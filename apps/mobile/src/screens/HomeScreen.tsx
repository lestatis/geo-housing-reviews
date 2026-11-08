import { useState } from "react";
import { ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useMessages } from "../i18n/LocaleProvider";
import { SUPPORTED_LOCALES, type Locale } from "../i18n/locale";
import { MIN_TOUCH_TARGET, theme } from "../theme";

/**
 * The search entry: what the app is for, one field, and the language switch.
 *
 * It makes no request on launch — the first network call is the user's — and it is not a gate: no
 * account is required, offered or implied anywhere on this screen (plan 023, "User journey").
 */

export interface HomeScreenProps {
  /** Opens the results route for a non-blank query. */
  onSearch(query: string): void;
}

function localeLabelKey(locale: Locale) {
  return locale === "ru" ? ("home.languageRussian" as const) : ("home.languageEnglish" as const);
}

export default function HomeScreen({ onSearch }: HomeScreenProps) {
  const { locale, setLocale, t } = useMessages();
  const [query, setQuery] = useState("");
  const trimmed = query.trim();
  const canSearch = trimmed.length > 0;

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text accessibilityRole="header" style={styles.title}>
          {t("app.name")}
        </Text>
        <Text style={styles.tagline}>{t("home.tagline")}</Text>

        <View style={styles.searchBlock}>
          <Text style={styles.label} nativeID="home-search-label">
            {t("home.searchLabel")}
          </Text>
          <TextInput
            accessibilityLabel={t("home.searchLabel")}
            onChangeText={setQuery}
            onSubmitEditing={() => {
              if (canSearch) {
                onSearch(trimmed);
              }
            }}
            placeholder={t("home.searchPlaceholder")}
            returnKeyType="search"
            style={styles.input}
            testID="home-search-input"
            value={query}
          />
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={t("home.searchButton")}
            accessibilityState={{ disabled: !canSearch }}
            disabled={!canSearch}
            onPress={() => onSearch(trimmed)}
            style={[styles.searchButton, !canSearch && styles.searchButtonDisabled]}
            testID="home-search-submit"
          >
            <Text style={styles.searchButtonText}>{t("home.searchButton")}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.card}>
          <Text accessibilityRole="header" style={styles.cardHeading}>
            {t("home.languageHeading")}
          </Text>
          <View style={styles.localeRow}>
            {SUPPORTED_LOCALES.map((option) => {
              const label = t(localeLabelKey(option));
              const selected = option === locale;
              return (
                <TouchableOpacity
                  key={option}
                  accessibilityRole="button"
                  accessibilityLabel={t("home.languageSwitchLabel", { language: label })}
                  accessibilityState={{ selected }}
                  onPress={() => setLocale(option)}
                  style={[styles.localeButton, selected && styles.localeButtonSelected]}
                  testID={`locale-${option}`}
                >
                  <Text
                    style={[styles.localeButtonText, selected && styles.localeButtonTextSelected]}
                  >
                    {label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
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
    fontSize: 26,
    fontWeight: "700",
  },
  tagline: {
    color: theme.mutedText,
    fontSize: 17,
    lineHeight: 24,
  },
  searchBlock: {
    gap: 8,
  },
  label: {
    color: theme.text,
    fontSize: 15,
    fontWeight: "600",
  },
  input: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 10,
    borderWidth: 1,
    color: theme.text,
    fontSize: 17,
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  searchButton: {
    alignItems: "center",
    backgroundColor: theme.accent,
    borderRadius: 10,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 16,
  },
  searchButtonDisabled: {
    opacity: 0.5,
  },
  searchButtonText: {
    color: theme.surface,
    fontSize: 17,
    fontWeight: "600",
  },
  card: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 8,
    padding: 16,
  },
  cardHeading: {
    color: theme.text,
    fontSize: 15,
    fontWeight: "600",
    textTransform: "uppercase",
  },
  localeRow: {
    flexDirection: "row",
    gap: 12,
  },
  localeButton: {
    alignItems: "center",
    borderColor: theme.border,
    borderRadius: 10,
    borderWidth: 1,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    minWidth: 120,
    paddingHorizontal: 16,
  },
  localeButtonSelected: {
    backgroundColor: theme.selectedSurface,
    borderColor: theme.accent,
  },
  localeButtonText: {
    color: theme.text,
    fontSize: 16,
  },
  localeButtonTextSelected: {
    color: theme.accent,
    fontWeight: "600",
  },
});
