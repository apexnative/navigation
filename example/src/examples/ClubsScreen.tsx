import React from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Screen } from '../ui';
import { dynamic } from '../ui/theme';
import { icons } from '../ui/icons';
import { ClubCard } from './ClubCard';
import { clubs, posts } from './data';
import { PostRow } from './PostRow';
import { SectionHeader } from './SectionHeader';

/** A feed: the user's clubs in a row of cards, then the latest posts. A post opens its own screen. */
function Clubs() {
  const nav = useNavigation();
  return (
    <Screen>
      <SectionHeader title="Your clubs" onMore={() => log('event', 'More clubs')} />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.bleed}
        contentContainerStyle={styles.cards}
      >
        {clubs.map((club) => (
          <ClubCard key={club.id} club={club} />
        ))}
      </ScrollView>
      <SectionHeader title="Latest posts" />
      {posts.map((post) => (
        <PostRow
          key={post.id}
          post={post}
          onPress={() => nav.push('ExamplePost', { props: { postId: post.id } })}
        />
      ))}
    </Screen>
  );
}

/**
 * A large title in the row of the bar that stays as it is while the list scrolls (inline, iOS 26: the screen has no
 * back button), a search button and the user's avatar.
 */
export const ClubsScreen = defineScreen({
  component: Clubs,
  options: (s) =>
    s
      .title('Clubs')
      .largeTitle({ displayMode: 'inline', collapseOnScroll: false })
      // The first right button is the one at the edge: the avatar, then the search.
      .rightButtons(
        { id: 'profile', component: { name: 'ExampleAvatar' } },
        {
          id: 'search',
          text: 'Search',
          icon: { ios: 'magnifyingglass', android: icons.search },
          onPress: () => log('event', 'Search pressed'),
        }
      )
      // The bar in the color of the screen: what scrolls under it does not show through.
      .topBar({ background: { color: dynamic.background } })
      .safeArea('top', 'left', 'right')
      // The list goes to the sides, so its scroll bar stands at the edge; `Screen` keeps the rows clear of the
      // vertical bar of iPhone Duo.
      .ios((i) => i.safeArea('top')),
});

const styles = StyleSheet.create({
  bleed: { marginHorizontal: -16, marginBottom: 20 },
  cards: { gap: 12, paddingHorizontal: 16 },
});
