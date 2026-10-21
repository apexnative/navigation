import React from 'react';
import { StyleProp, StyleSheet, Text, TextStyle } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** The name of a group, in capitals above it. */
export function Label({ children, style }: { children: string; style?: StyleProp<TextStyle> }) {
  const t = useTheme();
  return <Text style={[styles.label, { color: t.label }, style]}>{children.toUpperCase()}</Text>;
}

const styles = StyleSheet.create({
  label: {
    fontFamily: fonts.mono,
    fontSize: 11,
    letterSpacing: 1.5,
    marginBottom: 8,
    marginLeft: 4,
  },
});
