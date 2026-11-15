import { useCallback, useEffect, useRef, useState } from "react";

import { useApiClient } from "../api/ApiClientProvider";
import type { ApiFailureOutcome } from "../api/client";
import { isSearchResponse, type SearchHit } from "../api/searchGuards";

/** Search has no cursor, so this is also the only page size the slice can request. */
export const SEARCH_LIMIT = 20;

export type SearchState =
  | { status: "idle" }
  | { status: "loading"; query: string }
  | { status: "loaded"; query: string; items: SearchHit[] }
  | { status: "empty"; query: string }
  | { status: "error"; query: string; outcome: ApiFailureOutcome };

export interface SearchController {
  state: SearchState;
  retry(): void;
}

const IDLE: SearchState = { status: "idle" };

function stateMatchesQuery(state: SearchState, query: string): boolean {
  return state.status !== "idle" && state.query === query;
}

/**
 * Runs the one anonymous search that a submitted query needs.
 *
 * There is no automatic retry and no background polling. A response is applied only when it still
 * belongs to the newest query, so a slow first search cannot overwrite a newer one. A blank query
 * never reaches the client.
 */
export function useSearch(query: string): SearchController {
  const client = useApiClient();
  const normalizedQuery = query.trim();
  const [state, setState] = useState<SearchState>(IDLE);
  const [attempt, setAttempt] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;

    if (normalizedQuery.length === 0) {
      return;
    }

    void (async () => {
      const result = await client.get("/api/properties/search", isSearchResponse, {
        params: { query: { q: normalizedQuery, limit: SEARCH_LIMIT } },
      });
      if (requestSequence.current !== requestId) {
        return;
      }
      if (result.outcome === "ok") {
        const items = result.data.items ?? [];
        setState(
          items.length === 0
            ? { status: "empty", query: normalizedQuery }
            : { status: "loaded", query: normalizedQuery, items },
        );
        return;
      }
      setState({ status: "error", query: normalizedQuery, outcome: result.outcome });
    })();

    return () => {
      if (requestSequence.current === requestId) {
        requestSequence.current += 1;
      }
    };
  }, [attempt, client, normalizedQuery]);

  const retry = useCallback(() => {
    if (normalizedQuery.length === 0) {
      return;
    }
    setState({ status: "loading", query: normalizedQuery });
    setAttempt((value) => value + 1);
  }, [normalizedQuery]);

  const visibleState: SearchState =
    normalizedQuery.length === 0
      ? IDLE
      : stateMatchesQuery(state, normalizedQuery)
        ? state
        : { status: "loading", query: normalizedQuery };

  return { state: visibleState, retry };
}
