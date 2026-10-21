import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';
import { avatarUrl, people } from './data';

/** Who goes: a few avatars over each other and the count. */
export function Attendees({ count }: { count: number }) {
  const t = useTheme();
  return (
    <View style={styles.row}>
      <View style={styles.faces}>
        {people.map((person, index) => (
          <Image
            key={person.id}
            source={{ uri: avatarUrl(person.name) }}
            style={[styles.face, { borderColor: t.background, marginLeft: index === 0 ? 0 : -10 }]}
          />
        ))}
      </View>
      <Text style={[styles.count, { color: t.textMuted }]}>{count} going</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, marginVertical: 18 },
  faces: { flexDirection: 'row' },
  face: { width: 32, height: 32, borderRadius: 16, borderWidth: 2 },
  count: { fontSize: 14, fontWeight: '500' },
});
