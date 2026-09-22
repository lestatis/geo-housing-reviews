import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Text, TouchableOpacity } from "react-native";

import { LocaleProvider, useMessages } from "../LocaleProvider";
import { createMemoryLocaleStore, type LocalePreferenceStore } from "../localeStore";

function Probe() {
  const { locale, setLocale, t, count, date } = useMessages();
  return (
    <>
      <Text>{t("home.tagline")}</Text>
      <Text>{locale}</Text>
      <Text>{count("reviews.count", 2)}</Text>
      <Text>{date(new Date(2026, 8, 1))}</Text>
      <TouchableOpacity accessibilityRole="button" accessibilityLabel="switch" onPress={() => setLocale("ru")}>
        <Text>switch</Text>
      </TouchableOpacity>
    </>
  );
}

function renderProbe(store: LocalePreferenceStore, deviceLocale?: string) {
  render(
    <LocaleProvider store={store} deviceLocale={deviceLocale}>
      <Probe />
    </LocaleProvider>,
  );
}

describe("LocaleProvider", () => {
  it("starts from the device locale", async () => {
    renderProbe(createMemoryLocaleStore(), "ru-RU");
    expect(await screen.findByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
    expect(screen.getByText("ru")).toBeTruthy();
  });

  it("treats an unsupported device locale as English", () => {
    renderProbe(createMemoryLocaleStore(), "ka-GE");
    expect(screen.getByText("Find a building. Read what living there is like.")).toBeTruthy();
    expect(screen.getByText("en")).toBeTruthy();
  });

  it("prefers a stored override over the device locale", async () => {
    renderProbe(createMemoryLocaleStore("ru"), "en-US");
    expect(await screen.findByText("ru")).toBeTruthy();
    expect(screen.getByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
  });

  it("changes the visible string when the locale is switched", async () => {
    renderProbe(createMemoryLocaleStore(), "en-US");
    expect(screen.getByText("Find a building. Read what living there is like.")).toBeTruthy();

    fireEvent.press(screen.getByLabelText("switch"));

    expect(await screen.findByText("Найдите дом. Узнайте, каково там жить.")).toBeTruthy();
    expect(screen.queryByText("Find a building. Read what living there is like.")).toBeNull();
    // The switch is local: it changes plural and date rendering with it.
    expect(screen.getByText("2 отзыва")).toBeTruthy();
    expect(screen.getByText(/^1 сентября 2026/)).toBeTruthy();
  });

  it("persists the manual override on the device", async () => {
    const store = createMemoryLocaleStore();
    renderProbe(store, "en-US");

    fireEvent.press(screen.getByLabelText("switch"));

    await waitFor(async () => {
      await expect(store.load()).resolves.toBe("ru");
    });
    expect(screen.getByText("ru")).toBeTruthy();
  });

  it("refuses to be used outside a provider", () => {
    // A screen rendered without the provider is a wiring bug; failing loudly beats rendering blanks.
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => {});
    try {
      expect(() => render(<Probe />)).toThrow("useMessages must be used inside LocaleProvider");
    } finally {
      consoleError.mockRestore();
    }
  });
});
