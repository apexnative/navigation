import React, { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@apexnative/navigation';
import { palette } from '../ui/theme';

/**
 * A toast as an overlay: a strip at the top that goes by itself. Touches outside its views reach the screens below, which take the
 * touches everywhere but on the strip.
 */
export function ToastOverlay({ text = 'Saved' }: { text?: string }) {
  const nav = useNavigation();
  useEffect(() => {
    const timer = setTimeout(() => nav.dismiss(), 2500);
    return () => clearTimeout(timer);
  }, [nav]);
  return (
    <View pointerEvents="box-none" style={styles.fill}>
      <View style={styles.toast}>
        <Text style={styles.text}>✓ {text}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, alignItems: 'center' },
  toast: {
    marginTop: 8,
    backgroundColor: palette.accent,
    borderRadius: 22,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  text: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
});
