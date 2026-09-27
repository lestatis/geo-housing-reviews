import { useRouter } from "expo-router";

import HomeScreen from "../src/screens/HomeScreen";

/**
 * The home route. The screen itself lives in `src/screens` so that everything under `app/` stays a
 * route and nothing else — expo-router treats files in this directory as destinations.
 *
 * Navigation lives in the route, not the screen, so the screen is testable without mocking
 * expo-router; `q` travels in the route so a result page is linkable and restorable.
 */
export default function HomeRoute() {
  const router = useRouter();
  return (
    <HomeScreen
      onSearch={(q) => {
        router.push({ pathname: "/search", params: { q } });
      }}
    />
  );
}
