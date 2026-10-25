import React from 'react';
import { ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useScrollBottomPadding } from '../insets';

/**
 * A horizontal carousel of cards. The swipe back works from the edge only (`popGesture('edge')`), so a swipe on the
 * cards moves them and doesn't take the screen away.
 */
function Carousel() {
  const c = useColors();
  const { width } = useWindowDimensions();
  const bottom = useScrollBottomPadding();
  return (
    <ScrollView
      contentContainerStyle={bottom}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <ScrollView horizontal pagingEnabled showsHorizontalScrollIndicator={false}>
        {TILES.slice(0, 6).map((color, index) => (
          <View key={color} style={{ width, padding: 16 }}>
            <View style={[styles.card, { backgroundColor: color }]}>
              <Text style={styles.cardText}>{ITEMS[index + 2]}</Text>
            </View>
          </View>
        ))}
      </ScrollView>
      <Text style={[styles.body, { color: c.muted }]}>
        Swipe the cards to see the next trip. The swipe back starts at the edge of the screen.
      </Text>
    </ScrollView>
  );
}

export const CarouselScreen = defineScreen({
  component: Carousel,
  options: (s) => s.title('Trips').popGesture('edge'),
});

const styles = StyleSheet.create({
  card: { height: 260, borderRadius: 20, justifyContent: 'flex-end', padding: 20 },
  cardText: { fontFamily: fonts.display, fontSize: 26, color: '#FFFFFF' },
  body: { fontFamily: fonts.body, fontSize: 16, lineHeight: 22, marginHorizontal: 16 },
});
