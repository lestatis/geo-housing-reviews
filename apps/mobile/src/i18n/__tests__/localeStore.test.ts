import AsyncStorage from "@react-native-async-storage/async-storage";

import { persistentLocaleStore } from "../localeStore";

// The same key the store uses; spelled out here so a rename has to be deliberate in two places.
const LOCALE_KEY = "geo-housing.locale";

describe("persistent locale override", () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it("round-trips a supported locale", async () => {
    await persistentLocaleStore.save("ru");
    await expect(persistentLocaleStore.load()).resolves.toBe("ru");
    await expect(AsyncStorage.getItem(LOCALE_KEY)).resolves.toBe("ru");
  });

  it("has no preference before one is chosen", async () => {
    await expect(persistentLocaleStore.load()).resolves.toBeUndefined();
  });

  it("ignores a stored value that is not a locale we ship", async () => {
    await AsyncStorage.setItem(LOCALE_KEY, "ka-GE");
    await expect(persistentLocaleStore.load()).resolves.toBeUndefined();
  });
});
