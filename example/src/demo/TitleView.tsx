import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { palette } from '../ui/theme';

/** A component in place of the title: `topBar.title.component` names it. */
export function TitleView({ text = 'Title view' }: { text?: string }) {
  return (
    <View style={styles.pill}>
      <Text style={styles.text}>★ {text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    alignSelf: 'center',
    backgroundColor: palette.primary,
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 5,
  },
  text: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
});
