import React from 'react';
import { StyleSheet, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';

/**
 * The end of a clip: an overlay with a dot nobody sees. The simulator's recorder writes a frame only when the screen
 * changes and holds the last one back; this change makes it write the clip's last frame.
 */
function Tick() {
  return (
    <View pointerEvents="none" style={styles.screen}>
      <View style={styles.dot} />
    </View>
  );
}

export const TickScreen = defineScreen({ component: Tick });

const styles = StyleSheet.create({
  screen: { flex: 1 },
  dot: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    width: 2,
    height: 2,
    backgroundColor: 'rgba(0,0,0,0.03)',
  },
});
