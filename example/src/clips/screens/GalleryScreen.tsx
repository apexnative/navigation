import React from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { defineScreen, usePreview } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { TILES, useColors } from '../colors';
import { useScrollBottomPadding } from '../insets';
import { useClipNavigation } from '../nav';

/** A grid of photos; a tap moves the photo into its own screen, a long press previews it. */
function Gallery() {
  const c = useColors();
  const nav = useClipNavigation();
  const bottom = useScrollBottomPadding();
  const open = (index: number) =>
    nav.push('Photo', {
      props: { index },
      transition: { shared: [[`tile-${index}`, `photo-${index}`]], duration: 450 },
    });
  useClipEvent('open', (index) => open(Number(index)));
  return (
    <ScrollView
      contentContainerStyle={bottom}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <View style={styles.grid}>
        {TILES.map((color, index) => (
          <Tile key={color} index={index} color={color} onPress={() => open(index)} />
        ))}
      </View>
    </ScrollView>
  );
}

// `usePreview` with the names of the clips' screens: the Showcase registers its own app's types (see `nav.ts`).
const usePhotoPreview = usePreview as unknown as (
  id: string,
  config: { screen: string; props: object; actions: { id: string; title: string }[] }
) => void;

/** A photo of the grid: a long press previews it with a Share action (iOS). */
function Tile({ index, color, onPress }: { index: number; color: string; onPress: () => void }) {
  usePhotoPreview(`tile-${index}`, {
    screen: 'Photo',
    props: { index },
    actions: [{ id: 'share', title: 'Share' }],
  });
  return (
    <Pressable onPress={onPress} style={styles.cell}>
      <View nativeID={`tile-${index}`} style={[styles.tile, { backgroundColor: color }]} />
    </Pressable>
  );
}

export const GalleryScreen = defineScreen({
  component: Gallery,
  options: (s) => s.title('Photos'),
});

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', padding: 6 },
  cell: { width: '33.33%', padding: 4 },
  tile: { aspectRatio: 1, borderRadius: 10 },
});
