import { useRouter } from "expo-router";
import { useState } from "react";
import {
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useMessages } from "../i18n/LocaleProvider";
import { SUPPORTED_LOCALES, type Locale } from "../i18n/locale";
import { MIN_TOUCH_TARGET, theme } from "../theme";

function localeLabelKey(locale: Locale) {
  return locale === "ru" ? ("home.languageRussian" as const) : ("home.languageEnglish" as const);
}

/**
 * The anonymous search entry.
 *
 * Nothing fires on launch: the first network request belongs to the person who submits a query.
 * The locale switch stays here because the manual override has no account or settings screen yet.
 */
export default function HomeScreen() {
  const router = useRouter();
  const { locale, setLocale, t } = useMessages();
  const [query, setQuery] = useState("");
  const trimmedQuery = query.trim();
  const canSubmit = trimmedQuery.length > 0;

  function submit() {
    if (!canSubmit) {
      return;
    }
    router.push(`/search?q=${encodeURIComponent(trimmedQuery)}`);
  }

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text accessibilityRole="header" style={styles.title}>
          {t("app.name")}
        </Text>
        <Text style={styles.tagline}>{t("home.tagline")}</Text>

        <View style={styles.searchCard}>
          <TextInput
            accessibilityLabel={t("home.searchLabel")}
            autoCorrect={false}
            onChangeText={setQuery}
            onSubmitEditing={submit}
            placeholder={t("home.searchPlaceholder")}
            placeholderTextColor={theme.mutedText}
            returnKeyType="search"
            style={styles.input}
            testID="home-search-input"
            value={query}
          />
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={t("home.searchAction")}
            accessibilityState={{ disabled: !canSubmit }}
            disabled={!canSubmit}
            onPress={submit}
            style={[styles.searchButton, !canSubmit && styles.searchButtonDisabled]}
            testID="home-search-submit"
          >
            <Text style={[styles.searchButtonText, !canSubmit && styles.searchButtonTextDisabled]}>
              {t("home.searchAction")}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.languageBlock}>
          <Text accessibilityRole="header" style={styles.sectionHeading}>
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
                  <Text style={[styles.localeButtonText, selected && styles.localeButtonTextSelected]}>
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
    gap: 18,
    padding: 20,
  },
  title: {
    color: theme.text,
    fontSize: 28,
    fontWeight: "700",
    lineHeight: 36,
  },
  tagline: {
    color: theme.mutedText,
    fontSize: 17,
    lineHeight: 24,
  },
  searchCard: {
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    gap: 12,
    padding: 16,
  },
  input: {
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
    paddingHorizontal: 18,
  },
  searchButtonDisabled: {
    backgroundColor: theme.border,
  },
  searchButtonText: {
    color: theme.surface,
    fontSize: 16,
    fontWeight: "600",
  },
  searchButtonTextDisabled: {
    color: theme.mutedText,
  },
  languageBlock: {
    gap: 10,
  },
  sectionHeading: {
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
    backgroundColor: theme.surface,
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
