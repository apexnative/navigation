import React, { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { log } from '../showcase/journal';
import { palette } from '../ui/theme';

/**
 * A component as a button of the bar: `component` names it. A component button handles its own touches — here a tap
 * counts, and goes to the Journal; the bar sends no `buttonPressed` for it.
 */
export function BarButtonView({ label = 'Like' }: { label?: string }) {
  const [count, setCount] = useState(0);
  return (
    <Pressable
      accessibilityRole="button"
      hitSlop={6}
      onPress={() => {
        setCount((value) => value + 1);
        log('event', 'Component button', `${label} ${count + 1}`);
      }}
      style={({ pressed }) => [styles.pill, { opacity: pressed ? 0.7 : 1 }]}
    >
      <Text style={styles.text}>
        ♥ {label} {count}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: {
    backgroundColor: palette.primary,
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  text: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
});
