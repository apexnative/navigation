import React from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import type { ScreenInfo } from '@apexnative/navigation';
import { Muted, useTheme } from '../ui';

/** What a screen shows while it waits for its data or a lazy component (`loadingFallback`). */
export function LoadingFallback({ screen }: { screen: ScreenInfo }) {
  const t = useTheme();
  return (
    <View style={[styles.center, { backgroundColor: t.background }]}>
      <ActivityIndicator />
      <Muted>Loading {screen.name}…</Muted>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
});
