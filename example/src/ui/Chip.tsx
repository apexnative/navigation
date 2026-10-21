import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { fonts } from './fonts';
import { brand, useTheme } from './theme';

/** A choice among others in a row; the selected one is gold. */
export function Chip({
  label,
  selected,
  onPress,
  code = false,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
  /** The label is a value of code. */
  code?: boolean;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? brand.mustard : 'transparent',
          borderColor: selected ? brand.mustard : t.outline,
          opacity: pressed ? 0.7 : 1,
        },
      ]}
    >
      <Text
        style={[
          code ? styles.code : styles.label,
          { color: selected ? brand.deep : t.dark ? t.text : t.body },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: 32,
    borderRadius: 999,
    borderWidth: 2,
    paddingHorizontal: 12,
    justifyContent: 'center',
  },
  label: { fontFamily: fonts.bodyMedium, fontSize: 13 },
  code: { fontFamily: fonts.mono, fontSize: 12 },
});
