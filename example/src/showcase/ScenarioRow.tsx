import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Scenario } from '../sections/scenario';
import {
  brand,
  fonts,
  IdChip,
  isOtherPlatform,
  platformLabel,
  RichText,
  Tag,
  useTheme,
} from '../ui';

/**
 * A scenario in a list: its number in a pill, its title, the start of what to check and its platform. On the right a
 * count of the options set and a chevron, or nothing. A scenario of another platform is dimmed and doesn't open.
 */
export function ScenarioRow({
  scenario,
  onPress,
  chevron,
  check = true,
  setCount = 0,
  testID,
}: {
  scenario: Scenario;
  onPress: () => void;
  chevron: string;
  /** The start of what to check, under the title. */
  check?: boolean;
  setCount?: number;
  testID?: string;
}) {
  const t = useTheme();
  const other = isOtherPlatform(scenario.platform);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: other }}
      disabled={other}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.row,
        { backgroundColor: pressed ? t.code : 'transparent' },
        other && styles.other,
      ]}
    >
      <IdChip id={scenario.id} />
      <View style={styles.body}>
        <Text style={[styles.title, { color: t.text }]}>{scenario.title}</Text>
        {check ? (
          <RichText style={[styles.check, { color: t.textMuted }]} numberOfLines={2}>
            {scenario.check}
          </RichText>
        ) : null}
        {scenario.platform ? (
          <View style={styles.badges}>
            <Tag small label={`${platformLabel(scenario.platform)} only`} />
          </View>
        ) : null}
      </View>
      {setCount > 0 ? (
        <View style={styles.count}>
          <Text style={styles.countText}>{setCount}</Text>
        </View>
      ) : null}
      {other ? null : <Text style={[styles.chevron, { color: t.chevron }]}>{chevron}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 13,
  },
  other: { opacity: 0.45 },
  body: { flex: 1 },
  title: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
  check: { fontSize: 13, lineHeight: 18, marginTop: 2 },
  badges: { flexDirection: 'row', gap: 6, marginTop: 7 },
  count: {
    minWidth: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: brand.mustard,
  },
  countText: { fontFamily: fonts.mono, color: brand.deep, fontSize: 12, paddingHorizontal: 6 },
  chevron: { fontSize: 18, lineHeight: 22, width: 14, textAlign: 'center' },
});
