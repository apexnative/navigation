import React, { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** A label and its value. */
export function KeyValue({ label, value }: { label: string; value: ReactNode }) {
  const t = useTheme();
  return (
    <View style={[styles.row, { borderBottomColor: t.divider }]}>
      <Text style={[styles.label, { color: t.text }]}>{label}</Text>
      <Text style={[styles.value, { color: t.textMuted }]} selectable>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    paddingVertical: 8,
    borderBottomWidth: 1,
    gap: 12,
  },
  label: { fontFamily: fonts.body, fontSize: 14.5 },
  value: { fontFamily: fonts.mono, fontSize: 13, flexShrink: 1, textAlign: 'right' },
});
