import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';
import { findClub, photoUrl, type Event } from './data';

/** An event: its picture with the date over it, its title, time and club. */
export function EventCard({ event, onPress }: { event: Event; onPress: () => void }) {
  const t = useTheme();
  const club = findClub(event.clubId);
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: t.card, borderColor: t.border, opacity: pressed ? 0.7 : 1 },
      ]}
    >
      <Image source={{ uri: photoUrl(event.id, 520, 300) }} style={styles.cover} />
      <View style={[styles.date, { backgroundColor: t.card }]}>
        <Text style={[styles.day, { color: t.text }]}>{event.day}</Text>
        <Text style={[styles.month, { color: t.danger }]}>{event.month}</Text>
      </View>
      <View style={styles.body}>
        <Text style={[styles.title, { color: t.text }]} numberOfLines={2}>
          {event.title}
        </Text>
        <Text style={[styles.meta, { color: t.textMuted }]} numberOfLines={1}>
          {event.time} · {club.name}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: 260,
    borderRadius: 18,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  cover: { width: '100%', height: 130 },
  date: {
    position: 'absolute',
    top: 10,
    left: 10,
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 4,
    alignItems: 'center',
  },
  day: { fontSize: 18, fontWeight: '800' },
  month: { fontSize: 11, fontWeight: '700' },
  body: { padding: 12, gap: 4 },
  title: { fontSize: 16, fontWeight: '600' },
  meta: { fontSize: 13 },
});
