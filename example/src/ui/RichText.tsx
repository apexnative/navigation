import React from 'react';
import { StyleProp, StyleSheet, Text, TextStyle } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** Text where `backticks` mark code. */
export function RichText({
  children,
  style,
  numberOfLines,
}: {
  children: string;
  style?: StyleProp<TextStyle>;
  numberOfLines?: number;
}) {
  const t = useTheme();
  return (
    <Text style={[styles.text, { color: t.body }, style]} numberOfLines={numberOfLines}>
      {children.split('`').map((part, index) =>
        index % 2 === 1 ? (
          <Text key={index} style={[styles.code, { backgroundColor: t.code, color: t.text }]}>
            {part}
          </Text>
        ) : (
          part
        )
      )}
    </Text>
  );
}

const styles = StyleSheet.create({
  text: { fontFamily: fonts.body, fontSize: 14.5, lineHeight: 22 },
  code: { fontFamily: fonts.mono, fontSize: 12.5 },
});
