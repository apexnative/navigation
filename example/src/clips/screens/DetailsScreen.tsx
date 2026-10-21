import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { LIST_LAYOUT, useSidePadding } from '../insets';

export interface DetailsProps {
  index: number;
}

/** An item: a colored card, a few lines and a way on to Profile. */
function Details({ index }: DetailsProps) {
  const c = useColors();
  const sides = useSidePadding();
  const nav = useClipNavigation();
  return (
    <ScrollView
      contentContainerStyle={sides}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <View style={[styles.card, { backgroundColor: TILES[index % TILES.length] }]} />
      <Text style={[styles.title, { color: c.text }]}>{ITEMS[index]}</Text>
      <Text style={[styles.body, { color: c.muted }]}>
        Updated today. Shared with two people. Tap Profile to see who made it.
      </Text>
      <Pressable onPress={() => nav.push('Profile', { props: { index } })}>
        <Text style={[styles.link, { color: c.tint }]}>Open Profile</Text>
      </Pressable>
    </ScrollView>
  );
}

export const DetailsScreen = defineScreen({
  component: Details,
  options: (s, { index }: DetailsProps) => s.title(ITEMS[index] ?? 'Details').layout(LIST_LAYOUT),
});

const styles = StyleSheet.create({
  card: { height: 180, margin: 16, borderRadius: 16 },
  title: { fontFamily: fonts.display, fontSize: 26, marginHorizontal: 16 },
  body: { fontFamily: fonts.body, fontSize: 16, lineHeight: 22, margin: 16 },
  link: { fontFamily: fonts.body, fontSize: 17, marginHorizontal: 16 },
});
