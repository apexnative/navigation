import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { Muted, Screen, useTheme } from '../ui';
import { CARDS } from './cards';

/** A card of the shared element gallery: the card and its title came from the tile. */
function CardDetails({ index }: { index: number }) {
  const t = useTheme();
  const card = CARDS[index];
  return (
    <Screen>
      <View
        nativeID={`card-hero-${index}`}
        style={[styles.hero, { backgroundColor: card.color }]}
      />
      <Text nativeID={`card-heading-${index}`} style={[styles.heading, { color: t.text }]}>
        {card.title}
      </Text>
      <View nativeID={`card-text-${index}`}>
        <Muted>{card.text}</Muted>
      </View>
    </Screen>
  );
}

export const CardScreen = defineScreen({
  component: CardDetails,
  options: (s) => s.title('Card').hideBottomTabs(),
});

const styles = StyleSheet.create({
  hero: { height: 260, borderRadius: 24 },
  heading: { fontSize: 30, fontWeight: '800', marginVertical: 14 },
});
