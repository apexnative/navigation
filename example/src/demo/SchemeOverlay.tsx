import React from 'react';
import { Appearance, StyleSheet, Text, useColorScheme, View } from 'react-native';
import { useNavigation } from '@apexnative/navigation';
import { Button, useTheme } from '../ui';

/**
 * An overlay that switches the app between light and dark while it shows: a card at the bottom with the scheme React
 * Native reports and its buttons. Its colors follow `useColorScheme`, as every screen's do; the overlay's window must
 * follow the app's `Appearance.setColorScheme`, not the system. Touches outside the card reach the screens below.
 */
export function SchemeOverlay() {
  const t = useTheme();
  const scheme = useColorScheme();
  const nav = useNavigation();
  return (
    <View pointerEvents="box-none" style={styles.fill}>
      <View style={[styles.card, { backgroundColor: t.card, borderColor: t.border }]}>
        <Text style={[styles.title, { color: t.text }]}>The overlay is {scheme}</Text>
        <View style={styles.buttons}>
          <View style={styles.button}>
            <Button title="Dark" onPress={() => Appearance.setColorScheme('dark')} />
          </View>
          <View style={styles.button}>
            <Button title="Light" onPress={() => Appearance.setColorScheme('light')} />
          </View>
          <View style={styles.button}>
            <Button title="System" onPress={() => Appearance.setColorScheme('unspecified')} />
          </View>
        </View>
        <Button title="Close" variant="primary" onPress={() => nav.dismiss()} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, justifyContent: 'flex-end', padding: 16, paddingBottom: 48 },
  card: { borderRadius: 24, borderWidth: StyleSheet.hairlineWidth, padding: 16, gap: 10 },
  title: { fontSize: 17, fontWeight: '700' },
  buttons: { flexDirection: 'row', gap: 8 },
  button: { flex: 1 },
});
