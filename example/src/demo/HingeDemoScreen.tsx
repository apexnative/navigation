import React, { useRef } from 'react';
import { Animated, StyleSheet, Text, View } from 'react-native';
import { defineScreen, type Hinge, useHinge, useHingeChange } from '@apexnative/navigation';
import { brand, fonts, Muted, Screen, useTheme } from '../ui';

const LID = 150;
const BAR = 12;

const STATES: Record<Hinge['state'], { label: string; color: string }> = {
  closed: { label: 'Closed', color: brand.lavender },
  partiallyOpen: { label: 'Partly open', color: brand.mustard },
  fullyOpen: { label: 'Open', color: brand.teal },
  unknown: { label: 'Unknown', color: brand.sky },
};

/**
 * The screen of 6.10: the angle of the hinge as the device opens. `useHinge` renders the number and the state; the lid
 * of the drawing turns with `useHingeChange`, without rendering the screen again.
 */
function HingeDemo() {
  const t = useTheme();
  const hinge = useHinge();
  const angle = useRef(new Animated.Value(hinge?.angle ?? 180)).current;
  useHingeChange((next) => angle.setValue(next?.angle ?? 180));
  // The lid lies on the base when closed and points the other way when open flat.
  const rotate = angle.interpolate({ inputRange: [0, 360], outputRange: ['0deg', '-360deg'] });
  const state = hinge ? STATES[hinge.state] : undefined;
  return (
    <Screen scroll={false}>
      <View style={styles.center}>
        <Text style={[styles.degrees, { color: t.text }]}>
          {hinge?.angle != null ? `${Math.round(hinge.angle)}°` : '—'}
        </Text>
        {state ? (
          <View style={[styles.pill, { backgroundColor: state.color }]}>
            <Text style={styles.pillText}>{state.label}</Text>
          </View>
        ) : (
          <Muted>This device has no hinge.</Muted>
        )}
        <View style={styles.drawing}>
          <View style={[styles.base, { backgroundColor: brand.navy }]} />
          <Animated.View style={[styles.lid, { transform: [{ rotate }] }]} />
          <View style={styles.hinge} />
        </View>
        <Muted>
          Open iPhone Duo or an Android foldable slowly: the number follows useHinge, the lid
          follows useHingeChange without rendering the screen again.
        </Muted>
      </View>
    </Screen>
  );
}

export const HingeDemoScreen = defineScreen({
  component: HingeDemo,
  options: (s) => s.title('The hinge'),
});

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 20, padding: 24 },
  degrees: { fontFamily: fonts.display, fontSize: 96, fontVariant: ['tabular-nums'] },
  pill: { borderRadius: 999, paddingHorizontal: 16, paddingVertical: 6 },
  pillText: { fontFamily: fonts.bodySemiBold, fontSize: 15, color: brand.deep },
  drawing: { width: LID * 2 + 24, height: LID + 24, marginVertical: 12 },
  // The hinge sits in the middle of the bottom edge; the base goes right from it, the lid turns around it.
  base: {
    position: 'absolute',
    left: LID + 12,
    bottom: 12 - BAR / 2,
    width: LID,
    height: BAR,
    borderRadius: BAR / 2,
  },
  lid: {
    position: 'absolute',
    left: LID + 12,
    bottom: 12 - BAR / 2,
    width: LID,
    height: BAR,
    borderRadius: BAR / 2,
    backgroundColor: brand.coral,
    transformOrigin: 'left center',
  },
  hinge: {
    position: 'absolute',
    left: LID + 12 - BAR,
    bottom: 12 - BAR,
    width: BAR * 2,
    height: BAR * 2,
    borderRadius: BAR,
    backgroundColor: brand.mustard,
  },
});
