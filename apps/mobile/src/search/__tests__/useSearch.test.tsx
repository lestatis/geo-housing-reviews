import { act, renderHook, waitFor } from "@testing-library/react-native";
import type { ReactNode } from "react";

import { ApiClientProvider } from "../../api/ApiClientProvider";
import type { MobileApiClient } from "../../api/client";
import { useSearch, type SearchController } from "../useSearch";

const firstHit = {
  propertyId: "property-1",
  canonicalName: "Abashidze 12",
  type: "BUILDING",
  address: { city: "Batumi", district: "Old Batumi", street: "Abashidze", building: "12" },
  score: 0.91,
  distanceMeters: 1250,
};

const secondHit = {
  propertyId: "property-2",
  canonicalName: "Orbi Sea Towers",
  type: "RESIDENTIAL_COMPLEX",
  address: { city: "Batumi", district: null, street: "Sherif Khimshiashvili", building: "15" },
  score: 0.87,
  distanceMeters: 980,
};

function fakeClient(get: jest.Mock): MobileApiClient {
  return {
    baseUrl: "https://api.test",
    timeoutMs: 10_000,
    get: get as unknown as MobileApiClient["get"],
  };
}

function wrapper(client: MobileApiClient) {
  return function ApiWrapper({ children }: { children: ReactNode }) {
    return <ApiClientProvider client={client}>{children}</ApiClientProvider>;
  };
}

function success(items: unknown[]) {
  return { outcome: "ok", data: { items } };
}

describe("useSearch", () => {
  it("issues exactly one search request per query and exposes the loaded items", async () => {
    const get = jest.fn().mockResolvedValue(success([firstHit]));
    const { result, rerender } = renderHook<SearchController, { query: string }>(
      ({ query }) => useSearch(query),
      {
        initialProps: { query: "  Orbi  " },
        wrapper: wrapper(fakeClient(get)),
      },
    );

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    expect(result.current.state).toEqual({
      status: "loaded",
      query: "Orbi",
      items: [firstHit],
    });
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/api/properties/search", expect.any(Function), {
      params: { query: { q: "Orbi", limit: 20 } },
    });

    rerender({ query: "Orbi" });
    await Promise.resolve();
    expect(get).toHaveBeenCalledTimes(1);
  });

  it("sends nothing for a blank or whitespace query", async () => {
    const get = jest.fn();
    const { result } = renderHook(() => useSearch("   "), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state).toEqual({ status: "idle" }));
    expect(get).not.toHaveBeenCalled();
  });

  it("maps an empty item list to the empty state", async () => {
    const get = jest.fn().mockResolvedValue(success([]));
    const { result } = renderHook(() => useSearch("Nothing here"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() =>
      expect(result.current.state).toEqual({ status: "empty", query: "Nothing here" }),
    );
  });

  it.each(["notFound", "offline", "timeout", "server", "malformed", "unknown"])(
    "maps the %s outcome without retrying automatically",
    async (outcome) => {
      const get = jest.fn().mockResolvedValue({ outcome });
      const { result } = renderHook(() => useSearch("Orbi"), {
        wrapper: wrapper(fakeClient(get)),
      });

      await waitFor(() =>
        expect(result.current.state).toEqual({
          status: "error",
          query: "Orbi",
          outcome,
        }),
      );
      expect(get).toHaveBeenCalledTimes(1);
    },
  );

  it("retries only when asked and keeps the same query", async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ outcome: "server" })
      .mockResolvedValueOnce(success([firstHit]));
    const { result } = renderHook(() => useSearch("Orbi"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("error"));

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    expect(get).toHaveBeenCalledTimes(2);
    expect(get.mock.calls[1]?.[2]).toEqual({ params: { query: { q: "Orbi", limit: 20 } } });
  });

  it("discards a response that belongs to a superseded query", async () => {
    let resolveFirst!: (value: unknown) => void;
    const firstResponse = new Promise<unknown>((resolve) => {
      resolveFirst = resolve;
    });
    const get = jest
      .fn()
      .mockReturnValueOnce(firstResponse)
      .mockResolvedValueOnce(success([secondHit]));
    const { result, rerender } = renderHook<SearchController, { query: string }>(
      ({ query }) => useSearch(query),
      {
        initialProps: { query: "First" },
        wrapper: wrapper(fakeClient(get)),
      },
    );

    await waitFor(() => expect(get).toHaveBeenCalledTimes(1));
    rerender({ query: "Second" });
    await waitFor(() =>
      expect(result.current.state).toEqual({
        status: "loaded",
        query: "Second",
        items: [secondHit],
      }),
    );

    await act(async () => {
      resolveFirst(success([firstHit]));
      await Promise.resolve();
    });

    expect(result.current.state).toEqual({
      status: "loaded",
      query: "Second",
      items: [secondHit],
    });
  });
});
