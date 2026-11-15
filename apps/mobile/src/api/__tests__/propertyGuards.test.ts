import { isPropertyResponse } from "../propertyGuards";

const validProperty = {
  propertyId: "property-1",
  type: "BUILDING",
  status: "ACTIVE",
  canonicalName: "Abashidze 12",
  address: {
    country: "GE",
    city: "Batumi",
    district: "Old Batumi",
    street: "Abashidze",
    building: "12",
  },
  aliases: [{ locale: "en", name: "Abashidze House", source: "USER", confidence: 0.8 }],
  version: 3,
};

describe("isPropertyResponse", () => {
  it("accepts a property and ignores extra keys the public screen never reads", () => {
    expect(
      isPropertyResponse({
        ...validProperty,
        address: { ...validProperty.address, originalText: "Apartment 42" },
        authorAccountId: "internal-account",
      }),
    ).toBe(true);
  });

  it("accepts a property with no address row and no aliases", () => {
    expect(isPropertyResponse({ ...validProperty, address: null, aliases: [] })).toBe(true);
  });

  it.each([
    ["missing canonicalName", { ...validProperty, canonicalName: undefined }],
    ["missing type", { ...validProperty, type: undefined }],
    ["missing status", { ...validProperty, status: 7 }],
    ["missing address", { ...validProperty, address: undefined }],
    ["address part of the wrong type", { ...validProperty, address: { city: 5 } }],
    ["aliases not an array", { ...validProperty, aliases: null }],
    ["alias without a name", { ...validProperty, aliases: [{ locale: "en" }] }],
    ["alias locale of the wrong type", { ...validProperty, aliases: [{ name: "Orbi", locale: 4 }] }],
    ["not an object", "property"],
  ])("rejects a body with %s", (_label, value) => {
    expect(isPropertyResponse(value)).toBe(false);
  });
});
