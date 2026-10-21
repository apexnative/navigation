import React from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { Screen, useTheme } from '../ui';
import { dynamic } from '../ui/theme';
import { icons, md } from '../ui/icons';
import { findPerson, findPost, photoUrl } from './data';

function Post({ postId }: { postId: string }) {
  const t = useTheme();
  const post = findPost(postId);
  return (
    <Screen>
      <Text style={[styles.title, { color: t.text }]}>{post.title}</Text>
      <Text style={[styles.byline, { color: t.textMuted }]}>
        {findPerson(post.authorId).name} · {post.ago} ago
      </Text>
      <Image source={{ uri: photoUrl(post.id, 900, 560) }} style={styles.cover} />
      {post.body.map((paragraph, index) => (
        <Text key={index} style={[styles.paragraph, { color: t.text }]}>
          {paragraph}
        </Text>
      ))}
      <View style={[styles.stats, { borderTopColor: t.border }]}>
        <Text style={[styles.stat, { color: t.textMuted }]}>♥ {post.likes} likes</Text>
        <Text style={[styles.stat, { color: t.textMuted }]}>💬 {post.comments} comments</Text>
      </View>
    </Screen>
  );
}

/**
 * The bar of a post: the back button, then the club and the author in a title component that fills the bar up
 * to the "…" menu.
 */
export const PostScreen = defineScreen({
  component: Post,
  options: (s, props: { postId: string }) => {
    const post = findPost(props.postId);
    return (
      s
        .title({
          component: {
            name: 'ExamplePostHeader',
            alignment: 'fill',
            passProps: { clubId: post.clubId, authorId: post.authorId, ago: post.ago },
          },
        })
        .rightButtons({
          id: 'post-more',
          text: 'More',
          icon: { ios: 'ellipsis', android: icons.menu },
          menu: {
            items: [
              {
                id: 'post-share',
                title: 'Share',
                icon: { ios: 'square.and.arrow.up', android: md('share') },
              },
              {
                id: 'post-save',
                title: 'Save',
                icon: { ios: 'bookmark', android: md('bookmark') },
              },
              {
                id: 'post-report',
                title: 'Report',
                icon: { ios: 'flag', android: md('flag') },
                destructive: true,
              },
            ],
          },
        })
        .topBar({ background: { color: dynamic.background } })
        .safeArea('top', 'left', 'right')
        // The list goes to the sides, so its scroll bar stands at the edge; `Screen` keeps the rows clear of the
        // vertical bar of iPhone Duo.
        .ios((i) => i.safeArea('top'))
    );
  },
});

const styles = StyleSheet.create({
  title: { fontSize: 28, fontWeight: '800', lineHeight: 34, letterSpacing: -0.4 },
  byline: { fontSize: 14, marginTop: 8, marginBottom: 16 },
  cover: { width: '100%', aspectRatio: 16 / 10, borderRadius: 16, marginBottom: 16 },
  paragraph: { fontSize: 17, lineHeight: 25, marginBottom: 14 },
  stats: {
    flexDirection: 'row',
    gap: 20,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingTop: 14,
    marginTop: 6,
  },
  stat: { fontSize: 14 },
});
