import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fonts, useTheme } from '../ui';
import { TILES } from './colors';

/** A row of a list in the Showcase's look: a colored mark, a title, a line under it and a chevron. */
export function Row({
  index,
  title,
  subtitle,
  onPress,
}: {
  index: number;
  title: string;
  subtitle?: string;
  onPress?: () => void;
}) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.row, { backgroundColor: pressed ? t.code : t.card }]}
    >
      <View style={[styles.mark, { backgroundColor: TILES[index % TILES.length] }]} />
      <View style={styles.text}>
        <Text style={[styles.title, { color: t.text }]}>{title}</Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: t.textMuted }]}>{subtitle}</Text>
        ) : null}
      </View>
      <Text style={[styles.chevron, { color: t.chevron }]}>›</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  mark: { width: 34, height: 34, borderRadius: 10 },
  text: { flex: 1, gap: 2 },
  title: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
  subtitle: { fontFamily: fonts.body, fontSize: 13 },
  chevron: { fontSize: 22 },
});
