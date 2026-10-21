import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { Raised } from './Raised';
import { brand, useTheme } from './theme';

const COLORS = {
  key: brand.orange,
  string: '#7FE0C4',
  value: brand.mustard,
  comment: '#8C9AB5',
};

// Comments, strings, numbers and literals, and names before a colon.
const TOKEN =
  /(\/\/[^\n]*)|('(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\]|\\.)*`)|\b(\d+(?:\.\d+)?|true|false|null|undefined)\b|([A-Za-z_$][\w$]*)(?=\??:(?!:))/g;

/** The parts of code, colored by what they are. */
function highlight(code: string) {
  const parts: React.ReactNode[] = [];
  let last = 0;
  for (const match of code.matchAll(TOKEN)) {
    const index = match.index ?? 0;
    if (index > last) parts.push(code.slice(last, index));
    const color = match[1]
      ? COLORS.comment
      : match[2]
        ? COLORS.string
        : match[3]
          ? COLORS.value
          : COLORS.key;
    parts.push(
      <Text key={index} style={{ color }}>
        {match[0]}
      </Text>
    );
    last = index + match[0].length;
  }
  if (last < code.length) parts.push(code.slice(last));
  return parts;
}

/** A block of code on a dark card, raised on a coral shadow. */
export function Code({ children }: { children: string }) {
  const t = useTheme();
  return (
    <Raised color={brand.coral} radius={18}>
      <View style={[styles.code, { backgroundColor: t.space }]}>
        <Text style={styles.text} selectable>
          {highlight(children)}
        </Text>
      </View>
    </Raised>
  );
}

const styles = StyleSheet.create({
  code: { borderRadius: 18, paddingHorizontal: 16, paddingVertical: 14 },
  text: { fontFamily: fonts.mono, fontSize: 12.5, lineHeight: 21, color: brand.cream },
});
