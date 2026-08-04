import { describe, expect, it } from "vitest";
import { describeWindow, windowBounds } from "./window";

describe("the window a screen covers", () => {
  it("defaults to seven inclusive days, which is what the screen says it does", () => {
    // Today and the six before it. Subtracting seven would show eight dated days under a label
    // saying seven — a screen and a window disagreeing about what is being looked at.
    const window = describeWindow(undefined, undefined, new Date("2026-08-04T12:00:00Z"), 7);

    expect(window.since).toBe("2026-07-29");
    expect(window.until).toBe("2026-08-04");
  });

  it("keeps what was asked for", () => {
    const window = describeWindow("2026-07-01", "2026-07-15", new Date("2026-08-04T12:00:00Z"), 7);

    expect(window.since).toBe("2026-07-01");
    expect(window.until).toBe("2026-07-15");
  });

  it("falls back to the default rather than passing on something that is not a date", () => {
    // A rejected query tells the reader nothing; a mangled one tells them something false.
    const window = describeWindow("last tuesday", "2026-13-45", new Date("2026-08-04T12:00:00Z"), 7);

    expect(window.since).toBe("2026-07-29");
    expect(window.until).toBe("2026-08-04");
  });

  it("refuses a day that does not exist, rather than silently moving it", () => {
    // JavaScript rejects a thirteenth month but rolls an overrunning day forward: `2026-02-31`
    // parses as 3 March. Kept, it would label the screen "2026-02-31" while asking the API about a
    // window ending in March — a window that says one thing and fetches another.
    const window = describeWindow("2026-02-31", "2026-04-31", new Date("2026-08-04T12:00:00Z"), 7);

    expect(window.since).toBe("2026-07-29");
    expect(window.until).toBe("2026-08-04");
    // A real leap day is still a real date.
    expect(describeWindow("2024-02-29", undefined, new Date("2026-08-04T12:00:00Z"), 7).since).toBe(
      "2024-02-29",
    );
  });
});

describe("translating that window for the API", () => {
  it("ends at the next midnight, so the last second of the chosen day is inside it", () => {
    // The regression this exists for: the screen sent 23:59:59Z against SQL asking for
    // created_at < :until, so anything recorded in the final fraction of the day vanished from an
    // audit log — the one place a missing entry matters most.
    const bounds = windowBounds({ since: "2026-08-01", until: "2026-08-04" });

    expect(bounds.since).toBe("2026-08-01T00:00:00Z");
    expect(new Date("2026-08-04T23:59:59.500Z") < new Date(bounds.until)).toBe(true);
    expect(new Date("2026-08-05T00:00:00.000Z") < new Date(bounds.until)).toBe(false);
  });

  it("crosses a month and a year boundary", () => {
    expect(windowBounds({ since: "2026-01-01", until: "2026-01-31" }).until).toBe(
      "2026-02-01T00:00:00.000Z",
    );
    expect(windowBounds({ since: "2026-01-01", until: "2026-12-31" }).until).toBe(
      "2027-01-01T00:00:00.000Z",
    );
  });

  it("clamps at the end of representable time rather than emitting a five-digit year", () => {
    // Nothing can be recorded after it, so the clamp loses nothing — and an expanded ISO year is
    // something the API would refuse to parse.
    expect(windowBounds({ since: "9999-12-31", until: "9999-12-31" }).until).toBe(
      "9999-12-31T23:59:59.999Z",
    );
  });
});

describe("a screen's own default span", () => {
  it("is the caller's, because seven days and thirty are different questions", () => {
    // The counting rule is shared; the span is not. The audit screen asks about a week, the
    // metrics screen about a month, and a single constant would make one of them lie.
    const week = describeWindow(undefined, undefined, new Date("2026-08-04T12:00:00Z"), 7);
    const month = describeWindow(undefined, undefined, new Date("2026-08-04T12:00:00Z"), 30);

    expect(week.since).toBe("2026-07-29");
    expect(month.since).toBe("2026-07-06");
    expect(week.until).toBe(month.until);
  });
});
