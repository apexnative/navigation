import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { brand, rimColor, useTheme } from './theme';

/** The number of a section in a porthole: its rim has the color of the section. */
export function SectionBadge({ id, size = 34 }: { id: number; size?: number }) {
  const t = useTheme();
  return (
    <View
      style={[
        styles.badge,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          borderColor: rimColor(id),
          backgroundColor: t.dark ? brand.deep : brand.navy,
        },
      ]}
    >
      <Text style={[styles.text, { fontSize: Math.round(size * 0.38) }]}>{id}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: { borderWidth: 3, alignItems: 'center', justifyContent: 'center' },
  text: { fontFamily: fonts.mono, color: brand.cream },
});
