import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { brand, fonts, kindColors, useTheme } from '../ui';
import type { JournalEntry } from './journal';

/** An entry of the journal on its timeline: a dot and a tag in the color of its kind; an error in a card. */
export function JournalEntryRow({ entry }: { entry: JournalEntry }) {
  const t = useTheme();
  const color = kindColors[entry.kind];
  const title = <Text style={[styles.title, { color: t.text }]}>{entry.title}</Text>;
  return (
    <View style={styles.entry}>
      <View style={[styles.dot, { backgroundColor: color, borderColor: t.background }]} />
      <View style={styles.meta}>
        <Text style={[styles.time, { color: t.time }]}>{entry.time}</Text>
        <View style={[styles.kind, { backgroundColor: color }]}>
          <Text style={styles.kindText}>{entry.kind.toUpperCase()}</Text>
        </View>
      </View>
      {entry.kind === 'error' ? (
        <View
          style={[
            styles.error,
            { backgroundColor: t.card, borderColor: t.errorBorder, borderLeftColor: color },
          ]}
        >
          {title}
          {entry.detail ? (
            <Text style={[styles.errorDetail, { color: t.errorText }]} numberOfLines={4}>
              {entry.detail}
            </Text>
          ) : null}
        </View>
      ) : (
        <>
          {title}
          {entry.detail ? (
            <Text style={[styles.detail, { color: t.textMuted }]} numberOfLines={3}>
              {entry.detail}
            </Text>
          ) : null}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  entry: { paddingLeft: 26, paddingBottom: 18 },
  dot: {
    position: 'absolute',
    left: 0,
    top: 3,
    width: 12,
    height: 12,
    borderRadius: 6,
    borderWidth: 2,
  },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 },
  time: { fontFamily: fonts.mono, fontSize: 11 },
  kind: { borderRadius: 6, paddingHorizontal: 7, paddingVertical: 2 },
  kindText: { fontFamily: fonts.mono, fontSize: 10, letterSpacing: 0.8, color: brand.deep },
  title: { fontFamily: fonts.mono, fontSize: 14 },
  detail: { fontFamily: fonts.body, fontSize: 13, lineHeight: 18, marginTop: 3 },
  error: {
    borderWidth: 1,
    borderLeftWidth: 3,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  errorDetail: { fontFamily: fonts.mono, fontSize: 12, lineHeight: 17, marginTop: 4 },
});
