import React, { useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useSafeAreaInsets } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { useTheme } from '../ui';
import { dynamic } from '../ui/theme';
import { icons, md } from '../ui/icons';
import { Attendees } from './Attendees';
import { findClub, findEvent, photoUrl } from './data';
import { InfoRow } from './InfoRow';

function EventDetails({ eventId }: { eventId: string }) {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const [going, setGoing] = useState(false);
  const event = findEvent(eventId);
  const club = findClub(event.clubId);
  return (
    <ScrollView
      style={{ backgroundColor: t.background }}
      // The photo starts at the top of the screen, under the status bar and the bar.
      contentInsetAdjustmentBehavior="never"
      contentContainerStyle={{ paddingBottom: insets.bottom + 24 }}
    >
      <Image source={{ uri: photoUrl(event.id, 1200, 900) }} style={styles.photo} />
      <View
        style={[styles.body, { paddingLeft: 20 + insets.left, paddingRight: 20 + insets.right }]}
      >
        <View style={styles.club}>
          <Image source={{ uri: photoUrl(club.id, 80, 80) }} style={styles.clubPicture} />
          <Text style={[styles.clubName, { color: t.textMuted }]}>{club.name}</Text>
        </View>
        <Text style={[styles.title, { color: t.text }]}>{event.title}</Text>
        <InfoRow
          symbol="📅"
          title={`${event.weekday}, ${event.day} ${event.month}`}
          detail={`${event.time} – ${event.ends}`}
        />
        <InfoRow symbol="📍" title={event.place} detail="Open in Maps" />
        <Attendees count={event.going + (going ? 1 : 0)} />
        <Text style={[styles.heading, { color: t.text }]}>About</Text>
        <Text style={[styles.about, { color: t.text }]}>{event.about}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={() => setGoing((value) => !value)}
          style={({ pressed }) => [
            styles.join,
            { backgroundColor: going ? t.primarySoft : t.primary, opacity: pressed ? 0.8 : 1 },
          ]}
        >
          <Text style={[styles.joinText, { color: going ? t.primary : '#FFFFFF' }]}>
            {going ? "You're going ✓" : "I'm going"}
          </Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}

/**
 * A photo under a transparent bar, from the top of the screen: the back button and "…" float over it in their glass,
 * the status bar is light, and the list draws no edge effect over the photo.
 */
export const EventScreen = defineScreen({
  component: EventDetails,
  options: (s) =>
    s
      .topBar({
        background: { color: 'transparent' },
        drawBehind: true,
        // Glass over a photo turns light or dark by what is under it, and the buttons' traits with it: a color with
        // a light and a dark value follows, so the icons stay readable on both.
        backButton: { color: dynamic.text },
        rightButtonColor: dynamic.text,
      })
      .rightButtons({
        id: 'event-more',
        text: 'More',
        icon: { ios: 'ellipsis', android: icons.menu },
        onPress: () => log('event', 'Event menu'),
        menu: {
          items: [
            {
              id: 'event-share',
              title: 'Share',
              icon: { ios: 'square.and.arrow.up', android: md('share') },
            },
            {
              id: 'event-calendar',
              title: 'Add to Calendar',
              icon: { ios: 'calendar.badge.plus', android: md('calendar_add_on') },
            },
          ],
        },
      })
      .scrollEdgeEffect({ top: { hidden: true } })
      .statusBar({ style: 'light' })
      .safeArea('left', 'right')
      // The page goes to the sides, so its scroll bar stands at the edge; the text keeps clear of the vertical bar of
      // iPhone Duo itself.
      .ios((i) => i.safeArea()),
});

const styles = StyleSheet.create({
  photo: { width: '100%', height: 320 },
  body: { padding: 20 },
  club: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 8 },
  clubPicture: { width: 22, height: 22, borderRadius: 6 },
  clubName: { fontSize: 14, fontWeight: '500' },
  title: { fontSize: 28, fontWeight: '800', lineHeight: 34, marginBottom: 18 },
  heading: { fontSize: 20, fontWeight: '700', marginBottom: 8 },
  about: { fontSize: 16, lineHeight: 23, marginBottom: 24 },
  join: { borderRadius: 14, paddingVertical: 15, alignItems: 'center' },
  joinText: { fontSize: 17, fontWeight: '700' },
});
