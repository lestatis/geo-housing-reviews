import { describe, expect, it } from "vitest";
import {
  type Property,
  type SearchHit,
  canActivate,
  canHide,
  toPropertyRow,
  toSearchRow,
} from "./property";

const HIT: SearchHit = {
  propertyId: "cc440000-0000-4000-8000-000000000001",
  canonicalName: "Orbi Sea Towers Residence",
  score: 0.87,
  distanceMeters: 1250,
};

const PROPERTY: Property = {
  propertyId: "cc440000-0000-4000-8000-000000000001",
  canonicalName: "Orbi Sea Towers Residence",
  type: "BUILDING",
  status: "DRAFT",
  version: 3,
  address: { city: "Batumi", street: "Chavchavadze Avenue" },
};

describe("a search hit", () => {
  it("says what was found, without pretending to know its status", () => {
    // The search response carries no status and no version — an admin action needs both, so the
    // row links to the property rather than offering a button it could not fill in.
    const row = toSearchRow(HIT);

    expect(row.name).toBe("Orbi Sea Towers Residence");
    expect(row.propertyId).toBe("cc440000-0000-4000-8000-000000000001");
    expect(row.distance).toBe("1.3 km");
  });

  it("shows a distance only when the search had somewhere to measure from", () => {
    expect(toSearchRow({ ...HIT, distanceMeters: 240 }).distance).toBe("240 m");
    expect(toSearchRow({ ...HIT, distanceMeters: undefined }).distance).toBe("—");
  });

  it("copes with a hit the API described only partially", () => {
    expect(() => toSearchRow({})).not.toThrow();
    expect(toSearchRow({}).name).toBe("—");
  });
});

describe("a property an administrator is about to act on", () => {
  it("carries the version, because a lifecycle action must name what it saw", () => {
    const row = toPropertyRow(PROPERTY);

    expect(row.status).toBe("Draft");
    expect(row.version).toBe(3);
    expect(row.address).toBe("Chavchavadze Avenue, Batumi");
  });

  it("says where a merged record points, so nobody edits a tombstone by accident", () => {
    const merged = toPropertyRow({
      ...PROPERTY,
      status: "MERGED",
      mergedIntoPropertyId: "dd550000-0000-4000-8000-000000000002",
    });

    expect(merged.status).toBe("Merged");
    expect(merged.mergedInto).toBe("dd550000-0000-4000-8000-000000000002");
  });

  it("copes with a property the API described only partially", () => {
    expect(() => toPropertyRow({})).not.toThrow();
    expect(toPropertyRow({}).address).toBe("—");
    expect(toPropertyRow({}).version).toBe(0);
  });
});

describe("which lifecycle actions make sense", () => {
  it("offers activation only to something not already active", () => {
    // Offering an action the API will refuse teaches an administrator to ignore the buttons.
    expect(canActivate("DRAFT")).toBe(true);
    expect(canActivate("HIDDEN")).toBe(true);
    expect(canActivate("ACTIVE")).toBe(false);
    expect(canActivate("MERGED")).toBe(false);
  });

  it("offers hiding to anything still visible", () => {
    expect(canHide("DRAFT")).toBe(true);
    expect(canHide("ACTIVE")).toBe(true);
    expect(canHide("HIDDEN")).toBe(false);
    // A merged record is already superseded; hiding it would only obscure where it points.
    expect(canHide("MERGED")).toBe(false);
  });
});
