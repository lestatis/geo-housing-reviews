import { fireEvent, render, screen } from "@testing-library/react-native";

import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import HomeScreen from "../HomeScreen";

function renderHome(deviceLocale = "en-US") {
  render(
    <LocaleProvider store={createMemoryLocaleStore()} deviceLocale={deviceLocale}>
      <HomeScreen />
    </LocaleProvider>,
  );
}

describe("HomeScreen", () => {
  it("renders the smoke screen in English", () => {
    renderHome();

    expect(screen.getByText("Geo Housing Reviews")).toBeTruthy();
    expect(screen.getByText("Find a building. Read what living there is like.")).toBeTruthy();
    expect(screen.getByText("Review count forms: 1 review · 2 reviews · 5 reviews")).toBeTruthy();
  });

  it("changes a visible string when the locale is switched to Russian", () => {
    renderHome();

    fireEvent.press(screen.getByTestId("locale-ru"));

    expect(screen.getByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
    expect(screen.queryByText("Find a building. Read what living there is like.")).toBeNull();
  });

  it("shows the Russian 1/2/5 plural forms and a Russian date", () => {
    renderHome();

    fireEvent.press(screen.getByTestId("locale-ru"));

    expect(screen.getByText("Формы количества отзывов: 1 отзыв · 2 отзыва · 5 отзывов")).toBeTruthy();
    expect(screen.getByText(/^Формат даты: 1 сентября 2026/)).toBeTruthy();
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
