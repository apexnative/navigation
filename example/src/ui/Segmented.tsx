import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

export interface Segment {
  key: string;
  label: string;
  selected: boolean;
  onPress: () => void;
}

/** One of a few short values, side by side; the selected one is filled. Values of code are in mono, words in text. */
export function Segmented({ segments, words = false }: { segments: Segment[]; words?: boolean }) {
  const t = useTheme();
  return (
    <View style={[styles.segmented, { backgroundColor: t.code }]}>
      {segments.map((segment) => (
        <Pressable
          key={segment.key}
          accessibilityRole="button"
          accessibilityState={{ selected: segment.selected }}
          onPress={segment.onPress}
          style={({ pressed }) => [
            styles.segment,
            {
              backgroundColor: segment.selected ? t.selected : 'transparent',
              opacity: pressed ? 0.7 : 1,
            },
          ]}
        >
          <Text
            style={[
              words ? styles.words : styles.label,
              { color: segment.selected ? t.onSelected : t.body },
            ]}
            numberOfLines={1}
          >
            {segment.label}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  segmented: { flexDirection: 'row', borderRadius: 12, padding: 3, gap: 3 },
  segment: {
    flex: 1,
    minHeight: 34,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  label: { fontFamily: fonts.mono, fontSize: 12.5 },
  words: { fontFamily: fonts.bodySemiBold, fontSize: 14 },
});
