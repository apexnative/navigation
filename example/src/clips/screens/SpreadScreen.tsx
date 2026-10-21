import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useReservedRegions } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { useHingeForClips } from '../hinge';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useSidePadding } from '../insets';

// How far the pages keep from the fold: controls and text read badly on it.
const GAP = 20;

/**
 * A note read as a book: one page, or two pages, one on each side of the fold of a partly folded device
 * (`useReservedRegions`).
 */
function Spread() {
  const c = useColors();
  const sides = useSidePadding(16);
  const fold = useReservedRegions().division[0];
  useHingeForClips();
  const pages = [0, 1].map((page) => (
    <View key={page} style={[styles.page, { backgroundColor: c.card }]}>
      <View style={[styles.tile, { backgroundColor: TILES[(page + 3) % TILES.length] }]} />
      <Text style={[styles.title, { color: c.text }]}>{ITEMS[page + 2]}</Text>
      <Text style={[styles.body, { color: c.muted }]}>
        Pack light, leave early, take the coast road. Lunch in the harbor, then the long walk to the
        lighthouse before the light goes.
      </Text>
    </View>
  ));
  if (!fold) {
    return (
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={{ backgroundColor: c.background }}
        contentContainerStyle={[styles.column, sides]}
      >
        {pages}
      </ScrollView>
    );
  }
  // A fold across the screen puts the pages one above the other, a fold down it side by side.
  const across = fold.width > fold.height;
  const first = across
    ? { top: 0, left: 0, right: 0, height: fold.y - GAP }
    : { top: 0, bottom: 0, left: 0, width: fold.x - GAP };
  const second = across
    ? { top: fold.y + fold.height + GAP, left: 0, right: 0, bottom: 0 }
    : { top: 0, bottom: 0, left: fold.x + fold.width + GAP, right: 0 };
  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <View style={[styles.side, first]}>{pages[0]}</View>
      <View style={[styles.side, second]}>{pages[1]}</View>
    </View>
  );
}

export const SpreadScreen = defineScreen({
  component: Spread,
  options: (s) => s.title('Trip notes'),
});

const styles = StyleSheet.create({
  screen: { flex: 1 },
  column: { padding: 16, gap: 16 },
  side: { position: 'absolute', padding: 16, justifyContent: 'center' },
  page: { borderRadius: 16, padding: 16, gap: 10 },
  tile: { height: 120, borderRadius: 12 },
  title: { fontFamily: fonts.display, fontSize: 22 },
  body: { fontFamily: fonts.body, fontSize: 15, lineHeight: 21 },
});
