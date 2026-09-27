import { useLocalSearchParams } from "expo-router";

import SearchResultsScreen from "../src/screens/SearchResultsScreen";

/**
 * The results route: ranked matches for the `q` it was opened with.
 */
export default function SearchRoute() {
  const params = useLocalSearchParams<{ q?: string }>();
  return (
    <SearchResultsScreen
      query={typeof params.q === "string" ? params.q : ""}
    />
  );
}
