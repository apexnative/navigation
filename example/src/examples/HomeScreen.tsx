import React from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Screen } from '../ui';
import { dynamic } from '../ui/theme';
import { events, posts } from './data';
import { EventCard } from './EventCard';
import { PostRow } from './PostRow';
import { SectionHeader } from './SectionHeader';

/** A home screen: this week's events in a row of cards, then popular posts. */
function Home() {
  const nav = useNavigation();
  return (
    <Screen>
      <SectionHeader title="This week" onMore={() => log('event', 'More events')} />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.bleed}
        contentContainerStyle={styles.cards}
      >
        {events.map((event) => (
          <EventCard
            key={event.id}
            event={event}
            onPress={() => nav.push('ExampleEvent', { props: { eventId: event.id } })}
          />
        ))}
      </ScrollView>
      <SectionHeader title="Popular posts" />
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

/** The app's logo on the left of the bar in place of a title, and the user's avatar on the right. */
export const HomeScreen = defineScreen({
  component: Home,
  options: (s) =>
    s
      // The logo as the title, stretched from the leading edge: as a left button with no glass it made the back button's
      // glass jump on a push to a screen with a photo under the bar.
      .title({ component: { name: 'ExampleLogo', alignment: 'fill' } })
      .rightButtons({ id: 'profile', component: { name: 'ExampleAvatar' } })
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
