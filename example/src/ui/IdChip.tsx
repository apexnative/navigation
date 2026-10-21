import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** The number of a scenario, in a coral pill. */
export function IdChip({ id }: { id: string | number }) {
  const t = useTheme();
  return (
    <View style={[styles.chip, { backgroundColor: t.pill }]}>
      <Text style={[styles.text, { color: t.pillText }]}>{id}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    minWidth: 38,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 4,
    alignItems: 'center',
  },
  text: { fontFamily: fonts.mono, fontSize: 12 },
});
