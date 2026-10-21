import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/** The accessory of the demo tab bar (iOS 26): a row above the tabs, as the mini player of a music app. */
export function TabsAccessory() {
  const t = useTheme();
  const [playing, setPlaying] = useState(false);
  return (
    <View style={styles.row}>
      <Text style={[styles.title, { color: t.text }]} numberOfLines={1}>
        Evening playlist
      </Text>
      <Pressable hitSlop={8} onPress={() => setPlaying((value) => !value)}>
        <Text style={[styles.control, { color: t.text }]}>{playing ? '❚❚' : '▶'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, gap: 12 },
  title: { flex: 1, fontSize: 15, fontWeight: '600' },
  control: { fontSize: 17, fontWeight: '700' },
});
