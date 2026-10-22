import React from 'react';
import { Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';

/** Details as an app writes it at first: a centered `Text`, no background, no options. */
function PlainDetails({ title }: { title?: string }) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>{title ?? 'Details'}</Text>
    </View>
  );
}

export const PlainDetailsScreen = defineScreen({ component: PlainDetails });

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: Platform.OS === 'ios' ? PlatformColor('label') : 'red',
  },
});
