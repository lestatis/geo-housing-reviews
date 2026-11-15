import { formatAddressSummary } from "../formatAddressSummary";

describe("formatAddressSummary", () => {
  it("formats the full address in street building, district, city order", () => {
    expect(
      formatAddressSummary({
        street: "Abashidze",
        building: "12",
        district: "Old Batumi",
        city: "Batumi",
      }),
    ).toBe("Abashidze 12, Old Batumi, Batumi");
  });

  it("omits blank parts instead of leaving separators or placeholders", () => {
    expect(
      formatAddressSummary({
        street: "Abashidze",
        building: null,
        district: " ",
        city: "Batumi",
      }),
    ).toBe("Abashidze, Batumi");
    expect(formatAddressSummary({ street: "Abashidze", building: "12" })).toBe("Abashidze 12");
  });

  it("renders nothing for a missing address row", () => {
    expect(formatAddressSummary(null)).toBe("");
    expect(formatAddressSummary(undefined)).toBe("");
  });
});
