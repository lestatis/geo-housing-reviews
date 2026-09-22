import { getNetworkStateAsync } from "expo-network";

/**
 * The only source of "the device has no network" evidence.
 *
 * Classification rule from plan 023: `offline` may be returned **only** when the device reports no
 * network. A request that hit its 10 s deadline is a `timeout`, never `offline`, and a transport
 * error on a device that believes it is online is `unknown`.
 *
 * The import is static on purpose. A dynamic `import("expo-network")` inside `read()` is invisible
 * to Jest's module registry — it fails with "A dynamic import callback was invoked without
 * --experimental-vm-modules" — and that failure was caught here and reported as `unknown`, so the
 * mapping below could not be tested and would have looked correct while returning `unknown` for
 * every state. A static import is mockable and behaves the same in Metro.
 */

export type NetworkState = "online" | "offline" | "unknown";

export interface NetworkStatusProbe {
  read(): Promise<NetworkState>;
}

/**
 * An explicit opt-in for callers with no device evidence, such as tests.
 *
 * It is not a default: `MobileApiClientOptions.networkStatus` has no default precisely so that a
 * probe cannot be forgotten, and a failure is then classified as `unknown` rather than `offline`.
 */
export const unknownNetworkStatus: NetworkStatusProbe = {
  async read(): Promise<NetworkState> {
    return "unknown";
  },
};

/**
 * Reads the platform network state through `expo-network`.
 *
 * An unreadable state is `unknown`: guessing "offline" would put a false "no connection" message in
 * front of a user whose device is online.
 */
export function createExpoNetworkStatusProbe(): NetworkStatusProbe {
  return {
    async read(): Promise<NetworkState> {
      try {
        const state = await getNetworkStateAsync();
        if (state.isConnected === false || state.isInternetReachable === false) {
          return "offline";
        }
        if (state.isConnected === true) {
          return "online";
        }
        return "unknown";
      } catch {
        // The platform call failed; that is not evidence of being offline.
        return "unknown";
      }
    },
  };
}
