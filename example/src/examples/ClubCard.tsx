import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';
import { photoUrl, type Club } from './data';

/** A club in a row of cards: its picture, name and members. */
export function ClubCard({ club }: { club: Club }) {
  const t = useTheme();
  return (
    <View style={[styles.card, { backgroundColor: t.card, borderColor: t.border }]}>
      <Image source={{ uri: photoUrl(club.id, 400, 240) }} style={styles.cover} />
      <View style={styles.body}>
        <Text style={[styles.name, { color: t.text }]} numberOfLines={2}>
          {club.name}
        </Text>
        <Text style={[styles.members, { color: t.textMuted }]}>
          {club.members.toLocaleString('en-US')} members
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: 180,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  cover: { width: '100%', height: 96 },
  body: { padding: 12, gap: 4 },
  name: { fontSize: 15, fontWeight: '600' },
  members: { fontSize: 13 },
});
