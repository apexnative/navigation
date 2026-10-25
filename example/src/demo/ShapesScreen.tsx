import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Screen, useTheme } from '../ui';
import { SHAPES } from './shapes';
import { ShapeView } from './ShapeView';

const DURATION = 900;

/**
 * Turned and scaled shared elements: a tap opens the shape, which flies from its tile, turned and scaled as it is
 * there (by its own `transform` and its parent's), to its screen, turned as it is there, along the shorter arc
 * (`transition: { shared }`); a badge with a `transform` of its own rises and fades in after it (`elementTransitions`).
 */
function Shapes() {
  const t = useTheme();
  const nav = useNavigation();
  const open = (index: number) =>
    nav.push('Shape', {
      props: { index },
      transition: { shared: [[`shape-${index}`, `shape-hero-${index}`]], duration: DURATION },
      options: {
        animations: {
          push: {
            elementTransitions: [
              {
                id: `shape-badge-${index}`,
                alpha: { from: 0, duration: 400, startDelay: 400 },
                translationY: { from: 40, duration: 400, startDelay: 400 },
              },
            ],
          },
        },
      },
    });
  return (
    <Screen>
      <View style={styles.grid}>
        {SHAPES.map((shape, index) => (
          <Pressable key={shape.title} onPress={() => open(index)} style={styles.cell}>
            <View style={[styles.tile, { transform: [{ scale: shape.parentScale ?? 1 }] }]}>
              <ShapeView shape={shape} id={`shape-${index}`} size={80} transform={shape.from} />
            </View>
            <Text style={[styles.title, { color: t.text }]}>{shape.title}</Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

export const ShapesScreen = defineScreen({
  component: Shapes,
  options: (s) => s.title('Shapes').hideBottomTabs(),
});

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 20 },
  cell: { width: '47%', alignItems: 'center' },
  tile: { height: 120, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 15, fontWeight: '700', marginTop: 6 },
});
