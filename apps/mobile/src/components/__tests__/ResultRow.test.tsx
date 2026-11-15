import { fireEvent, render, screen } from "@testing-library/react-native";

import type { SearchHit } from "../../api/searchGuards";
import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import { ResultRow } from "../ResultRow";

const hit: SearchHit = {
  propertyId: "property-1",
  canonicalName: "Abashidze 12",
  type: "BUILDING",
  address: { city: "Batumi", district: "Old Batumi", street: "Abashidze", building: "12" },
  score: 0.98,
  distanceMeters: 4321,
};

function renderRow(value: SearchHit = hit) {
  const onOpen = jest.fn();
  render(
    <LocaleProvider store={createMemoryLocaleStore()} deviceLocale="en-US">
      <ResultRow hit={value} onOpen={onOpen} />
    </LocaleProvider>,
  );
  return onOpen;
}

describe("ResultRow", () => {
  it("renders name, address and translated type with an accessible label", () => {
    renderRow();

    expect(screen.getByText("Abashidze 12")).toBeTruthy();
    expect(screen.getByText("Abashidze 12, Old Batumi, Batumi")).toBeTruthy();
    expect(screen.getByText("Building")).toBeTruthy();
    expect(
      screen.getByLabelText(
        "Abashidze 12, Abashidze 12, Old Batumi, Batumi, Building. Opens details.",
      ),
    ).toBeTruthy();
  });

  it("omits the address sentence when the property has no address", () => {
    renderRow({ ...hit, address: null });

    expect(screen.getByLabelText("Abashidze 12, Building. Opens details.")).toBeTruthy();
    expect(screen.queryByText("Abashidze 12, Old Batumi, Batumi")).toBeNull();
  });

  it("uses a generic label for an unrecognized property type", () => {
    renderRow({ ...hit, type: "FUTURE_TYPE" });

    expect(screen.getByText("Property")).toBeTruthy();
  });

  it("opens the property route and never renders score or distance", () => {
    const onOpen = renderRow();

    fireEvent.press(screen.getByTestId("result-property-1"));
    expect(onOpen).toHaveBeenCalledWith("property-1");
    expect(JSON.stringify(screen.toJSON())).not.toContain("0.98");
    expect(JSON.stringify(screen.toJSON())).not.toContain("4321");
  });
});
