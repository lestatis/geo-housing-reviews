import { fireEvent, render, screen } from "@testing-library/react-native";

import { ApiClientProvider } from "../../api/ApiClientProvider";
import type { MobileApiClient } from "../../api/client";
import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import PropertyScreen from "../PropertyScreen";

const mockBack = jest.fn();
const mockCanGoBack = jest.fn();
const mockReplace = jest.fn();
let mockParams: { id?: string | string[] } = { id: "property-1" };

jest.mock("expo-router", () => ({
  useLocalSearchParams: () => mockParams,
  useRouter: () => ({ back: mockBack, canGoBack: mockCanGoBack, replace: mockReplace }),
}));

const property = {
  propertyId: "property-1",
  type: "RESIDENTIAL_COMPLEX",
  status: "ACTIVE",
  canonicalName: "Orbi Sea Towers",
  address: {
    country: "GE",
    city: "Batumi",
    district: "Old Batumi",
    street: "Sherif Khimshiashvili",
    building: "15",
    originalText: "Apartment 42",
  },
  aliases: [
    { locale: "ru", name: "Орби", source: "USER", confidence: 1 },
    { locale: "en", name: "Orbi Tower", source: "USER", confidence: 1 },
    { locale: "en", name: "Orbi Sea Towers", source: "USER", confidence: 1 },
  ],
  version: 3,
};

function review(index: number) {
  return {
    reviewId: `review-${index}`,
    propertyId: "property-1",
    relationshipType: "CURRENT_RESIDENT",
    residenceFrom: "2023-05-12",
    residenceTo: "2024-09-30",
    status: "PUBLISHED",
    verificationTier: "RELATIONSHIP_SIGNAL",
    content: {
      versionNumber: 1,
      locale: "en",
      body: `Review body ${index}`,
      pros: null,
      cons: null,
      recommendation: "RECOMMEND",
      ratings: [],
      createdAt: "2024-09-30T10:00:00Z",
    },
    publishedAt: "2024-09-30T10:00:00Z",
    createdAt: "2024-09-30T09:00:00Z",
    updatedAt: "2024-09-30T10:00:00Z",
    version: 2,
    helpfulCount: 0,
  };
}

function success(data: unknown) {
  return { outcome: "ok", data };
}

function page(items: unknown[], nextCursor: string | null) {
  return success({ items, nextCursor });
}

function fakeClient(get: jest.Mock): MobileApiClient {
  return {
    baseUrl: "https://api.test",
    timeoutMs: 10_000,
    get: get as unknown as MobileApiClient["get"],
  };
}

function responses(propertyResult: unknown, feedResult: unknown) {
  return jest.fn((path: string) =>
    Promise.resolve(path.endsWith("/reviews") ? feedResult : propertyResult),
  );
}

function renderScreen(get: jest.Mock) {
  render(
    <ApiClientProvider client={fakeClient(get)}>
      <LocaleProvider store={createMemoryLocaleStore("en")} deviceLocale="en-US">
        <PropertyScreen />
      </LocaleProvider>
    </ApiClientProvider>,
  );
}

describe("PropertyScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockParams = { id: "property-1" };
    mockCanGoBack.mockReturnValue(true);
  });

  it("announces the header and feed skeletons while loading", async () => {
    renderScreen(jest.fn(() => new Promise(() => {})));

    expect(await screen.findByText("Loading the building")).toBeTruthy();
    expect(screen.getByText("Loading reviews")).toBeTruthy();
    expect(screen.getByLabelText("Loading the building")).toBeTruthy();
    expect(screen.getByLabelText("Loading reviews")).toBeTruthy();
  });

  it("renders identity, aliases and address without the typed address text", async () => {
    renderScreen(responses(success(property), page([], null)));

    expect(await screen.findByText("Orbi Sea Towers")).toBeTruthy();
    expect(screen.getByText("Also known as: Orbi Tower, Орби")).toBeTruthy();
    expect(
      screen.getByText("Sherif Khimshiashvili 15, Old Batumi, Batumi"),
    ).toBeTruthy();
    expect(screen.getByText("Residential complex")).toBeTruthy();
    expect(await screen.findByText("No published reviews yet.")).toBeTruthy();
    expect(JSON.stringify(screen.toJSON())).not.toContain("Apartment 42");
  });

  it("shows the low-confidence notice for a DRAFT property", async () => {
    renderScreen(responses(success({ ...property, status: "DRAFT" }), page([], null)));

    expect(
      await screen.findByText(
        "This property was added by a user and has not yet been reviewed by our team.",
      ),
    ).toBeTruthy();
  });

  it("reads a 404 as a normal no-longer-listed outcome", async () => {
    renderScreen(responses({ outcome: "notFound" }, page([], null)));

    expect(await screen.findByText("This building is no longer listed")).toBeTruthy();
  });

  it("fails the whole screen on a header error and retries on request", async () => {
    let propertyReady = false;
    const get = jest.fn((path: string) => {
      if (path.endsWith("/reviews")) {
        return Promise.resolve(page([], null));
      }
      return Promise.resolve(propertyReady ? success(property) : { outcome: "server" });
    });
    renderScreen(get);

    expect(
      await screen.findByText("This building is temporarily unavailable. Please try again."),
    ).toBeTruthy();
    expect(screen.queryByText("No published reviews yet.")).toBeNull();

    propertyReady = true;
    fireEvent.press(screen.getByTestId("property-retry"));
    expect(await screen.findByText("Orbi Sea Towers")).toBeTruthy();
  });

  it("keeps the header and retries a failed first review page", async () => {
    let feedReady = false;
    const get = jest.fn((path: string) => {
      if (path.endsWith("/reviews")) {
        return Promise.resolve(feedReady ? page([], null) : { outcome: "server" });
      }
      return Promise.resolve(success(property));
    });
    renderScreen(get);

    expect(await screen.findByText("Orbi Sea Towers")).toBeTruthy();
    expect(
      await screen.findByText("Reviews are temporarily unavailable. Please try again."),
    ).toBeTruthy();

    feedReady = true;
    fireEvent.press(screen.getByTestId("reviews-retry"));
    expect(await screen.findByText("No published reviews yet.")).toBeTruthy();
  });

  it("says it is showing the first page and appends the next one", async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) => review(index + 1));
    const get = jest
      .fn()
      .mockResolvedValueOnce(success(property))
      .mockResolvedValueOnce(page(firstPage, "cursor-1"))
      .mockResolvedValueOnce(page([review(21)], null));
    renderScreen(get);

    expect(await screen.findByText("Showing the first 20 reviews.")).toBeTruthy();
    expect(screen.getByText("Review body 1")).toBeTruthy();

    fireEvent.press(screen.getByTestId("reviews-load-more"));
    expect(await screen.findByText("Review body 21")).toBeTruthy();
    expect(screen.queryByText("Showing the first 20 reviews.")).toBeNull();
    expect(get.mock.calls[2]?.[2]).toEqual({
      params: {
        path: { propertyId: "property-1" },
        query: { cursor: "cursor-1", limit: 20 },
      },
    });
  });

  it("keeps loaded pages when the next page fails and retries it in place", async () => {
    let pageTwoReady = false;
    const get = jest.fn((path: string, _guard: unknown, options?: { params?: { query?: { cursor?: string } } }) => {
      if (!path.endsWith("/reviews")) {
        return Promise.resolve(success(property));
      }
      if (!options?.params?.query?.cursor) {
        return Promise.resolve(page([review(1)], "cursor-1"));
      }
      return Promise.resolve(pageTwoReady ? page([review(2)], null) : { outcome: "offline" });
    });
    renderScreen(get);

    expect(await screen.findByText("Review body 1")).toBeTruthy();
    fireEvent.press(screen.getByTestId("reviews-load-more"));

    expect(
      await screen.findByText("No connection. Check your internet connection and try again."),
    ).toBeTruthy();
    expect(screen.getByText("Review body 1")).toBeTruthy();
    expect(screen.getByLabelText("Retry")).toBeTruthy();

    pageTwoReady = true;
    fireEvent.press(screen.getByTestId("reviews-load-more"));
    expect(await screen.findByText("Review body 2")).toBeTruthy();
  });

  it("never puts an internal account identifier in the rendered tree", async () => {
    renderScreen(
      responses(success(property), page([{ ...review(1), authorAccountId: "account-internal-123" }], null)),
    );

    await screen.findByText("Review body 1");
    expect(JSON.stringify(screen.toJSON())).not.toContain("account-internal-123");
  });

  it("returns home when a deep link leaves no history to go back to", async () => {
    mockCanGoBack.mockReturnValue(false);
    renderScreen(responses(success(property), page([], null)));

    await screen.findByText("Orbi Sea Towers");
    fireEvent.press(screen.getByLabelText("Back"));
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).toHaveBeenCalledWith("/");
  });
});
