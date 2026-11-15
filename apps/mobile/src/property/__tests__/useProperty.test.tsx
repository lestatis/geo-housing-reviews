import { act, renderHook, waitFor } from "@testing-library/react-native";
import type { ReactNode } from "react";

import { ApiClientProvider } from "../../api/ApiClientProvider";
import type { MobileApiClient } from "../../api/client";
import { useProperty, type PropertyController } from "../useProperty";

const property = {
  propertyId: "property-1",
  type: "BUILDING",
  status: "ACTIVE",
  canonicalName: "Abashidze 12",
  address: { city: "Batumi", district: "Old Batumi", street: "Abashidze", building: "12" },
  aliases: [],
  version: 1,
};

const secondProperty = { ...property, propertyId: "property-2", canonicalName: "Orbi" };

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

function success(data: unknown) {
  return { outcome: "ok", data };
}

describe("useProperty", () => {
  it("issues exactly one property request and exposes the loaded property", async () => {
    const get = jest.fn().mockResolvedValue(success(property));
    const { result } = renderHook(() => useProperty("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    expect(result.current.state).toEqual({ status: "loaded", property });
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/api/properties/{propertyId}", expect.any(Function), {
      params: { path: { propertyId: "property-1" } },
    });
  });

  it("maps 404 to notFound and every other failure to a recoverable error", async () => {
    const notFound = jest.fn().mockResolvedValue({ outcome: "notFound" });
    const notFoundHook = renderHook(() => useProperty("property-1"), {
      wrapper: wrapper(fakeClient(notFound)),
    });
    await waitFor(() => expect(notFoundHook.result.current.state).toEqual({ status: "notFound" }));

    const error = jest.fn().mockResolvedValue({ outcome: "timeout" });
    const errorHook = renderHook(() => useProperty("property-1"), {
      wrapper: wrapper(fakeClient(error)),
    });
    await waitFor(() =>
      expect(errorHook.result.current.state).toEqual({ status: "error", outcome: "timeout" }),
    );
  });

  it("retries only when asked", async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ outcome: "server" })
      .mockResolvedValueOnce(success(property));
    const { result } = renderHook(() => useProperty("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("error"));
    act(() => result.current.retry());

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("discards a response that belongs to a superseded property id", async () => {
    let resolveFirst!: (value: unknown) => void;
    const firstResponse = new Promise<unknown>((resolve) => {
      resolveFirst = resolve;
    });
    const get = jest.fn().mockReturnValueOnce(firstResponse).mockResolvedValueOnce(success(secondProperty));
    const { result, rerender } = renderHook<PropertyController, { id: string }>(
      ({ id }) => useProperty(id),
      {
        initialProps: { id: "property-1" },
        wrapper: wrapper(fakeClient(get)),
      },
    );

    await waitFor(() => expect(get).toHaveBeenCalledTimes(1));
    rerender({ id: "property-2" });
    await waitFor(() =>
      expect(result.current.state).toEqual({ status: "loaded", property: secondProperty }),
    );

    await act(async () => {
      resolveFirst(success(property));
      await Promise.resolve();
    });

    expect(result.current.state).toEqual({ status: "loaded", property: secondProperty });
  });

  it("sends nothing for a blank id and reports it as not found", async () => {
    const get = jest.fn();
    const { result } = renderHook(() => useProperty("   "), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state).toEqual({ status: "notFound" }));
    expect(get).not.toHaveBeenCalled();
  });
});
