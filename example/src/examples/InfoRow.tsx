import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/** A fact of an event: a symbol in a tile, a line and a quieter line under it. */
export function InfoRow({
  symbol,
  title,
  detail,
}: {
  symbol: string;
  title: string;
  detail: string;
}) {
  const t = useTheme();
  return (
    <View style={styles.row}>
      <View style={[styles.tile, { backgroundColor: t.primarySoft }]}>
        <Text style={styles.symbol}>{symbol}</Text>
      </View>
      <View style={styles.texts}>
        <Text style={[styles.title, { color: t.text }]}>{title}</Text>
        <Text style={[styles.detail, { color: t.textMuted }]}>{detail}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  tile: { width: 44, height: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  symbol: { fontSize: 20 },
  texts: { flex: 1, gap: 2 },
  title: { fontSize: 16, fontWeight: '600' },
  detail: { fontSize: 14 },
});
