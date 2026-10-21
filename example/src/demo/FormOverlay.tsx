import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, TextField, useTheme } from '../ui';

function Form() {
  const t = useTheme();
  const nav = useNavigation();
  return (
    <View style={styles.dim}>
      <View style={[styles.card, { backgroundColor: t.card }]}>
        <Text style={[styles.title, { color: t.text }]}>Rename the album</Text>
        <TextField placeholder="A new name" />
        <Button title="Done" variant="primary" onPress={() => nav.dismiss()} />
      </View>
    </View>
  );
}

/**
 * An overlay with a text field: a tap on the field gives it the keyboard, as on a screen. Its dimming covers the whole window: no
 * safe area edges.
 */
export const FormOverlay = defineScreen({
  component: Form,
  options: (s) => s.safeArea(),
});

const styles = StyleSheet.create({
  dim: {
    flex: 1,
    alignItems: 'center',
    paddingTop: 140,
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
  },
  card: { width: 320, borderRadius: 24, padding: 20, gap: 12 },
  title: { fontSize: 18, fontWeight: '700' },
});
