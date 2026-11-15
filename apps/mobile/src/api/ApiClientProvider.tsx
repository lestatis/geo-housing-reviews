import { createContext, useContext, type ReactNode } from "react";

import type { MobileApiClient } from "./client";

const ApiClientContext = createContext<MobileApiClient | undefined>(undefined);

export interface ApiClientProviderProps {
  client: MobileApiClient;
  children: ReactNode;
}

/**
 * Supplies the one anonymous API client the app uses.
 *
 * The provider takes an already-constructed client rather than building one here so a test can
 * inject a fake without importing the device network probe. Production construction happens once in
 * `app/_layout.tsx`.
 */
export function ApiClientProvider({ client, children }: ApiClientProviderProps) {
  return <ApiClientContext.Provider value={client}>{children}</ApiClientContext.Provider>;
}

export function useApiClient(): MobileApiClient {
  const client = useContext(ApiClientContext);
  if (!client) {
    throw new Error("useApiClient must be used inside ApiClientProvider");
  }
  return client;
}
