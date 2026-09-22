import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { intlSupport } from "../i18n/intl";
import { useMessages } from "../i18n/LocaleProvider";
import { SUPPORTED_LOCALES, type Locale } from "../i18n/locale";
import { MIN_TOUCH_TARGET, theme } from "../theme";

/**
 * The 023-A smoke screen.
 *
 * It exists to prove that routing, localization and styling boot on a device, and to give a human
 * the evidence plan 023 asks for: the active locale, the Russian 1/2/5 plural forms, a formatted
 * date, and whether the runtime has real `Intl` or the local fallback. The search entry replaces it
 * in 023-B, at which point this screen and its diagnostics go away.
 *
 * The counts below are fixed localization samples, not review counts read from the API: no screen
 * in this slice may show a count it did not itself count (plan 023, "DRAFT and data sufficiency").
 */

const SAMPLE_DATE = new Date(2026, 8, 1);
const PLURAL_SAMPLES = [1, 2, 5];
// The three rendered messages are listed side by side so a human can compare them; this is not a
// sentence assembled from fragments.
const PLURAL_SEPARATOR = " · ";

function localeLabelKey(locale: Locale) {
  return locale === "ru" ? ("home.languageRussian" as const) : ("home.languageEnglish" as const);
}

export default function HomeScreen() {
  const { locale, setLocale, t, count, date } = useMessages();
  const pluralSamples = PLURAL_SAMPLES.map((value) => count("reviews.count", value)).join(
    PLURAL_SEPARATOR,
  );
  const intl = intlSupport();

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text accessibilityRole="header" style={styles.title}>
          {t("app.name")}
        </Text>
        <Text style={styles.tagline}>{t("home.tagline")}</Text>

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
                  <Text style={[styles.localeButtonText, selected && styles.localeButtonTextSelected]}>
                    {label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        <View style={styles.card}>
          <Text accessibilityRole="header" style={styles.cardHeading}>
            {t("home.diagnosticsHeading")}
          </Text>
          <Text style={styles.line}>{t("home.diagnosticsLocaleLine", { locale })}</Text>
          <Text style={styles.line}>{t("home.diagnosticsPluralLine", { forms: pluralSamples })}</Text>
          <Text style={styles.line}>
            {t("home.diagnosticsDateLine", { date: date(SAMPLE_DATE) })}
          </Text>
          <Text style={styles.line}>
            {t("home.diagnosticsIntlLine", {
              support:
                intl === "native"
                  ? t("home.diagnosticsIntlNative")
                  : t("home.diagnosticsIntlFallback"),
            })}
          </Text>
        </View>

        <Text style={styles.notice}>{t("home.diagnosticsNote")}</Text>
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
  line: {
    color: theme.text,
    fontSize: 15,
    lineHeight: 22,
  },
  notice: {
    color: theme.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
});
