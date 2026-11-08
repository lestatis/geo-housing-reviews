import { fireEvent, render, screen } from "@testing-library/react-native";

import { ApiProvider } from "../../api/ApiProvider";
import { createMobileApiClient, type MobileApiClient } from "../../api/client";
import { SEARCH_LIMIT } from "../../api/search";
import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import SearchResultsScreen from "../SearchResultsScreen";

function stubClient(get: jest.Mock): MobileApiClient {
  return { baseUrl: "http://example.test", timeoutMs: 1000, get } as unknown as MobileApiClient;
}

function renderResults(get: jest.Mock, query: string) {
  render(
    <ApiProvider client={stubClient(get)}>
      <LocaleProvider store={createMemoryLocaleStore()} deviceLocale="en-US">
        <SearchResultsScreen query={query} />
      </LocaleProvider>
    </ApiProvider>,
  );
}

const orbiA = {
  propertyId: "a",
  canonicalName: "Orbi Sea Towers",
  type: "RESIDENTIAL_COMPLEX",
  address: { city: "Batumi", district: "Old Batumi", street: "Chavchavadze Avenue", building: "12" },
  score: 0.87,
  distanceMeters: 1250,
};

describe("SearchResultsScreen", () => {
  it("announces that it is searching while the request is in flight", () => {
    renderResults(jest.fn().mockReturnValue(new Promise(() => {})), "orbi");

    expect(screen.getByTestId("search-loading").props.accessibilityLabel).toBe("Searching");
    expect(screen.getByText("Results for «orbi»")).toBeTruthy();
  });

  it("names the query when nothing matched and suggests what to change", async () => {
    renderResults(jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [] } }), "zzz");

    expect(await screen.findByTestId("search-empty")).toBeTruthy();
    expect(screen.getByText("No building found for «zzz»")).toBeTruthy();
    expect(
      screen.getByText("Try another spelling, the street instead of the building, or the district."),
    ).toBeTruthy();
    expect(screen.queryByTestId("search-error")).toBeNull();
  });

  it("shows a recoverable error with retry when the request fails", async () => {
    const get = jest.fn().mockResolvedValue({ outcome: "server" });
    renderResults(get, "orbi");

    expect(await screen.findByTestId("search-error")).toBeTruthy();
    expect(screen.getByText("This is temporarily unavailable. Please try again.")).toBeTruthy();

    get.mockResolvedValueOnce({ outcome: "ok", data: { items: [orbiA] } });
    fireEvent.press(screen.getByTestId("search-retry"));

    expect(await screen.findByTestId("result-a")).toBeTruthy();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("says there is no connection only when the device reports no network", async () => {
    renderResults(jest.fn().mockResolvedValue({ outcome: "offline" }), "orbi");

    expect(await screen.findByText("No connection. Check your connection and try again.")).toBeTruthy();
  });

  it("folds a timeout into the temporary-availability message, never into offline", async () => {
    renderResults(jest.fn().mockResolvedValue({ outcome: "timeout" }), "orbi");

    expect(await screen.findByText("This is temporarily unavailable. Please try again.")).toBeTruthy();
    expect(screen.queryByText("No connection. Check your connection and try again.")).toBeNull();
  });

  it("renders each row's name, address and translated type without making it pressable", async () => {
    // Non-pressable rows are a deliberate 023-B decision: the property detail route is 023-C
    // scope, so a tap would be a dead end. 023-C restores the press and the "opens" semantics.
    renderResults(
      jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [orbiA] } }),
      "orbi",
    );

    const row = await screen.findByTestId("result-a");
    expect(screen.getByText("Orbi Sea Towers")).toBeTruthy();
    expect(screen.getByText("Chavchavadze Avenue 12, Old Batumi, Batumi")).toBeTruthy();
    expect(screen.getByText("Residential complex")).toBeTruthy();
    expect(row.props.accessibilityRole).toBeUndefined();
    expect(row.props.accessibilityLabel).toBe(
      "Orbi Sea Towers, Chavchavadze Avenue 12, Old Batumi, Batumi, Residential complex",
    );
    expect(row.props.accessibilityLabel).not.toContain("Opens");
  });

  it("tells two buildings with the same name apart by their address", async () => {
    renderResults(
      jest.fn().mockResolvedValue({
        outcome: "ok",
        data: {
          items: [
            orbiA,
            {
              propertyId: "b",
              canonicalName: "Orbi Sea Towers",
              type: "BUILDING",
              address: { city: "Batumi", district: "New Boulevard" },
            },
          ],
        },
      }),
      "orbi",
    );

    await screen.findByTestId("result-a");
    expect(screen.getByText("Chavchavadze Avenue 12, Old Batumi, Batumi")).toBeTruthy();
    expect(screen.getByText("New Boulevard, Batumi")).toBeTruthy();
    expect(screen.getByText("Building")).toBeTruthy();
  });

  it("says so when a hit has no address and no type rather than printing nothing", async () => {
    renderResults(
      jest.fn().mockResolvedValue({
        outcome: "ok",
        data: { items: [{ propertyId: "a", canonicalName: "Draft Tower" }] },
      }),
      "draft",
    );

    await screen.findByTestId("result-a");
    expect(screen.getByText("Address not recorded")).toBeTruthy();
    expect(screen.getByText("Type not recorded")).toBeTruthy();
  });

  it("renders the JSON-null response the backend sends instead of treating it as malformed", async () => {
    // Regression: goes through the real client and guard rather than a stubbed `get`, because the
    // defect was the guard rejecting `null` and a stubbed `get` never runs it.
    const body = `{
      "items": [
        { "propertyId": "a", "canonicalName": "Orbi Sea Towers", "type": "RESIDENTIAL_COMPLEX",
          "address": { "city": "Batumi", "district": null, "street": "Gorgiladze", "building": null },
          "score": 0.87, "distanceMeters": null },
        { "propertyId": "b", "canonicalName": "Draft Tower", "type": "BUILDING",
          "address": null, "score": 0.5, "distanceMeters": null }
      ]
    }`;
    const client = createMobileApiClient({
      baseUrl: "http://example.test",
      networkStatus: { read: async () => "online" },
      fetch: async () =>
        new Response(body, { status: 200, headers: { "content-type": "application/json" } }),
    });
    render(
      <ApiProvider client={client}>
        <LocaleProvider store={createMemoryLocaleStore()} deviceLocale="en-US">
          <SearchResultsScreen query="orbi" />
        </LocaleProvider>
      </ApiProvider>,
    );

    await screen.findByTestId("result-a");
    expect(screen.queryByTestId("search-error")).toBeNull();
    expect(screen.getByText("Gorgiladze, Batumi")).toBeTruthy();
    expect(screen.getByTestId("result-b")).toBeTruthy();
    expect(screen.getByText("Address not recorded")).toBeTruthy();
  });

  it("issues exactly one search request and never fetches a hit's detail", async () => {
    const get = jest.fn().mockResolvedValue({
      outcome: "ok",
      data: {
        items: [
          orbiA,
          { propertyId: "b", canonicalName: "Alliance Palace", address: { city: "Batumi" } },
        ],
      },
    });
    renderResults(get, "orbi");

    await screen.findByTestId("result-a");
    await screen.findByTestId("result-b");

    expect(get).toHaveBeenCalledTimes(1);
    expect(get.mock.calls.map((call) => call[0])).toEqual(["/api/properties/search"]);
  });

  it("never renders the ranking score or the distance", async () => {
    // score is in the contract for a "did you mean" decision, not for a phone screen; distance is
    // not shown because this slice has no location UI.
    renderResults(
      jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [orbiA] } }),
      "orbi",
    );

    await screen.findByTestId("result-a");
    const tree = JSON.stringify(screen.toJSON());

    expect(tree).not.toContain("score");
    expect(tree).not.toContain("0.87");
    expect(tree).not.toContain("distanceMeters");
    expect(tree).not.toContain("1250");
  });

  it("warns that the list is only the first page when the limit comes back full", async () => {
    const items = Array.from({ length: SEARCH_LIMIT }, (_, index) => ({
      propertyId: `p${index}`,
      canonicalName: `Building ${index}`,
      address: { city: "Batumi" },
    }));
    renderResults(jest.fn().mockResolvedValue({ outcome: "ok", data: { items } }), "batumi");

    await screen.findByTestId("result-p0");
    expect(screen.getByText("Showing the first 20")).toBeTruthy();
  });

  it("does not claim a page limit when fewer than a full page came back", async () => {
    renderResults(
      jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [orbiA] } }),
      "orbi",
    );

    await screen.findByTestId("result-a");
    expect(screen.queryByText(/Showing the first/)).toBeNull();
  });

  it("does not call the API for a blank route parameter", () => {
    const get = jest.fn();
    renderResults(get, "   ");

    expect(get).not.toHaveBeenCalled();
    expect(screen.getByTestId("search-empty")).toBeTruthy();
  });
});
