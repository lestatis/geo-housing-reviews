import { StyleSheet, Text, TouchableOpacity, View } from "react-native";

import type { SearchHit } from "../api/searchGuards";
import type { MessageKey } from "../i18n/en";
import { useMessages } from "../i18n/LocaleProvider";
import { formatAddressSummary } from "../search/formatAddressSummary";
import { MIN_TOUCH_TARGET, theme } from "../theme";

const PROPERTY_TYPE_KEYS: Record<string, MessageKey> = {
  BUILDING: "propertyType.building",
  RESIDENTIAL_COMPLEX: "propertyType.residentialComplex",
  BLOCK: "propertyType.block",
  PHASE: "propertyType.phase",
};

export interface ResultRowProps {
  hit: SearchHit;
  onOpen(propertyId: string): void;
}

export function ResultRow({ hit, onOpen }: ResultRowProps) {
  const { t } = useMessages();
  const propertyId = hit.propertyId;
  const name = hit.canonicalName ?? "";
  const address = formatAddressSummary(hit.address);
  const propertyType = t(
    (hit.type ? PROPERTY_TYPE_KEYS[hit.type] : undefined) ?? "propertyType.unknown",
  );
  const accessibilityLabel = address
    ? t("results.rowAccessibilityLabel", { name, address, type: propertyType })
    : t("results.rowAccessibilityLabelNoAddress", { name, type: propertyType });

  if (!propertyId) {
    return null;
  }

  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={() => onOpen(propertyId)}
      style={styles.row}
      testID={`result-${propertyId}`}
    >
      <View style={styles.copy}>
        <Text style={styles.name}>{name}</Text>
        {address ? <Text style={styles.address}>{address}</Text> : null}
        <Text style={styles.type}>{propertyType}</Text>
      </View>
      <Text accessibilityElementsHidden importantForAccessibility="no" style={styles.chevron}>
        ›
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: "center",
    backgroundColor: theme.surface,
    borderColor: theme.border,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    gap: 12,
    minHeight: MIN_TOUCH_TARGET,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  copy: {
    flex: 1,
    gap: 4,
  },
  name: {
    color: theme.text,
    fontSize: 17,
    fontWeight: "600",
    lineHeight: 23,
  },
  address: {
    color: theme.mutedText,
    fontSize: 15,
    lineHeight: 21,
  },
  type: {
    color: theme.accent,
    fontSize: 14,
    lineHeight: 20,
  },
  chevron: {
    color: theme.mutedText,
    fontSize: 26,
    lineHeight: 30,
  },
});
