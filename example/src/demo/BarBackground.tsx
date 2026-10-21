import React from 'react';
import { StyleSheet, View } from 'react-native';
import { swatches } from '../ui/theme';

/** A component behind the bar: `topBar.background.component` names it. Stripes, to see where it ends. */
export function BarBackground() {
  return (
    <View style={styles.stripes}>
      {swatches.map((color) => (
        <View key={color} style={[styles.stripe, { backgroundColor: color }]} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  stripes: { flex: 1, flexDirection: 'row' },
  stripe: { flex: 1 },
});
