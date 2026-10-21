import React from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useSafeAreaInsets } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { fonts } from '../../ui';

export interface MiniPlayerProps {
  /** Shown as an overlay: a bar of its own above the tabs. Without it, it fills the accessory of the tabs. */
  overlay?: boolean;
}

/** "Now playing" with a play button: an overlay over the tabs, or the accessory of the iOS 26 tab bar. */
function MiniPlayer({ overlay = false }: MiniPlayerProps) {
  const c = useColors();
  const insets = useSafeAreaInsets();
  const row = (
    <View style={[styles.row, overlay && [styles.raised, { backgroundColor: c.card }]]}>
      <View style={[styles.cover, { backgroundColor: TILES[3] }]} />
      <View style={styles.text}>
        <Text style={[styles.title, { color: c.text }]}>Now playing</Text>
        <Text style={[styles.subtitle, { color: c.muted }]}>Night sky</Text>
      </View>
      <Text style={[styles.play, { color: c.text }]}>▶</Text>
    </View>
  );
  if (!overlay) return row;
  // Above the tab bar: the rest of the overlay lets the touches through.
  const tabBar = Platform.OS === 'ios' ? 34 : 80;
  return (
    <View
      pointerEvents="box-none"
      style={[styles.overlay, { paddingBottom: insets.bottom + tabBar }]}
    >
      {row}
    </View>
  );
}

export const MiniPlayerScreen = defineScreen({ component: MiniPlayer });

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 12 },
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14 },
  raised: {
    flex: 0,
    borderRadius: 16,
    paddingVertical: 10,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  cover: { width: 34, height: 34, borderRadius: 8 },
  text: { flex: 1 },
  title: { fontFamily: fonts.bodySemiBold, fontSize: 14 },
  subtitle: { fontFamily: fonts.body, fontSize: 12 },
  play: { fontSize: 18, paddingHorizontal: 6 },
});
