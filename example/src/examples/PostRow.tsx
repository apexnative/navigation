import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';
import { findClub, findPerson, photoUrl, type Post } from './data';

/** A post in a feed: where and when, its title, the start of its text, likes and comments. */
export function PostRow({ post, onPress }: { post: Post; onPress: () => void }) {
  const t = useTheme();
  const club = findClub(post.clubId);
  const author = findPerson(post.authorId);
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: t.card, borderColor: t.border, opacity: pressed ? 0.7 : 1 },
      ]}
    >
      <View style={styles.where}>
        <Image source={{ uri: photoUrl(club.id, 80, 80) }} style={styles.picture} />
        <Text style={[styles.meta, { color: t.textMuted }]} numberOfLines={1}>
          {club.name} · {author.name} · {post.ago}
        </Text>
      </View>
      <Text style={[styles.title, { color: t.text }]}>{post.title}</Text>
      <Text style={[styles.excerpt, { color: t.textMuted }]} numberOfLines={2}>
        {post.body[0]}
      </Text>
      <Text style={[styles.meta, { color: t.textMuted }]}>
        ♥ {post.likes} · 💬 {post.comments}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    padding: 14,
    gap: 8,
    marginBottom: 12,
  },
  where: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  picture: { width: 22, height: 22, borderRadius: 6 },
  meta: { fontSize: 13, flexShrink: 1 },
  title: { fontSize: 17, fontWeight: '700', lineHeight: 22 },
  excerpt: { fontSize: 15, lineHeight: 20 },
});
