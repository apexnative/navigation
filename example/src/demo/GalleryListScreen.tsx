import React from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation, usePreview } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { useTheme } from '../ui';
import { GALLERY } from './galleryColors';

const ROWS = Array.from({ length: 100 }, (_, index) => index);

/**
 * A long list of photos with a preview on every row (`usePreview`): a long press previews the photo with a Share menu, a
 * tap opens it. On iOS a preview's screen renders only once its row is pressed, so the list holds none until then.
 */
function GalleryList() {
  return (
    <FlatList
      data={ROWS}
      keyExtractor={(row) => String(row)}
      contentInsetAdjustmentBehavior="automatic"
      renderItem={({ item }) => <GalleryRow row={item} />}
    />
  );
}

/** A row of the list: its swatch and title, the view its preview lifts from. */
function GalleryRow({ row }: { row: number }) {
  const t = useTheme();
  const nav = useNavigation();
  const index = row % GALLERY.length;
  usePreview(`gallery-row-${row}`, {
    screen: 'GalleryItem',
    props: { index },
    actions: [
      {
        id: 'share',
        title: 'Share',
        onPress: () => log('event', 'Preview action', `Share row ${row + 1}`),
      },
    ],
  });
  return (
    <Pressable
      nativeID={`gallery-row-${row}`}
      onPress={() => nav.push('GalleryItem', { props: { index } })}
      style={[styles.row, { borderBottomColor: t.border, backgroundColor: t.background }]}
    >
      <View style={[styles.swatch, { backgroundColor: GALLERY[index] }]} />
      <Text style={[styles.title, { color: t.text }]}>
        Row {row + 1} · Photo {index + 1}
      </Text>
    </Pressable>
  );
}

export const GalleryListScreen = defineScreen({
  component: GalleryList,
  options: (s) => s.title('Previews in a list').hideBottomTabs(),
});

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  swatch: { width: 44, height: 44, borderRadius: 10, marginRight: 14 },
  title: { fontSize: 17 },
});
