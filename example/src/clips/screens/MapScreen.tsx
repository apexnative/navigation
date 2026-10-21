import React from 'react';
import { StyleSheet, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { TILES } from '../colors';

// The blocks of the made-up map: where each sits, how big, and its color.
const BLOCKS = Array.from({ length: 28 }, (_, index) => ({
  left: `${(index % 4) * 25 + 2}%`,
  top: `${Math.floor(index / 4) * 14 + 2}%`,
  color: TILES[(index * 5) % TILES.length],
  park: index % 7 === 3,
}));

/** A map under a sheet: streets and blocks, nothing to press. */
function MapView() {
  return (
    <View style={styles.map}>
      {BLOCKS.map((block, index) => (
        <View
          key={index}
          style={[
            styles.block,
            {
              left: block.left as `${number}%`,
              top: block.top as `${number}%`,
              backgroundColor: block.park ? '#B7DDA4' : block.color,
              opacity: block.park ? 1 : 0.22,
            },
          ]}
        />
      ))}
      <View style={[styles.pin, { left: '46%', top: '30%' }]} />
    </View>
  );
}

export const MapScreen = defineScreen({
  component: MapView,
  options: (s) => s.title('Map'),
});

const styles = StyleSheet.create({
  map: { flex: 1, backgroundColor: '#F1ECE2' },
  block: { position: 'absolute', width: '21%', height: '11%', borderRadius: 6 },
  pin: {
    position: 'absolute',
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#E76F51',
    borderWidth: 4,
    borderColor: '#FFFFFF',
  },
});
