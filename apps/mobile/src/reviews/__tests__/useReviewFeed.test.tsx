import { act, renderHook, waitFor } from "@testing-library/react-native";
import type { ReactNode } from "react";

import { ApiClientProvider } from "../../api/ApiClientProvider";
import type { MobileApiClient } from "../../api/client";
import { useReviewFeed, type ReviewFeedController } from "../useReviewFeed";

function review(reviewId: string) {
  return {
    reviewId,
    propertyId: "property-1",
    relationshipType: "CURRENT_RESIDENT",
    residenceFrom: "2023-01-01",
    residenceTo: null,
    status: "PUBLISHED",
    verificationTier: "UNVERIFIED",
    content: {
      versionNumber: 1,
      locale: "en",
      body: "Body",
      pros: null,
      cons: null,
      recommendation: "NEUTRAL",
      ratings: [],
      createdAt: "2024-01-01T00:00:00Z",
    },
    publishedAt: "2024-01-01T00:00:00Z",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    version: 1,
    helpfulCount: 0,
  };
}

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

function page(items: unknown[], nextCursor: string | null) {
  return { outcome: "ok", data: { items, nextCursor } };
}

describe("useReviewFeed", () => {
  it("walks two pages, appends them and stops on a null cursor", async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce(page([review("review-1")], "cursor-1"))
      .mockResolvedValueOnce(page([review("review-2")], null));
    const { result } = renderHook(() => useReviewFeed("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    expect(result.current.state).toMatchObject({
      status: "loaded",
      nextCursor: "cursor-1",
      paging: "idle",
    });

    act(() => result.current.loadMore());
    await waitFor(() =>
      expect(result.current.state).toMatchObject({
        status: "loaded",
        nextCursor: null,
        paging: "idle",
      }),
    );
    if (result.current.state.status !== "loaded") {
      throw new Error("expected a loaded feed");
    }
    expect(result.current.state.reviews.map((item) => item.reviewId)).toEqual([
      "review-1",
      "review-2",
    ]);

    expect(get).toHaveBeenNthCalledWith(
      1,
      "/api/properties/{propertyId}/reviews",
      expect.any(Function),
      { params: { path: { propertyId: "property-1" }, query: { limit: 20 } } },
    );
    expect(get).toHaveBeenNthCalledWith(
      2,
      "/api/properties/{propertyId}/reviews",
      expect.any(Function),
      {
        params: {
          path: { propertyId: "property-1" },
          query: { cursor: "cursor-1", limit: 20 },
        },
      },
    );

    act(() => result.current.loadMore());
    await Promise.resolve();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("keeps page one when page two fails and retries that page without a reload", async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce(page([review("review-1")], "cursor-1"))
      .mockResolvedValueOnce({ outcome: "offline" })
      .mockResolvedValueOnce(page([review("review-2")], null));
    const { result } = renderHook(() => useReviewFeed("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    act(() => result.current.loadMore());

    await waitFor(() =>
      expect(result.current.state).toMatchObject({
        status: "loaded",
        nextCursor: "cursor-1",
        paging: "error",
        pagingOutcome: "offline",
      }),
    );
    if (result.current.state.status !== "loaded") {
      throw new Error("expected a loaded feed");
    }
    expect(result.current.state.reviews.map((item) => item.reviewId)).toEqual(["review-1"]);

    act(() => result.current.loadMore());
    await waitFor(() =>
      expect(result.current.state).toMatchObject({ status: "loaded", paging: "idle" }),
    );
    if (result.current.state.status !== "loaded") {
      throw new Error("expected a loaded feed");
    }
    expect(result.current.state.reviews.map((item) => item.reviewId)).toEqual([
      "review-1",
      "review-2",
    ]);
    expect(get).toHaveBeenCalledTimes(3);
  });

  it("never starts a second page request while one is in flight", async () => {
    let resolvePage!: (value: unknown) => void;
    const pendingPage = new Promise<unknown>((resolve) => {
      resolvePage = resolve;
    });
    const get = jest
      .fn()
      .mockResolvedValueOnce(page([review("review-1")], "cursor-1"))
      .mockReturnValueOnce(pendingPage);
    const { result } = renderHook(() => useReviewFeed("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    act(() => {
      result.current.loadMore();
      result.current.loadMore();
    });

    await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
    await act(async () => {
      resolvePage(page([review("review-2")], null));
      await Promise.resolve();
    });
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("retries a failed first page on request", async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ outcome: "server" })
      .mockResolvedValueOnce(page([review("review-1")], null));
    const { result } = renderHook(() => useReviewFeed("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() =>
      expect(result.current.state).toEqual({ status: "error", outcome: "server" }),
    );
    act(() => result.current.retry());
    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
  });

  it("discards a first-page response that belongs to a superseded property", async () => {
    let resolveFirst!: (value: unknown) => void;
    const firstResponse = new Promise<unknown>((resolve) => {
      resolveFirst = resolve;
    });
    const get = jest
      .fn()
      .mockReturnValueOnce(firstResponse)
      .mockResolvedValueOnce(page([review("review-2")], null));
    const { result, rerender } = renderHook<ReviewFeedController, { id: string }>(
      ({ id }) => useReviewFeed(id),
      {
        initialProps: { id: "property-1" },
        wrapper: wrapper(fakeClient(get)),
      },
    );

    await waitFor(() => expect(get).toHaveBeenCalledTimes(1));
    rerender({ id: "property-2" });
    await waitFor(() => expect(result.current.state.status).toBe("loaded"));

    await act(async () => {
      resolveFirst(page([review("review-1")], null));
      await Promise.resolve();
    });
    if (result.current.state.status !== "loaded") {
      throw new Error("expected a loaded feed");
    }
    expect(result.current.state.reviews.map((item) => item.reviewId)).toEqual(["review-2"]);
  });

  it("does not request more when the feed has no next cursor", async () => {
    const get = jest.fn().mockResolvedValueOnce(page([review("review-1")], null));
    const { result } = renderHook(() => useReviewFeed("property-1"), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() => expect(result.current.state.status).toBe("loaded"));
    act(() => result.current.loadMore());
    await Promise.resolve();
    expect(get).toHaveBeenCalledTimes(1);
  });

  it("sends nothing for a blank id and reports it as not found", async () => {
    const get = jest.fn();
    const { result } = renderHook(() => useReviewFeed("   "), {
      wrapper: wrapper(fakeClient(get)),
    });

    await waitFor(() =>
      expect(result.current.state).toEqual({ status: "error", outcome: "notFound" }),
    );
    expect(get).not.toHaveBeenCalled();
  });
});
