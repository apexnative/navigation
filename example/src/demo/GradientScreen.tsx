import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button } from '../ui';

interface GradientProps {
  /** The screen shows a transparent top bar over its gradient; without it the bar is hidden. */
  bar?: boolean;
}

/**
 * A screen whose content draws its background, a gradient, over the screen's own color: on Android the room beside it
 * in a push or a pop goes on with the gradient, not with the screen's own color. A strip of its own color at each side
 * shows which edge goes on: orange at the left, green at the right.
 */
function Gradient({ bar = false }: GradientProps) {
  const nav = useNavigation();
  return (
    <View style={styles.gradient}>
      <View style={[styles.side, styles.left]} />
      <View style={[styles.side, styles.right]} />
      <Text style={styles.title}>{bar ? 'With a transparent bar' : 'Without a bar'}</Text>
      {bar ? (
        <Button title="Back" onPress={() => nav.pop()} />
      ) : (
        <>
          <Button
            title="Push the screen with a bar"
            variant="primary"
            onPress={() => nav.push('Gradient', { props: { bar: true } })}
          />
          <Button title="Back" onPress={() => nav.pop()} />
        </>
      )}
    </View>
  );
}

export const GradientScreen = defineScreen({
  component: Gradient,
  options: (s, { bar = false }: GradientProps) =>
    s
      .statusBar({ drawBehind: true })
      .topBar(
        bar
          ? {
              visible: true,
              drawBehind: true,
              noBorder: true,
              background: { color: 'transparent' },
            }
          : { visible: false }
      )
      // No edge kept: the gradient goes under the status bar and the top bar too.
      .safeArea(),
});

const styles = StyleSheet.create({
  gradient: {
    flex: 1,
    justifyContent: 'center',
    gap: 12,
    padding: 24,
    backgroundImage: 'linear-gradient(180deg, #5b7fe6 0%, #b9c4f2 55%, #f4d8ee 100%)',
  },
  side: { position: 'absolute', top: 0, bottom: 0, width: 6 },
  left: { left: 0, backgroundColor: '#e8743b' },
  right: { right: 0, backgroundColor: '#2e9e6a' },
  title: { fontSize: 22, fontWeight: '600', color: '#1d2340', textAlign: 'center' },
});
