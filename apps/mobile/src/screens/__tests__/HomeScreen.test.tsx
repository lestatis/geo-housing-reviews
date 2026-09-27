import { fireEvent, render, screen } from "@testing-library/react-native";

import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import HomeScreen from "../HomeScreen";

function renderHome(onSearch = jest.fn(), deviceLocale = "en-US") {
  render(
    <LocaleProvider store={createMemoryLocaleStore()} deviceLocale={deviceLocale}>
      <HomeScreen onSearch={onSearch} />
    </LocaleProvider>,
  );
  return onSearch;
}

describe("HomeScreen", () => {
  it("states what the app is for and offers one search field", () => {
    renderHome();

    expect(screen.getByText("Geo Housing Reviews")).toBeTruthy();
    expect(screen.getByText("Find a building. Read what living there is like.")).toBeTruthy();
    expect(screen.getByTestId("home-search-input")).toBeTruthy();
    expect(screen.getByTestId("home-search-submit")).toBeTruthy();
  });

  it("searches for the trimmed query from the button", () => {
    const onSearch = renderHome();

    fireEvent.changeText(screen.getByTestId("home-search-input"), "  Orbi  ");
    fireEvent.press(screen.getByTestId("home-search-submit"));

    expect(onSearch).toHaveBeenCalledWith("Orbi");
  });

  it("searches from the keyboard's search key too", () => {
    const onSearch = renderHome();

    fireEvent.changeText(screen.getByTestId("home-search-input"), "Abashidze");
    fireEvent(screen.getByTestId("home-search-input"), "submitEditing");

    expect(onSearch).toHaveBeenCalledWith("Abashidze");
  });

  it("never searches for nothing", () => {
    // The API refuses a search with neither text nor a point, and the app has no location UI.
    const onSearch = renderHome();
    const button = () => screen.getByTestId("home-search-submit");

    expect(button().props.accessibilityState).toEqual({ disabled: true });

    fireEvent.changeText(screen.getByTestId("home-search-input"), "   ");
    fireEvent.press(button());
    fireEvent(screen.getByTestId("home-search-input"), "submitEditing");

    expect(onSearch).not.toHaveBeenCalled();

    fireEvent.changeText(screen.getByTestId("home-search-input"), "Orbi");
    expect(button().props.accessibilityState).toEqual({ disabled: false });
  });

  it("labels the search field and button for assistive technology", () => {
    renderHome();

    expect(screen.getByTestId("home-search-input").props.accessibilityLabel).toBe(
      "Search the catalogue",
    );
    expect(screen.getByTestId("home-search-submit").props.accessibilityRole).toBe("button");
    expect(screen.getByTestId("home-search-submit").props.accessibilityLabel).toBe("Search");
  });

  it("changes a visible string when the locale is switched to Russian", () => {
    renderHome();

    fireEvent.press(screen.getByTestId("locale-ru"));

    expect(screen.getByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
    expect(screen.getByText("Найти")).toBeTruthy();
    expect(screen.queryByText("Find a building. Read what living there is like.")).toBeNull();
  });
});
