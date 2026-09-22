import { createExpoNetworkStatusProbe, unknownNetworkStatus, type NetworkState } from "../network";

// The probe's only source of device evidence. `expo-network` is a native module, so the mapping
// from its state to our classification is tested here against a stub rather than a device.
const mockGetNetworkStateAsync = jest.fn();
jest.mock("expo-network", () => ({
  getNetworkStateAsync: (...args: unknown[]) => mockGetNetworkStateAsync(...args),
}));

async function probeReports(state: unknown): Promise<NetworkState> {
  mockGetNetworkStateAsync.mockReset();
  mockGetNetworkStateAsync.mockResolvedValue(state);
  return createExpoNetworkStatusProbe().read();
}

describe("device network probe", () => {
  it("reports offline when the device says it has no connection", async () => {
    await expect(probeReports({ isConnected: false })).resolves.toBe("offline");
  });

  it("reports offline when the device is connected but cannot reach the internet", async () => {
    await expect(
      probeReports({ isConnected: true, isInternetReachable: false }),
    ).resolves.toBe("offline");
  });

  it("reports online when the device is connected and reachability is unknown", async () => {
    await expect(
      probeReports({ isConnected: true, isInternetReachable: undefined }),
    ).resolves.toBe("online");
  });

  it("reports unknown when the native module rejects", async () => {
    mockGetNetworkStateAsync.mockReset();
    mockGetNetworkStateAsync.mockRejectedValue(new Error("native module unavailable"));

    await expect(createExpoNetworkStatusProbe().read()).resolves.toBe("unknown");
  });

  it("reports unknown when the platform returns no usable state", async () => {
    await expect(probeReports({})).resolves.toBe("unknown");
  });
});

describe("default network probe", () => {
  it("never claims the device is offline", async () => {
    // Nothing may report "no connection" without real device evidence (plan 023, "Error model").
    await expect(unknownNetworkStatus.read()).resolves.toBe("unknown");
  });
});
