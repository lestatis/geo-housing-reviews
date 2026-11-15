import { fireEvent, render, screen } from "@testing-library/react-native";

import { LocaleProvider } from "../../i18n/LocaleProvider";
import { createMemoryLocaleStore } from "../../i18n/localeStore";
import PropertyPlaceholderScreen from "../PropertyPlaceholderScreen";

const mockBack = jest.fn();
const mockCanGoBack = jest.fn();
const mockReplace = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack, canGoBack: mockCanGoBack, replace: mockReplace }),
}));

describe("PropertyPlaceholderScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCanGoBack.mockReturnValue(true);
  });

  it("localizes the placeholder and exposes a back action", () => {
    render(
      <LocaleProvider store={createMemoryLocaleStore("ru")} deviceLocale="ru-RU">
        <PropertyPlaceholderScreen />
      </LocaleProvider>,
    );

    expect(screen.getByText("Подробности об объекте появятся позже")).toBeTruthy();
    fireEvent.press(screen.getByLabelText("Назад"));
    expect(mockBack).toHaveBeenCalledTimes(1);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("returns home when a deep link leaves no history to go back to", () => {
    mockCanGoBack.mockReturnValue(false);
    render(
      <LocaleProvider store={createMemoryLocaleStore("en")} deviceLocale="en-US">
        <PropertyPlaceholderScreen />
      </LocaleProvider>,
    );

    fireEvent.press(screen.getByLabelText("Back"));
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).toHaveBeenCalledWith("/");
  });
});
