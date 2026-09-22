/**
 * The only source of "the device has no network" evidence.
 *
 * Classification rule from plan 023: `offline` may be returned **only** when the device reports no
 * network. A request that hit its 10 s deadline is a `timeout`, never `offline`, and a transport
 * error on a device that believes it is online is `unknown`.
 */

export type NetworkState = "online" | "offline" | "unknown";

export interface NetworkStatusProbe {
  read(): Promise<NetworkState>;
}

/** Used when no native network module is wired up: a failure is then never claimed to be offline. */
export const unknownNetworkStatus: NetworkStatusProbe = {
  async read(): Promise<NetworkState> {
    return "unknown";
  },
};

/**
 * Reads the platform network state through `expo-network`.
 *
 * The module import is lazy so that node-side tests, which inject their own probe, never load a
 * native module. An unreadable state is `unknown`: guessing "offline" would put a false "no
 * connection" message in front of a user whose device is online.
 */
export function createExpoNetworkStatusProbe(): NetworkStatusProbe {
  return {
    async read(): Promise<NetworkState> {
      try {
        const network = await import("expo-network");
        const state = await network.getNetworkStateAsync();
        if (state.isConnected === false || state.isInternetReachable === false) {
          return "offline";
        }
        if (state.isConnected === true) {
          return "online";
        }
        return "unknown";
      } catch {
        // The native module is unavailable in this runtime; that is not evidence of being offline.
        return "unknown";
      }
    },
  };
}
