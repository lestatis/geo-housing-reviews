import { createContext, useContext, type ReactNode } from "react";

import type { MobileApiClient } from "./client";

/**
 * Hands screens the anonymous public client without importing a native module at module load.
 *
 * Screens depend on the client through this context rather than constructing one, so a test drives
 * every network state with a stub instead of a real request (plan 023, "Verification").
 */
const ApiContext = createContext<MobileApiClient | undefined>(undefined);

export interface ApiProviderProps {
  client: MobileApiClient;
  children: ReactNode;
}

export function ApiProvider({ client, children }: ApiProviderProps) {
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>;
}

export function useApiClient(): MobileApiClient {
  const client = useContext(ApiContext);
  if (!client) {
    throw new Error("useApiClient must be used inside ApiProvider");
  }
  return client;
}
