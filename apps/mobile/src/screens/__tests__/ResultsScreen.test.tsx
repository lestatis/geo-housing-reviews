import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiClientProvider } from "../../api/ApiClientProvider";
import type { MobileApiClient } from "../../api/client";
import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import ResultsScreen from "../ResultsScreen";

const mockPush = jest.fn();
let mockSearchParams: { q?: string | string[] } = { q: "Orbi" };

jest.mock("expo-router", () => ({
  useLocalSearchParams: () => mockSearchParams,
  useRouter: () => ({ push: mockPush }),
}));

function fakeClient(get: jest.Mock): MobileApiClient {
  return {
    baseUrl: "https://api.test",
    timeoutMs: 10_000,
    get: get as unknown as MobileApiClient["get"],
  };
}

function renderResults(get: jest.Mock) {
  render(
    <ApiClientProvider client={fakeClient(get)}>
      <LocaleProvider store={createMemoryLocaleStore()} deviceLocale="en-US">
        <ResultsScreen />
      </LocaleProvider>
    </ApiClientProvider>,
  );
}

function success(items: unknown[]) {
  return { outcome: "ok", data: { items } };
}

function searchHit(propertyId: string, street: string) {
  return {
    propertyId,
    canonicalName: "Orbi Sea Towers",
    type: "RESIDENTIAL_COMPLEX",
    address: { city: "Batumi", district: "Old Batumi", street, building: "12" },
    score: 0.91,
    distanceMeters: 1250,
  };
}

describe("ResultsScreen", () => {
  beforeEach(() => {
    mockPush.mockReset();
    mockSearchParams = { q: "Orbi" };
  });

  it("announces the loading skeleton as Searching", async () => {
    const get = jest.fn().mockReturnValue(new Promise(() => {}));
    renderResults(get);

    expect(await screen.findByText("Searching")).toBeTruthy();
    expect(screen.getByLabelText("Searching")).toBeTruthy();
  });

  it("shows a truthful empty state with search hints and no creation call to action", async () => {
    renderResults(jest.fn().mockResolvedValue(success([])));

    expect(await screen.findByText("No building found for «Orbi»")).toBeTruthy();
    expect(
      screen.getByText(
        "Check the spelling, try the street instead of the building, or try the district.",
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/add this property/i)).toBeNull();
  });

  it("folds temporary failures into one recoverable message with a retry action", async () => {
    const get = jest.fn().mockResolvedValue({ outcome: "server" });
    renderResults(get);

    expect(await screen.findByText("Search is temporarily unavailable. Please try again.")).toBeTruthy();
    expect(screen.queryByText(/No connection/)).toBeNull();

    fireEvent.press(screen.getByTestId("results-retry"));
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
  });

  it("says No connection only for a genuine offline outcome", async () => {
    renderResults(jest.fn().mockResolvedValue({ outcome: "offline" }));

    expect(
      await screen.findByText("No connection. Check your internet connection and try again."),
    ).toBeTruthy();
    expect(screen.queryByText("Search is temporarily unavailable. Please try again.")).toBeNull();
  });

  it("renders same-name properties distinguishably from one search request", async () => {
    const get = jest
      .fn()
      .mockResolvedValue(success([searchHit("property-1", "Abashidze"), searchHit("property-2", "Chavchavadze")]));
    renderResults(get);

    expect(await screen.findByText("Abashidze 12, Old Batumi, Batumi")).toBeTruthy();
    expect(screen.getByText("Chavchavadze 12, Old Batumi, Batumi")).toBeTruthy();
    expect(screen.getAllByText("Orbi Sea Towers")).toHaveLength(2);
    expect(screen.getAllByText("Residential complex")).toHaveLength(2);

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls[0]?.[0]).toBe("/api/properties/search");

    const tree = JSON.stringify(screen.toJSON());
    expect(tree).not.toContain("0.91");
    expect(tree).not.toContain("1250");

    fireEvent.press(screen.getByTestId("result-property-1"));
    expect(mockPush).toHaveBeenCalledWith("/property/property-1");
  });

  it("says it is showing the first 20 when the single page is full", async () => {
    const items = Array.from({ length: 20 }, (_, index) =>
      searchHit(`property-${index}`, `Street ${index}`),
    );
    renderResults(jest.fn().mockResolvedValue(success(items)));

    expect(await screen.findByText("Showing the first 20 results.")).toBeTruthy();
  });

  it("sends nothing and explains the route when q is missing", async () => {
    mockSearchParams = {};
    const get = jest.fn();
    renderResults(get);

    expect(await screen.findByText("Enter a building name to search.")).toBeTruthy();
    expect(get).not.toHaveBeenCalled();
  });
});
