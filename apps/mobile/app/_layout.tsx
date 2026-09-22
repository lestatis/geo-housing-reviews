import { Stack, type ErrorBoundaryProps } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { StyleSheet, Text, View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { deviceLocaleTag } from "../src/i18n/deviceLocale";
import { LocaleProvider } from "../src/i18n/LocaleProvider";
import { persistentLocaleStore } from "../src/i18n/localeStore";
import { MIN_TOUCH_TARGET, theme } from "../src/theme";

/**
 * The root layout: a stack, the locale provider, and a crash boundary.
 *
 * There is no tab bar, deliberately. Plan 023 lists five primary tabs in the design handoff, four of
 * which are non-goals in this slice; shipping dead tabs would teach the wrong thing about the
 * product.
 */
export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <LocaleProvider store={persistentLocaleStore} deviceLocale={deviceLocaleTag()}>
        <Stack screenOptions={{ headerShown: false }} />
      </LocaleProvider>
      <StatusBar style="auto" />
    </SafeAreaProvider>
  );
}

/**
 * expo-router renders this when a route throws.
 *
 * It does not use the locale hook: the boundary can render before the provider exists, and an error
 * screen that itself throws shows nothing at all. The copy is bilingual for the same reason, and it
 * shows no error message, URL or stack — those belong in logs, not in front of a user.
 */
export function ErrorBoundary({ retry }: ErrorBoundaryProps) {
  return (
    <View style={styles.errorScreen}>
      <Text accessibilityRole="header" style={styles.errorTitle}>
        Something went wrong · Что-то пошло не так
      </Text>
      <Text
        accessibilityRole="button"
        accessibilityLabel="Retry · Повторить"
        onPress={() => {
          void retry();
        }}
        style={styles.errorRetry}
      >
        Retry · Повторить
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  errorScreen: {
    backgroundColor: theme.background,
    flex: 1,
    gap: 16,
    justifyContent: "center",
    padding: 24,
  },
  errorTitle: {
    color: theme.text,
    fontSize: 18,
    fontWeight: "600",
  },
  errorRetry: {
    borderColor: theme.accent,
    borderRadius: 10,
    borderWidth: 1,
    color: theme.accent,
    fontSize: 16,
    minHeight: MIN_TOUCH_TARGET,
    paddingVertical: 12,
    textAlign: "center",
  },
});
