import { unknownNetworkStatus } from "../network";

describe("default network probe", () => {
  it("never claims the device is offline", async () => {
    // Nothing may report "no connection" without real device evidence (plan 023, "Error model").
    await expect(unknownNetworkStatus.read()).resolves.toBe("unknown");
  });
});
