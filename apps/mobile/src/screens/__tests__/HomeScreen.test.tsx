import { fireEvent, render, screen } from "@testing-library/react-native";

import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import HomeScreen from "../HomeScreen";

const mockPush = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ push: mockPush }),
}));

function renderHome(deviceLocale = "en-US") {
  render(
    <LocaleProvider store={createMemoryLocaleStore()} deviceLocale={deviceLocale}>
      <HomeScreen />
    </LocaleProvider>,
  );
}

describe("HomeScreen", () => {
  beforeEach(() => {
    mockPush.mockReset();
  });

  it("renders the anonymous search entry without firing navigation on launch", () => {
    renderHome();

    expect(screen.getByText("Geo Housing Reviews")).toBeTruthy();
    expect(screen.getByText("Find a building. Read what living there is like.")).toBeTruthy();
    expect(screen.getByLabelText("Search for a building")).toBeTruthy();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("navigates to the results route with a trimmed query", () => {
    renderHome();

    fireEvent.changeText(screen.getByTestId("home-search-input"), "  Orbi Sea Towers  ");
    fireEvent.press(screen.getByTestId("home-search-submit"));

    expect(mockPush).toHaveBeenCalledWith("/search?q=Orbi%20Sea%20Towers");
  });

  it("disables submit while the query is blank", () => {
    renderHome();

    const submit = screen.getByTestId("home-search-submit");
    expect(submit.props.accessibilityState).toEqual({ disabled: true });

    fireEvent.press(submit);
    expect(mockPush).not.toHaveBeenCalled();

    fireEvent.changeText(screen.getByTestId("home-search-input"), "   ");
    expect(screen.getByTestId("home-search-submit").props.accessibilityState).toEqual({
      disabled: true,
    });
  });

  it("keeps the persisted-able locale switch reachable", () => {
    renderHome();

    fireEvent.press(screen.getByTestId("locale-ru"));

    expect(screen.getByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
    expect(screen.getByText("Найти")).toBeTruthy();
    expect(screen.queryByText("Find a building. Read what living there is like.")).toBeNull();
  });

  it("gives both locale choices a role, a label and a selected state", () => {
    renderHome();

    const english = screen.getByTestId("locale-en");
    const russian = screen.getByTestId("locale-ru");

    expect(english.props.accessibilityRole).toBe("button");
    expect(english.props.accessibilityState).toEqual({ selected: true });
    expect(english.props.accessibilityLabel).toBe("Switch the interface language to English");

    fireEvent.press(russian);

    expect(screen.getByTestId("locale-ru").props.accessibilityState).toEqual({ selected: true });
    expect(screen.getByTestId("locale-en").props.accessibilityState).toEqual({ selected: false });
    expect(screen.getByTestId("locale-en").props.accessibilityLabel).toBe(
      "Переключить язык интерфейса на English",
    );
  });
});
