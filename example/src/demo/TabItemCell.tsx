import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/**
 * The item of a tab of the demo app drawn by a component, in the custom row of section 9: its title, a pill behind it
 * when the tab is selected, and the tab's badge.
 */
export function TabItemCell({
  title,
  selected,
  badge,
}: {
  title: string;
  selected?: boolean;
  badge?: string;
}) {
  const t = useTheme();
  return (
    <View style={styles.cell}>
      <View style={[styles.pill, selected ? { backgroundColor: t.primarySoft } : null]}>
        <Text numberOfLines={1} style={[styles.title, { color: selected ? t.primary : t.text }]}>
          {title}
        </Text>
        {badge ? (
          <View style={[styles.badge, { backgroundColor: t.primary }]}>
            <Text style={styles.badgeText}>{badge}</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  cell: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 18,
  },
  title: { fontSize: 15, fontWeight: '600' },
  badge: { minWidth: 18, borderRadius: 9, paddingHorizontal: 5, alignItems: 'center' },
  badgeText: { color: 'white', fontSize: 11, fontWeight: '700' },
});
