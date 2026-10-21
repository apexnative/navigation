import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { Card, Muted, Screen } from '../ui';
import { GALLERY } from './galleryColors';

/** A photo of the gallery demo, opened with a zoom or from a preview. */
function GalleryItem({ index }: { index: number }) {
  return (
    <Screen>
      <View style={[styles.photo, { backgroundColor: GALLERY[index] }]}>
        <Text style={styles.label}>Photo {index + 1}</Text>
      </View>
      <Card title={`Photo ${index + 1}`}>
        <Muted>Opened from its tile in the gallery.</Muted>
      </Card>
    </Screen>
  );
}

export const GalleryItemScreen = defineScreen({
  component: GalleryItem,
  options: (s, props: { index: number }) => s.title(`Photo ${props.index + 1}`).hideBottomTabs(),
});

const styles = StyleSheet.create({
  photo: {
    aspectRatio: 1,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  label: { color: '#FFFFFF', fontSize: 28, fontWeight: '800' },
});
