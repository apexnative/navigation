import React, { useRef } from 'react';
import { Animated, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useHinge, useHingeChange } from '@apexnative/navigation';
import { useColors } from '../colors';
import { useHingeForClips } from '../hinge';
import { brand, fonts } from '../../ui';

const LID = 140;
const BAR = 12;

const STATES = {
  closed: 'Closed',
  partiallyOpen: 'Partly open',
  fullyOpen: 'Open',
  unknown: 'Unknown',
};

/**
 * The hinge as the device opens: the degrees and the state from `useHinge`; the lid of the drawing turns with
 * `useHingeChange`, without rendering the screen again.
 */
function Lid() {
  const c = useColors();
  const hinge = useHinge();
  useHingeForClips();
  const angle = useRef(new Animated.Value(hinge?.angle ?? 180)).current;
  useHingeChange((next) => angle.setValue(next?.angle ?? 180));
  // The lid lies on the base at 0° and points the other way at 180°.
  const rotate = angle.interpolate({ inputRange: [0, 360], outputRange: ['0deg', '-360deg'] });
  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <Text style={[styles.degrees, { color: c.text }]}>
        {hinge?.angle != null ? `${Math.round(hinge.angle)}°` : '—'}
      </Text>
      <Text style={[styles.state, { color: c.muted }]}>
        {hinge ? STATES[hinge.state] : 'No hinge'}
      </Text>
      <View style={styles.drawing}>
        <View style={[styles.bar, { backgroundColor: brand.navy }]} />
        <Animated.View
          style={[
            styles.bar,
            styles.lid,
            { backgroundColor: brand.coral, transform: [{ rotate }] },
          ]}
        />
        <View style={[styles.joint, { backgroundColor: brand.mustard }]} />
      </View>
    </View>
  );
}

export const LidScreen = defineScreen({
  component: Lid,
  options: (s) => s.title('Hinge'),
});

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8 },
  degrees: { fontFamily: fonts.display, fontSize: 96, fontVariant: ['tabular-nums'] },
  state: { fontFamily: fonts.bodySemiBold, fontSize: 18 },
  drawing: { width: LID * 2 + 24, height: LID + 24, marginTop: 24 },
  // The joint sits in the middle of the bottom edge; the base goes right from it, the lid turns around it.
  bar: {
    position: 'absolute',
    left: LID + 12,
    bottom: 12 - BAR / 2,
    width: LID,
    height: BAR,
    borderRadius: BAR / 2,
  },
  lid: { transformOrigin: 'left center' },
  joint: {
    position: 'absolute',
    left: LID + 12 - BAR,
    bottom: 12 - BAR,
    width: BAR * 2,
    height: BAR * 2,
    borderRadius: BAR,
  },
});
