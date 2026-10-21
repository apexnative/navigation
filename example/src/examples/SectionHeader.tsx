import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/** The header of a part of a screen, with a "More" link on the right. */
export function SectionHeader({ title, onMore }: { title: string; onMore?: () => void }) {
  const t = useTheme();
  return (
    <View style={styles.row}>
      <Text style={[styles.title, { color: t.text }]}>{title}</Text>
      {onMore ? (
        <Pressable accessibilityRole="link" hitSlop={8} onPress={onMore}>
          <Text style={[styles.link, { color: t.info }]}>More</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    marginTop: 8,
    marginBottom: 12,
  },
  title: { fontSize: 22, fontWeight: '700' },
  link: { fontSize: 16 },
});
