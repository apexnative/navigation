import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/**
 * A title component as a chat shows it: an avatar, a name and a status. With `title.glass` it sits on a capsule of
 * glass; its padding makes the room around it.
 */
export function ChatTitle({
  name = 'Ada Park',
  status = 'online',
}: {
  name?: string;
  status?: string;
}) {
  const t = useTheme();
  return (
    <View style={styles.row}>
      <Image
        source={{
          uri: `https://api.dicebear.com/9.x/notionists/png?seed=${encodeURIComponent(name)}&size=96&backgroundColor=c0aede`,
        }}
        style={styles.avatar}
      />
      <View>
        <Text style={[styles.name, { color: t.text }]}>{name}</Text>
        <Text style={[styles.status, { color: t.textMuted }]}>{status}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingLeft: 5,
    paddingRight: 16,
    paddingVertical: 4,
  },
  avatar: { width: 30, height: 30, borderRadius: 15 },
  name: { fontSize: 15, fontWeight: '600' },
  status: { fontSize: 12 },
});
