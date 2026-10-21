import React, { ReactNode } from 'react';
import { DimensionValue, StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { Raised } from './Raised';
import { RetroIcon } from './RetroIcon';
import { brand, useTheme } from './theme';

// A few stars in the card, by their place in it.
const STARS: {
  left: DimensionValue;
  top: DimensionValue;
  size: number;
  opacity: number;
  gold?: boolean;
}[] = [
  { left: '50%', top: '10%', size: 2, opacity: 0.7 },
  { left: '84%', top: '20%', size: 3, opacity: 0.5 },
  { left: '70%', top: '84%', size: 2, opacity: 0.6 },
  { left: '92%', top: '66%', size: 2, opacity: 1, gold: true },
];

/**
 * The space card at the top of a screen: the porthole of the package, a title and what follows; `compact` for a card of
 * facts, as on Settings.
 */
export function Hero({
  title,
  subtitle,
  badge,
  icon = 'navigation',
  compact = false,
  children,
}: {
  title: string;
  subtitle?: string;
  /** A short fact in a gold pill. */
  badge?: string;
  icon?: 'navigation' | 'apexnative';
  compact?: boolean;
  children?: ReactNode;
}) {
  const t = useTheme();
  return (
    <Raised
      color={brand.coral}
      radius={compact ? 24 : 26}
      offset={compact ? 5 : 6}
      style={styles.wrapper}
    >
      <View style={[styles.hero, compact && styles.compact, { backgroundColor: t.space }]}>
        {STARS.map((star, index) => (
          <View
            key={index}
            style={[
              styles.star,
              {
                left: star.left,
                top: star.top,
                width: star.size,
                height: star.size,
                borderRadius: star.size / 2,
                opacity: star.opacity,
                backgroundColor: star.gold ? brand.mustard : brand.cream,
              },
            ]}
          />
        ))}
        <RetroIcon kind={icon} size={compact ? 60 : 72} />
        <View style={styles.body}>
          <Text style={[styles.title, compact && styles.compactTitle]}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
          {badge ? (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{badge}</Text>
            </View>
          ) : null}
          {children}
        </View>
      </View>
    </Raised>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginBottom: 8 },
  hero: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    borderRadius: 26,
    padding: 18,
    overflow: 'hidden',
  },
  compact: { borderRadius: 24, padding: 16, gap: 14 },
  star: { position: 'absolute' },
  body: { flex: 1 },
  title: { fontFamily: fonts.display, fontSize: 21, lineHeight: 25, color: brand.cream },
  compactTitle: { fontSize: 22, lineHeight: 27 },
  subtitle: {
    fontFamily: fonts.body,
    fontSize: 13.5,
    lineHeight: 19.5,
    color: '#D9CFB8',
    marginTop: 6,
  },
  badge: {
    alignSelf: 'flex-start',
    marginTop: 10,
    borderRadius: 999,
    paddingHorizontal: 9,
    paddingVertical: 4,
    backgroundColor: brand.mustard,
  },
  badgeText: { fontFamily: fonts.mono, fontSize: 11, color: brand.deep },
});
