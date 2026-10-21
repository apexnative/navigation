import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';
import { avatarUrl, findClub, findPerson, photoUrl } from './data';

/**
 * The title of a post's screen: the club's picture with the author's avatar over its corner, the club's
 * name and "author · time". A title `component` with `alignment: 'fill'` takes the bar from the back button to the
 * buttons on the right, so it starts next to the back button.
 */
export function PostHeader({
  clubId,
  authorId,
  ago,
}: {
  clubId: string;
  authorId: string;
  ago: string;
}) {
  const t = useTheme();
  const club = findClub(clubId);
  const author = findPerson(authorId);
  return (
    <View style={styles.row}>
      <View style={styles.pictures}>
        <Image source={{ uri: photoUrl(club.id, 120, 120) }} style={styles.club} />
        <Image
          source={{ uri: avatarUrl(author.name) }}
          style={[styles.author, { borderColor: t.background }]}
        />
      </View>
      <View style={styles.texts}>
        <Text style={[styles.title, { color: t.text }]} numberOfLines={1}>
          {club.name}
        </Text>
        <Text style={[styles.subtitle, { color: t.textMuted }]} numberOfLines={1}>
          {author.name} · {ago}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10 },
  pictures: { width: 42, height: 40 },
  club: { width: 36, height: 36, borderRadius: 10 },
  author: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
  },
  texts: { flex: 1 },
  title: { fontSize: 15, fontWeight: '600' },
  subtitle: { fontSize: 13, marginTop: 1 },
});
