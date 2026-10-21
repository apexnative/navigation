import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useTheme } from './theme';

/** A color to pick; `transparent` shows as a crossed circle. */
export function Swatch({
  color,
  selected,
  onPress,
}: {
  color: string;
  selected: boolean;
  onPress: () => void;
}) {
  const t = useTheme();
  const transparent = color === 'transparent';
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={color}
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[
        styles.swatch,
        {
          backgroundColor: transparent ? t.card : color,
          borderColor: selected ? t.primary : t.text,
          borderWidth: selected ? 3 : 2,
        },
      ]}
    >
      {transparent ? <Text style={[styles.cross, { color: t.textMuted }]}>×</Text> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  swatch: {
    width: 32,
    height: 32,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cross: { fontSize: 18, lineHeight: 20 },
});
