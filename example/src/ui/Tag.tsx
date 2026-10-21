import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { brand, useTheme } from './theme';

type Look = 'dark' | 'soft' | 'outline' | 'gold' | 'space';

/** A short fact in a pill: a count, the platforms, a topic; `small` for topics under a title. */
export function Tag({
  label,
  look = 'outline',
  small = false,
}: {
  label: string;
  look?: Look;
  small?: boolean;
}) {
  const t = useTheme();
  const colors = {
    dark: {
      bg: t.dark ? brand.cream : brand.navy,
      fg: t.dark ? brand.deep : brand.cream,
      border: 'transparent',
    },
    soft: { bg: t.code, fg: t.body, border: 'transparent' },
    outline: { bg: 'transparent', fg: t.textMuted, border: t.outline },
    gold: { bg: brand.mustard, fg: brand.deep, border: 'transparent' },
    // On the space card, which is dark in both themes.
    space: { bg: 'transparent', fg: '#D9CFB8', border: '#3A4766' },
  }[look];
  return (
    <View
      style={[
        small ? styles.small : styles.tag,
        { backgroundColor: colors.bg, borderColor: colors.border },
      ]}
    >
      <Text style={[small ? styles.smallText : styles.text, { color: colors.fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  tag: { borderRadius: 999, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 3 },
  text: { fontFamily: fonts.mono, fontSize: 11 },
  small: { borderRadius: 999, borderWidth: 1, paddingHorizontal: 7, paddingVertical: 1 },
  smallText: { fontFamily: fonts.mono, fontSize: 10 },
});
