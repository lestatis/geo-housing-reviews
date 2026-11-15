import { isSearchResponse } from "../searchGuards";

const fullAddress = {
  city: "Batumi",
  district: "Old Batumi",
  street: "Abashidze",
  building: "12",
};

const validHit = {
  propertyId: "property-1",
  canonicalName: "Orbi Sea Towers",
  type: "RESIDENTIAL_COMPLEX",
  address: fullAddress,
  score: 0.91,
  distanceMeters: 1250,
};

describe("isSearchResponse", () => {
  it("accepts a valid response with a full or null address", () => {
    expect(
      isSearchResponse({ items: [validHit, { ...validHit, propertyId: "property-2", address: null }] }),
    ).toBe(true);
  });

  it("accepts null address parts", () => {
    expect(
      isSearchResponse({
        items: [
          {
            ...validHit,
            address: { city: null, district: "Old Batumi", street: "Abashidze", building: null },
          },
        ],
      }),
    ).toBe(true);
  });

  it.each([
    ["a non-object body", null],
    ["items that are not an array", { items: {} }],
    ["a missing property id", { items: [{ ...validHit, propertyId: undefined }] }],
    ["a missing name", { items: [{ ...validHit, canonicalName: undefined }] }],
    ["a missing type", { items: [{ ...validHit, type: undefined }] }],
    ["a missing address key", { items: [{ ...validHit, address: undefined }] }],
    [
      "an address part with the wrong type",
      { items: [{ ...validHit, address: { ...fullAddress, building: 12 } }] },
    ],
    [
      "a missing address part",
      { items: [{ ...validHit, address: { city: "Batumi", district: null, street: null } }] },
    ],
  ])("rejects %s", (_name, value) => {
    expect(isSearchResponse(value)).toBe(false);
  });
});
