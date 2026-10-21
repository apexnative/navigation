import React from 'react';
import { StyleSheet, View } from 'react-native';
import { brand } from '../../ui';
import { MiniPhone } from './MiniPhone';

/** Safe area, small: its edges in colors around the content. */
export function SafeAreaPreview() {
  return (
    <MiniPhone background="#FFFAF0">
      <View style={[styles.edge, styles.top]} />
      <View style={[styles.edge, styles.bottom]} />
      <View style={[styles.edge, styles.side, styles.left]} />
      <View style={[styles.edge, styles.side, styles.right]} />
      <View style={styles.content} />
    </MiniPhone>
  );
}

const styles = StyleSheet.create({
  edge: { position: 'absolute', opacity: 0.75 },
  top: { left: 0, right: 0, top: 0, height: 22, backgroundColor: brand.coral },
  bottom: { left: 0, right: 0, bottom: 0, height: 18, backgroundColor: brand.teal },
  side: { top: 22, bottom: 18, width: 8, backgroundColor: brand.mustard },
  left: { left: 0 },
  right: { right: 0 },
  content: {
    position: 'absolute',
    left: 14,
    right: 14,
    top: 34,
    bottom: 30,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: brand.navy,
    borderRadius: 8,
    opacity: 0.6,
  },
});
