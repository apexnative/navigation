import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@apexnative/navigation';
import { useTheme } from '../ui';

/**
 * A mini player as an overlay: a card at the bottom that stays over every screen, tab and modal while the app goes
 * on. The app takes the touches everywhere but on the card: an overlay lets them through.
 */
export function MiniPlayerOverlay() {
  const t = useTheme();
  const nav = useNavigation();
  const [playing, setPlaying] = useState(true);
  return (
    <View pointerEvents="box-none" style={styles.fill}>
      <View style={[styles.card, { backgroundColor: t.card, borderColor: t.border }]}>
        <Text style={[styles.title, { color: t.text }]} numberOfLines={1}>
          Morning playlist
        </Text>
        <Pressable hitSlop={8} onPress={() => setPlaying((value) => !value)}>
          <Text style={[styles.control, { color: t.primary }]}>{playing ? '❚❚' : '▶'}</Text>
        </Pressable>
        <Pressable hitSlop={8} onPress={() => nav.dismiss()}>
          <Text style={[styles.control, { color: t.textMuted }]}>✕</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, justifyContent: 'flex-end', paddingHorizontal: 16, paddingBottom: 96 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    borderRadius: 20,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  title: { flex: 1, fontSize: 15, fontWeight: '600' },
  control: { fontSize: 18, fontWeight: '700' },
});
