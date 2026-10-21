import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { useTheme } from '../ui';

const ITEMS = ['Home', 'Profile', 'Settings', 'Help'];

/**
 * A menu of the side menu demo. A menu has no stack: a pick acts on the center, and the menu closes once the center
 * changed — Home starts the center over (`replace`), the others push Details over it. On glass (`sideMenu.*.glass`) it
 * has no background of its own, so the glass shows.
 */
function SideMenuMenu({ side, glass }: { side: 'left' | 'right'; glass?: boolean }) {
  const t = useTheme();
  const nav = useNavigation();
  const pick = (item: string) => {
    log('event', `Side menu (${side})`, item);
    if (item === 'Home') nav.replace('SideMenuCenter');
    else nav.push('Details');
  };
  return (
    <View style={[styles.menu, !glass && { backgroundColor: t.card }]}>
      <Text style={[styles.header, { color: t.textMuted }]}>
        {side === 'left' ? 'Left menu' : 'Right menu'}
      </Text>
      {ITEMS.map((item) => (
        <Pressable
          key={item}
          onPress={() => pick(item)}
          style={({ pressed }) => [styles.item, { opacity: pressed ? 0.6 : 1 }]}
        >
          <Text style={[styles.text, { color: t.text }]}>{item}</Text>
        </Pressable>
      ))}
    </View>
  );
}

export const SideMenuMenuScreen = defineScreen({ component: SideMenuMenu });

const styles = StyleSheet.create({
  menu: { flex: 1, paddingTop: 72, paddingHorizontal: 20 },
  header: { fontSize: 13, fontWeight: '700', textTransform: 'uppercase', marginBottom: 12 },
  item: { paddingVertical: 14 },
  text: { fontSize: 18, fontWeight: '600' },
});
