import { Stack, useLocalSearchParams } from "expo-router";

import { useMessages } from "../src/i18n/LocaleProvider";
import SearchResultsScreen from "../src/screens/SearchResultsScreen";

/**
 * The results route: ranked matches for the `q` it was opened with.
 *
 * The root stack hides headers for home; this route turns the native header back on so there is an
 * explicit, platform-accessible way back to the search entry.
 */
export default function SearchRoute() {
  const params = useLocalSearchParams<{ q?: string }>();
  const { t } = useMessages();
  return (
    <>
      <Stack.Screen
        options={{ headerShown: true, title: t("search.title"), headerBackTitle: t("search.back") }}
      />
      <SearchResultsScreen query={typeof params.q === "string" ? params.q : ""} />
    </>
  );
}
