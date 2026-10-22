import React from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation, useScreenEvent } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { useTheme } from '../ui';

const ITEMS = ['Home', 'Profile', 'Settings', 'Help'];

const SIZES: { title: string; size: { width?: number; height?: number }; android?: boolean }[] = [
  { title: 'Width 200', size: { width: 200 } },
  { title: 'Width 320.5', size: { width: 320.5 } },
  { title: 'Height 400', size: { height: 400 }, android: true },
  { title: 'Height 600', size: { height: 600 }, android: true },
];

/**
 * A menu of the side menu demo. A menu has no stack: a pick acts on the center, and the menu closes once the center
 * changed — Home starts the center over (`replace`), the others push Details over it. The size buttons change the
 * menu's `width` and `height` while it is open. Its WillAppear goes to the Journal, before the DidAppear every screen
 * logs. Its color is the screen's `layout.backgroundColor` (`openSideMenuDemo`), which fills the whole panel, behind
 * the status and navigation bars; this view stays in the safe area.
 */
function SideMenuMenu({ side }: { side: 'left' | 'right' }) {
  const t = useTheme();
  const nav = useNavigation();
  useScreenEvent('screenWillAppear', ({ screen }) =>
    log('event', 'screenWillAppear', `${screen.name} ${screen.id}`)
  );
  const pick = (item: string) => {
    log('event', `Side menu (${side})`, item);
    if (item === 'Home') nav.replace('SideMenuCenter');
    else nav.push('Details');
  };
  return (
    <View style={styles.menu}>
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
      {SIZES.filter((item) => !item.android || Platform.OS === 'android').map((item) => (
        <Pressable
          key={item.title}
          onPress={() => nav.setOptions({ sideMenu: { [side]: item.size } })}
          style={({ pressed }) => [styles.size, { opacity: pressed ? 0.6 : 1 }]}
        >
          <Text style={[styles.sizeText, { color: t.textMuted }]}>{item.title}</Text>
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
  size: { paddingVertical: 8 },
  sizeText: { fontSize: 15 },
});
