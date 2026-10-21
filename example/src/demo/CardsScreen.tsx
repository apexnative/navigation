import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Screen, useTheme } from '../ui';
import { CARDS } from './cards';

const DURATION = 450;

/**
 * A gallery of cards: a tap opens the card, and the card and its title move from the tile into the screen
 * (`transition: { shared }` with two pairs of `nativeID`s, and back on pop); the text rises and fades in after them,
 * and fades out on the way back (`elementTransitions`).
 */
function Cards() {
  const t = useTheme();
  const nav = useNavigation();
  const open = (index: number) =>
    nav.push('Card', {
      props: { index },
      // The card and its title move into the screen and back; the rest fades.
      transition: {
        shared: [
          [`card-${index}`, `card-hero-${index}`],
          [`card-title-${index}`, `card-heading-${index}`],
        ],
        duration: DURATION,
      },
      // Animations of their own over the preset: the text rises after the card, and fades out first on the way back.
      options: {
        animations: {
          push: {
            elementTransitions: [
              {
                id: `card-text-${index}`,
                alpha: { from: 0, duration: DURATION, startDelay: 150 },
                translationY: { from: 40, duration: DURATION, startDelay: 150 },
              },
            ],
          },
          pop: {
            elementTransitions: [{ id: `card-text-${index}`, alpha: { to: 0, duration: 200 } }],
          },
        },
      },
    });
  return (
    <Screen>
      <View style={styles.grid}>
        {CARDS.map((card, index) => (
          <Pressable key={card.title} onPress={() => open(index)} style={styles.cell}>
            <View
              nativeID={`card-${index}`}
              style={[styles.card, { backgroundColor: card.color }]}
            />
            <Text nativeID={`card-title-${index}`} style={[styles.title, { color: t.text }]}>
              {card.title}
            </Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

export const CardsScreen = defineScreen({
  component: Cards,
  options: (s) => s.title('Cards').hideBottomTabs(),
});

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 16 },
  cell: { width: '47%' },
  card: { height: 130, borderRadius: 18 },
  title: { fontSize: 16, fontWeight: '700', marginTop: 8 },
});
