import { useRouter } from "expo-router";
import { StyleSheet, Text, TouchableOpacity } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useMessages } from "../i18n/LocaleProvider";
import { MIN_TOUCH_TARGET, theme } from "../theme";

/** Temporary destination for 023-B; 023-C replaces it with the property and review experience. */
export default function PropertyPlaceholderScreen() {
  const router = useRouter();
  const { t } = useMessages();

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace("/");
    }
  };

  return (
    <SafeAreaView style={styles.screen}>
      <Text accessibilityRole="header" style={styles.title}>
        {t("propertyPlaceholder.title")}
      </Text>
      <TouchableOpacity
        accessibilityRole="button"
        accessibilityLabel={t("propertyPlaceholder.back")}
        onPress={goBack}
        style={styles.backButton}
      >
        <Text style={styles.backText}>{t("propertyPlaceholder.back")}</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: theme.background,
    flex: 1,
    gap: 20,
    justifyContent: "center",
    padding: 24,
  },
  title: {
    color: theme.text,
    fontSize: 22,
    fontWeight: "700",
    lineHeight: 30,
  },
  backButton: {
    alignItems: "center",
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: 1,
    justifyContent: "center",
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 18,
  },
  backText: {
    color: theme.accent,
    fontSize: 16,
    fontWeight: "600",
  },
});
