import React, { ReactNode } from 'react';
import { StyleSheet, Text } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** Quiet text: hints, empty lists. */
export function Muted({ children }: { children: ReactNode }) {
  const t = useTheme();
  return <Text style={[styles.text, { color: t.textMuted }]}>{children}</Text>;
}

const styles = StyleSheet.create({
  text: { fontFamily: fonts.body, fontSize: 13, lineHeight: 19, paddingHorizontal: 4 },
});
