import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useSidePadding } from '../insets';

export interface PhotoProps {
  index: number;
  /** On black, with a light status bar, as a photo viewer. */
  dark?: boolean;
}

/** One photo, large, with its caption. */
function Photo({ index, dark = false }: PhotoProps) {
  const c = useColors();
  const sides = useSidePadding();
  return (
    <View style={[styles.screen, sides, { backgroundColor: dark ? '#000000' : c.background }]}>
      <View
        nativeID={`photo-${index}`}
        style={[styles.photo, { backgroundColor: TILES[index % TILES.length] }]}
      />
      <Text style={[styles.caption, { color: dark ? '#FFFFFF' : c.text }]}>{ITEMS[index]}</Text>
    </View>
  );
}

export const PhotoScreen = defineScreen({
  component: Photo,
  options: (s, { dark = false }: PhotoProps) =>
    dark
      ? s
          .title({ text: 'Photo', color: '#FFFFFF' })
          .topBar({ background: { color: '#000000' }, backButton: { color: '#FFFFFF' } })
          .statusBar({ style: 'light', backgroundColor: '#000000' })
          .navigationBar({ backgroundColor: '#000000' })
      : s.title('Photo'),
});

const styles = StyleSheet.create({
  screen: { flex: 1 },
  photo: { aspectRatio: 1, margin: 16, borderRadius: 16 },
  caption: { fontFamily: fonts.bodySemiBold, fontSize: 20, marginHorizontal: 16 },
});
