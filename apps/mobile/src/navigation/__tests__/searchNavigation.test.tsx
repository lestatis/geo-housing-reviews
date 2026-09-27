import { router } from "expo-router";
import { act, renderRouter, screen } from "expo-router/testing-library";

import * as layoutRoute from "../../../app/_layout";
import * as indexRoute from "../../../app/index";
import * as searchRoute from "../../../app/search";

/**
 * Renders the real route files, so the assertions are about the stack the app ships: home keeps its
 * header hidden, and `/search` shows the native header with its back button.
 */

jest.mock("../../api/defaultClient", () => ({
  createDefaultApiClient: () => ({
    baseUrl: "http://example.test",
    timeoutMs: 1000,
    get: jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [] } }),
  }),
}));

function renderApp(initialUrl: string) {
  return renderRouter(
    {
      _layout: layoutRoute,
      index: indexRoute,
      search: searchRoute,
    },
    { initialUrl },
  );
}

/** The native header configuration each stack screen hands to react-native-screens. */
function headerConfigs(): Record<string, unknown>[] {
  return screen.UNSAFE_root.findAll(
    (node) => (node.type as unknown) === "RNSScreenStackHeaderConfig",
  ).map((node) => node.props as Record<string, unknown>);
}

describe("search navigation", () => {
  it("gives /search a native header with a back button, and keeps home's header hidden", async () => {
    renderApp("/search?q=orbi");
    await screen.findByTestId("search-empty");

    const [home, search] = headerConfigs();
    expect(home).toMatchObject({ hidden: true });
    expect(search).toMatchObject({
      hidden: false,
      hideBackButton: false,
      title: "Search",
      backTitle: "Back",
    });
  });

  it("goes back to home even when the app was opened straight on /search", async () => {
    const app = renderApp("/search?q=orbi");
    await screen.findByTestId("search-empty");

    act(() => router.back());

    expect(app.getPathname()).toBe("/");
    expect(await screen.findByTestId("home-search-input")).toBeTruthy();
  });
});
