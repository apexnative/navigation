import React, { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { Label } from './Label';
import { useTheme } from './theme';

/** A card with its name in capitals above it. */
export function Card({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  const t = useTheme();
  return (
    <View style={styles.wrapper}>
      <Label>{title}</Label>
      {description ? (
        <Text style={[styles.description, { color: t.textMuted }]}>{description}</Text>
      ) : null}
      <View style={[styles.card, { backgroundColor: t.card, borderColor: t.border }]}>
        {children}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginTop: 22 },
  description: {
    fontFamily: fonts.body,
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 8,
    marginLeft: 4,
  },
  card: { borderRadius: 20, padding: 14, gap: 10, borderWidth: 1 },
});
