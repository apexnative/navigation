import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { Muted, Screen, useTheme } from '../ui';
import { SHAPES } from './shapes';
import { ShapeView } from './ShapeView';

/** A shape of the turned shared elements: the shape came from its tile; the badge keeps its own turn as it rises. */
function ShapeDetails({ index }: { index: number }) {
  const t = useTheme();
  const shape = SHAPES[index];
  return (
    <Screen>
      <View style={styles.stage}>
        <ShapeView shape={shape} id={`shape-hero-${index}`} size={200} transform={shape.to} />
      </View>
      <View
        nativeID={`shape-badge-${index}`}
        style={[styles.badge, { backgroundColor: shape.color }]}
      >
        <Text style={styles.badgeText}>{shape.title}</Text>
      </View>
      <Text style={[styles.heading, { color: t.text }]}>{shape.title}</Text>
      <Muted>{shape.note}</Muted>
    </Screen>
  );
}

export const ShapeScreen = defineScreen({
  component: ShapeDetails,
  options: (s) => s.title('Shape').hideBottomTabs(),
});

const styles = StyleSheet.create({
  stage: { height: 300, alignItems: 'center', justifyContent: 'center' },
  // Turned by its own `transform`: it stays turned while its element transition rises and fades it in.
  badge: {
    alignSelf: 'center',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 14,
    transform: [{ rotate: '-8deg' }],
  },
  badgeText: { color: 'white', fontWeight: '800' },
  heading: { fontSize: 24, fontWeight: '800', marginVertical: 12 },
});
