import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation, usePreview } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Card, Muted, Screen } from '../ui';
import { GALLERY } from './galleryColors';

/**
 * A grid of photos: a tap zooms the photo open (iOS 18+ `transition: { zoom }` from the tile's `nativeID`), as a pushed
 * screen or a modal; a long press previews it with a menu (`usePreview` on the same `nativeID`).
 */
function Gallery() {
  const nav = useNavigation();

  const open = (index: number, modal: boolean) => {
    const transition = { zoom: `tile-${index}` };
    if (modal) {
      nav.showModal('GalleryItem', { props: { index }, closeButton: true, transition });
    } else {
      nav.push('GalleryItem', { props: { index }, transition });
    }
  };

  return (
    <Screen>
      <Card title="Zoom and preview">
        <Muted>
          Tap a photo: it zooms open as a pushed screen; the bottom row opens as a modal. Back, or a
          swipe down, zooms it back into its tile. Long press: a preview with a menu.
        </Muted>
      </Card>
      <View style={styles.grid}>
        {GALLERY.map((color, index) => (
          <Tile key={color} index={index} color={color} onPress={() => open(index, index >= 3)} />
        ))}
      </View>
    </Screen>
  );
}

/** A tile of the grid: a long press previews its photo, Share goes to the Journal. */
function Tile({ index, color, onPress }: { index: number; color: string; onPress: () => void }) {
  usePreview(`tile-${index}`, {
    screen: 'GalleryItem',
    props: { index },
    actions: [
      {
        id: 'share',
        title: 'Share',
        onPress: () => log('event', 'Preview action', `Share photo ${index + 1}`),
      },
    ],
  });
  return (
    <Pressable
      nativeID={`tile-${index}`}
      onPress={onPress}
      style={[styles.tile, { backgroundColor: color }]}
    >
      <Text style={styles.label}>{index >= 3 ? `Modal ${index + 1}` : `Push ${index + 1}`}</Text>
    </Pressable>
  );
}

export const GalleryScreen = defineScreen({
  component: Gallery,
  options: (s) => s.title('Gallery').hideBottomTabs(),
});

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tile: {
    width: '31%',
    aspectRatio: 1,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { color: '#FFFFFF', fontWeight: '700' },
});
