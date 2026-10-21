import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../ui';

/** A made-up wordmark as the left button of the bar, where an app puts its logo. */
export function BarLogo() {
  const t = useTheme();
  return (
    <View style={styles.frame} accessibilityRole="header" accessibilityLabel="Orbit">
      <Text style={[styles.word, { color: t.dark ? '#FFFFFF' : '#1E1B4B' }]}>
        Orbit
        <Text style={{ color: t.primary }}>.</Text>
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { height: 44, justifyContent: 'center' },
  word: { fontSize: 26, fontWeight: '800', letterSpacing: -0.6 },
});
