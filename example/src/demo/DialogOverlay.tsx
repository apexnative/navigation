import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Button, useTheme } from '../ui';

function Dialog() {
  const t = useTheme();
  const nav = useNavigation();
  const close = (answer: string) => {
    log('event', 'Dialog', answer);
    nav.dismiss();
  };
  return (
    <View style={styles.dim}>
      <View style={[styles.card, { backgroundColor: t.card }]}>
        <Text style={[styles.title, { color: t.text }]}>Delete this photo?</Text>
        <Text style={[styles.text, { color: t.textMuted }]}>It goes from all your devices.</Text>
        <View style={styles.buttons}>
          <View style={styles.button}>
            <Button title="Cancel" onPress={() => close('Cancel')} />
          </View>
          <View style={styles.button}>
            <Button title="Delete" variant="danger" onPress={() => close('Delete')} />
          </View>
        </View>
      </View>
    </View>
  );
}

/**
 * A dialog as an overlay: a dimmed screen with a card. The overlay takes every touch while it shows. Its dimming covers
 * the whole window, under the status bar and the home indicator too: no safe area edges.
 */
export const DialogOverlay = defineScreen({
  component: Dialog,
  options: (s) => s.safeArea(),
});

const styles = StyleSheet.create({
  dim: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
  },
  card: { width: 300, borderRadius: 24, padding: 20, gap: 8 },
  title: { fontSize: 18, fontWeight: '700' },
  text: { fontSize: 15, lineHeight: 21 },
  buttons: { flexDirection: 'row', gap: 10, marginTop: 8 },
  button: { flex: 1 },
});
