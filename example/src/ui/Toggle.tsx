import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { brand, useTheme } from './theme';

/** On or off: teal with the knob on the right when on. */
export function Toggle({
  on,
  onPress,
  label,
}: {
  on: boolean;
  onPress: () => void;
  label: string;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityLabel={label}
      accessibilityState={{ checked: on }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.track,
        {
          backgroundColor: on ? brand.teal : t.outline,
          justifyContent: on ? 'flex-end' : 'flex-start',
          opacity: pressed ? 0.8 : 1,
        },
      ]}
    >
      <View style={styles.knob} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  track: { width: 52, height: 32, borderRadius: 16, padding: 3, flexDirection: 'row' },
  knob: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#FFFFFF',
    boxShadow: '0 1px 3px rgba(16, 24, 40, 0.3)',
  },
});
