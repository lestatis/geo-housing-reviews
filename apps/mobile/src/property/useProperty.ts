import { useCallback, useEffect, useRef, useState } from "react";

import { useApiClient } from "../api/ApiClientProvider";
import type { ApiFailureOutcome } from "../api/client";
import { isPropertyResponse, type Property } from "../api/propertyGuards";

export type PropertyState =
  | { status: "loading" }
  | { status: "loaded"; property: Property }
  | { status: "notFound" }
  | { status: "error"; outcome: ApiFailureOutcome };

export interface PropertyController {
  state: PropertyState;
  retry(): void;
}

interface PropertySnapshot {
  propertyId: string;
  state: PropertyState;
}

/**
 * Reads one public property.
 *
 * The request is the only one this hook makes and there is no automatic retry: the screen offers a
 * manual, idempotent Retry. A response is applied only when it still belongs to the newest request,
 * so a superseded id or a superseded retry cannot overwrite the current screen. Loading is derived
 * from the snapshot's id rather than written into state inside the effect.
 */
export function useProperty(propertyId: string): PropertyController {
  const client = useApiClient();
  const normalizedId = propertyId.trim();
  const [snapshot, setSnapshot] = useState<PropertySnapshot | null>(null);
  const [attempt, setAttempt] = useState(0);
  const requestSequence = useRef(0);

  useEffect(() => {
    if (normalizedId.length === 0) {
      return;
    }
    const requestId = requestSequence.current + 1;
    requestSequence.current = requestId;

    void (async () => {
      const result = await client.get("/api/properties/{propertyId}", isPropertyResponse, {
        params: { path: { propertyId: normalizedId } },
      });
      if (requestSequence.current !== requestId) {
        return;
      }
      setSnapshot({
        propertyId: normalizedId,
        state:
          result.outcome === "ok"
            ? { status: "loaded", property: result.data }
            : result.outcome === "notFound"
              ? { status: "notFound" }
              : { status: "error", outcome: result.outcome },
      });
    })();

    return () => {
      if (requestSequence.current === requestId) {
        requestSequence.current += 1;
      }
    };
  }, [attempt, client, normalizedId]);

  const retry = useCallback(() => {
    if (normalizedId.length === 0) {
      return;
    }
    setSnapshot(null);
    setAttempt((value) => value + 1);
  }, [normalizedId]);

  const state: PropertyState =
    normalizedId.length === 0
      ? { status: "notFound" }
      : snapshot && snapshot.propertyId === normalizedId
        ? snapshot.state
        : { status: "loading" };

  return { state, retry };
}
