import type { PropertySearchResponse } from "../../api/search";
import { formatAddressSummary, propertyTypeKey, toSearchRows } from "../searchRows";

describe("formatAddressSummary", () => {
  it("reads street and building, then district, then city", () => {
    expect(
      formatAddressSummary({
        city: "Batumi",
        district: "Old Batumi",
        street: "Chavchavadze Avenue",
        building: "12",
      }),
    ).toBe("Chavchavadze Avenue 12, Old Batumi, Batumi");
  });

  it("omits blanks instead of leaving stray separators", () => {
    // A partial address is common; the row must not read "Chavchavadze Avenue , , Batumi".
    expect(formatAddressSummary({ city: "Batumi", street: "Chavchavadze Avenue" })).toBe(
      "Chavchavadze Avenue, Batumi",
    );
    expect(formatAddressSummary({ district: "Old Batumi" })).toBe("Old Batumi");
  });

  it("treats whitespace-only parts as absent", () => {
    expect(formatAddressSummary({ city: "Batumi", street: "  " })).toBe("Batumi");
  });

  it("is null when the property has no address and when every part is blank", () => {
    expect(formatAddressSummary(undefined)).toBeNull();
    expect(formatAddressSummary({})).toBeNull();
  });

  it("treats JSON-null address parts, and a null address, as missing", () => {
    expect(
      formatAddressSummary({ city: "Batumi", district: null, street: "Gorgiladze", building: null }),
    ).toBe("Gorgiladze, Batumi");
    expect(formatAddressSummary({ city: null, district: null, street: null, building: null })).toBeNull();
    expect(formatAddressSummary(null)).toBeNull();
  });
});

describe("toSearchRows", () => {
  it("keeps name, address and type and drops the ranking fields", () => {
    const rows = toSearchRows({
      items: [
        {
          propertyId: "p1",
          canonicalName: "Orbi Sea Towers Residence",
          type: "RESIDENTIAL_COMPLEX",
          address: { city: "Batumi", district: "Old Batumi" },
          score: 0.87,
          distanceMeters: 1250,
        },
      ],
    });

    expect(rows).toEqual([
      {
        propertyId: "p1",
        name: "Orbi Sea Towers Residence",
        address: "Old Batumi, Batumi",
        type: "RESIDENTIAL_COMPLEX",
      },
    ]);
    expect(Object.keys(rows[0])).not.toContain("score");
    expect(Object.keys(rows[0])).not.toContain("distanceMeters");
  });

  it("distinguishes two buildings that share a name by their address", () => {
    const rows = toSearchRows({
      items: [
        { propertyId: "a", canonicalName: "Sunset Towers", address: { district: "Old Batumi" } },
        { propertyId: "b", canonicalName: "Sunset Towers", address: { district: "New Boulevard" } },
      ],
    });

    expect(rows.map((row) => `${row.name} — ${row.address}`)).toEqual([
      "Sunset Towers — Old Batumi",
      "Sunset Towers — New Boulevard",
    ]);
  });

  it("maps the null-bearing shape the backend actually sends", () => {
    const response = JSON.parse(`{
      "items": [
        { "propertyId": "a", "canonicalName": "Orbi", "type": "BUILDING",
          "address": { "city": "Batumi", "district": null, "street": "Gorgiladze", "building": null },
          "score": 0.9, "distanceMeters": null },
        { "propertyId": "b", "canonicalName": "Draft Tower", "type": "BUILDING",
          "address": null, "score": 0.5, "distanceMeters": null }
      ]
    }`) as PropertySearchResponse;

    expect(toSearchRows(response)).toEqual([
      { propertyId: "a", name: "Orbi", address: "Gorgiladze, Batumi", type: "BUILDING" },
      { propertyId: "b", name: "Draft Tower", address: null, type: "BUILDING" },
    ]);
  });

  it("tolerates a response with no items array", () => {
    expect(toSearchRows({})).toEqual([]);
  });

  it("leaves a missing address and type absent rather than inventing them", () => {
    expect(toSearchRows({ items: [{ propertyId: "a", canonicalName: "Draft Tower" }] })).toEqual([
      { propertyId: "a", name: "Draft Tower", address: null, type: null },
    ]);
  });
});

describe("propertyTypeKey", () => {
  it("translates every type the contract serves", () => {
    expect(propertyTypeKey("BUILDING")).toBe("propertyType.building");
    expect(propertyTypeKey("RESIDENTIAL_COMPLEX")).toBe("propertyType.residentialComplex");
    expect(propertyTypeKey("BLOCK")).toBe("propertyType.block");
    expect(propertyTypeKey("PHASE")).toBe("propertyType.phase");
  });

  it("has no label for nothing or for a value it does not know", () => {
    // An unknown type is shown as "not recorded" rather than printed raw on a phone.
    expect(propertyTypeKey(null)).toBeUndefined();
    expect(propertyTypeKey("SOMETHING_NEW")).toBeUndefined();
  });
});
