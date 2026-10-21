import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { IdChip } from './IdChip';
import { SectionBadge } from './SectionBadge';
import { useTheme } from './theme';

/**
 * A row of a list that opens something: a section shows its number in a porthole, anything else in a pill. A disabled
 * row is dimmed and opens nothing.
 */
export function ListRow({
  id,
  title,
  subtitle,
  onPress,
  porthole = false,
  disabled = false,
  testID,
}: {
  id: string | number;
  title: string;
  subtitle?: string;
  onPress: () => void;
  /** The number of a section. */
  porthole?: boolean;
  disabled?: boolean;
  testID?: string;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      testID={testID}
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: pressed ? t.code : t.card,
          opacity: disabled ? 0.45 : 1,
        },
      ]}
    >
      {porthole ? <SectionBadge id={Number(id)} /> : <IdChip id={id} />}
      <View style={styles.body}>
        <Text style={[styles.title, { color: t.text }]}>{title}</Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: t.textMuted }]} numberOfLines={2}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {disabled ? null : <Text style={[styles.chevron, { color: t.chevron }]}>›</Text>}
    </Pressable>
  );
}

/** Rows in one card, with lines between them. */
export function List({ children }: { children: React.ReactNode }) {
  const t = useTheme();
  const rows = React.Children.toArray(children).filter(Boolean);
  return (
    <View style={[styles.list, { backgroundColor: t.card, borderColor: t.border }]}>
      {rows.map((row, index) => (
        <View
          key={React.isValidElement(row) && row.key !== null ? row.key : index}
          style={index > 0 ? [styles.divided, { borderTopColor: t.divider }] : undefined}
        >
          {row}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { borderRadius: 22, overflow: 'hidden', borderWidth: 1 },
  divided: { borderTopWidth: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  body: { flex: 1, gap: 2 },
  title: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
  subtitle: { fontFamily: fonts.body, fontSize: 13, lineHeight: 18 },
  chevron: { fontSize: 22, lineHeight: 24 },
});
