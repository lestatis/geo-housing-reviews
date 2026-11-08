import type { MobileApiClient } from "../client";
import { isPropertySearchResponse, searchProperties, SEARCH_LIMIT } from "../search";

function stubClient(get: jest.Mock): MobileApiClient {
  return { baseUrl: "http://example.test", timeoutMs: 1000, get } as unknown as MobileApiClient;
}

describe("searchProperties", () => {
  it("asks for one bounded page of the catalogue with the query it was given", async () => {
    const get = jest.fn().mockResolvedValue({ outcome: "ok", data: { items: [] } });

    const result = await searchProperties(stubClient(get), "Orbi");

    expect(result).toEqual({ outcome: "ok", data: { items: [] } });
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/api/properties/search", isPropertySearchResponse, {
      params: { query: { q: "Orbi", limit: SEARCH_LIMIT } },
    });
  });

  it("carries the failure outcome through unchanged", async () => {
    const get = jest.fn().mockResolvedValue({ outcome: "offline" });

    await expect(searchProperties(stubClient(get), "Orbi")).resolves.toEqual({
      outcome: "offline",
    });
  });
});

describe("isPropertySearchResponse", () => {
  it("accepts a hit with a full or a partial address, and one with none", () => {
    expect(
      isPropertySearchResponse({
        items: [
          {
            propertyId: "p1",
            canonicalName: "Orbi",
            type: "BUILDING",
            address: { city: "Batumi", district: "Old Batumi", street: "A", building: "1" },
          },
          { propertyId: "p2", canonicalName: "Orbi", type: "BUILDING", address: { city: "Batumi" } },
          { propertyId: "p3", canonicalName: "Orbi" },
        ],
      }),
    ).toBe(true);
  });

  it("accepts the JSON nulls the backend sends for an unrecorded address or address part", () => {
    // Regression: Spring serializes absent values as null, not by omitting the key. A guard that
    // only allowed a missing key turned every such search into `malformed`.
    const body: unknown = JSON.parse(`{
      "items": [
        { "propertyId": "p1", "canonicalName": "Orbi", "type": "BUILDING",
          "address": { "city": "Batumi", "district": null, "street": "A", "building": null },
          "score": 0.9, "distanceMeters": null },
        { "propertyId": "p2", "canonicalName": "Orbi", "type": "BUILDING",
          "address": null, "score": 0.8, "distanceMeters": null },
        { "propertyId": "p3", "canonicalName": "Orbi", "type": "BUILDING",
          "address": { "city": null, "district": null, "street": null, "building": null },
          "score": 0.7, "distanceMeters": 1250.0 }
      ]
    }`);

    expect(isPropertySearchResponse(body)).toBe(true);
  });

  it("still rejects a null type, which the backend never sends", () => {
    const body: unknown = JSON.parse(
      `{ "items": [{ "propertyId": "p1", "canonicalName": "Orbi", "type": null, "address": null }] }`,
    );

    expect(isPropertySearchResponse(body)).toBe(false);
  });

  it("rejects a body that is not the search response at all", () => {
    expect(isPropertySearchResponse(null)).toBe(false);
    expect(isPropertySearchResponse({ items: "nope" })).toBe(false);
    expect(isPropertySearchResponse({})).toBe(false);
  });

  it("rejects an item whose shape a result row cannot read", () => {
    expect(isPropertySearchResponse({ items: [{ canonicalName: "Orbi" }] })).toBe(false);
    expect(isPropertySearchResponse({ items: [{ propertyId: "p1" }] })).toBe(false);
    expect(
      isPropertySearchResponse({
        items: [{ propertyId: "p1", canonicalName: "Orbi", type: 7 }],
      }),
    ).toBe(false);
    expect(
      isPropertySearchResponse({
        items: [{ propertyId: "p1", canonicalName: "Orbi", address: { city: 7 } }],
      }),
    ).toBe(false);
  });
});
