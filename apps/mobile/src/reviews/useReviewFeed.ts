import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { useApiClient } from "../api/ApiClientProvider";
import type { ApiFailureOutcome } from "../api/client";
import { isReviewPage, type Review } from "../api/reviewGuards";

/** The one page size the slice requests; a full first page is what the notice reports. */
export const REVIEW_PAGE_SIZE = 20;

export type ReviewPaging = "idle" | "loading" | "error";

export type ReviewFeedState =
  | { status: "loading" }
  | { status: "error"; outcome: ApiFailureOutcome }
  | {
      status: "loaded";
      reviews: Review[];
      nextCursor: string | null;
      paging: ReviewPaging;
      pagingOutcome: ApiFailureOutcome | null;
    };

export interface ReviewFeedController {
  state: ReviewFeedState;
  /** Loads the next page, or retries the page that just failed. Never runs while one is in flight. */
  loadMore(): void;
  /** Retries the first page after a first-page failure. */
  retry(): void;
}

interface FeedSnapshot {
  propertyId: string;
  state: ReviewFeedState;
}

const REVIEWS_PATH = "/api/properties/{propertyId}/reviews" as const;

/**
 * The anonymous cursor-paged review feed.
 *
 * Pages are appended, never replaced. A failed next page keeps every page already on screen and
 * surfaces its own outcome; loading it again is the same `loadMore`. There is no automatic retry,
 * and a request is dropped when a newer one has superseded it. Loading is derived from the
 * snapshot's id rather than written into state inside the effect.
 */
export function useReviewFeed(propertyId: string): ReviewFeedController {
  const client = useApiClient();
  const normalizedId = propertyId.trim();
  const [snapshot, setSnapshot] = useState<FeedSnapshot | null>(null);
  const [attempt, setAttempt] = useState(0);
  const requestSequence = useRef(0);
  const inFlight = useRef(false);

  useEffect(() => {
    if (normalizedId.length === 0) {
      return;
    }
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;
    inFlight.current = true;

    void (async () => {
      const result = await client.get(REVIEWS_PATH, isReviewPage, {
        params: { path: { propertyId: normalizedId }, query: { limit: REVIEW_PAGE_SIZE } },
      });
      if (requestSequence.current !== requestId) {
        return;
      }
      inFlight.current = false;
      setSnapshot({
        propertyId: normalizedId,
        state:
          result.outcome === "ok"
            ? {
                status: "loaded",
                reviews: result.data.items,
                nextCursor: result.data.nextCursor,
                paging: "idle",
                pagingOutcome: null,
              }
            : { status: "error", outcome: result.outcome },
      });
    })();

    return () => {
      if (requestSequence.current === requestId) {
        requestSequence.current += 1;
      }
      inFlight.current = false;
    };
  }, [attempt, client, normalizedId]);

  const retry = useCallback(() => {
    if (normalizedId.length === 0) {
      return;
    }
    setSnapshot(null);
    setAttempt((value) => value + 1);
  }, [normalizedId]);

  const state = useMemo<ReviewFeedState>(
    () =>
      normalizedId.length === 0
        ? { status: "error", outcome: "notFound" }
        : snapshot && snapshot.propertyId === normalizedId
          ? snapshot.state
          : { status: "loading" },
    [normalizedId, snapshot],
  );

  const loadMore = useCallback(() => {
    if (state.status !== "loaded" || inFlight.current || state.nextCursor === null) {
      return;
    }
    const cursor = state.nextCursor;
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;
    inFlight.current = true;
    setSnapshot({
      propertyId: normalizedId,
      state: { ...state, paging: "loading", pagingOutcome: null },
    });

    void (async () => {
      const result = await client.get(REVIEWS_PATH, isReviewPage, {
        params: { path: { propertyId: normalizedId }, query: { cursor, limit: REVIEW_PAGE_SIZE } },
      });
      if (requestSequence.current !== requestId) {
        return;
      }
      inFlight.current = false;
      if (result.outcome === "ok") {
        const page = result.data;
        setSnapshot((current) =>
          current && current.propertyId === normalizedId && current.state.status === "loaded"
            ? {
                propertyId: normalizedId,
                state: {
                  ...current.state,
                  reviews: [...current.state.reviews, ...page.items],
                  nextCursor: page.nextCursor,
                  paging: "idle",
                  pagingOutcome: null,
                },
              }
            : current,
        );
        return;
      }
      setSnapshot((current) =>
        current && current.propertyId === normalizedId && current.state.status === "loaded"
          ? {
              propertyId: normalizedId,
              state: {
                ...current.state,
                paging: "error",
                pagingOutcome: result.outcome,
              },
            }
          : current,
      );
    })();
  }, [client, normalizedId, state]);

  return { state, loadMore, retry };
}
