import { apiBaseUrl } from "../config";
import { createMobileApiClient, type MobileApiClient } from "./client";
import { createExpoNetworkStatusProbe } from "./network";

/**
 * The client the running app uses: the configured base URL and real device network evidence.
 *
 * Built lazily from the root layout rather than at module load, so importing a screen (a test, or a
 * tool) never touches `expo-network`.
 */
export function createDefaultApiClient(): MobileApiClient {
  return createMobileApiClient({
    baseUrl: apiBaseUrl(),
    networkStatus: createExpoNetworkStatusProbe(),
  });
}
