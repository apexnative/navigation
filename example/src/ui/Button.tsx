import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { PlatformBadge } from './PlatformBadge';
import { isOtherPlatform, type PlatformName } from './platform';
import { Raised } from './Raised';
import { brand, useTheme } from './theme';

type Variant = 'primary' | 'secondary' | 'danger';

/**
 * A button that logs the error of what it runs instead of throwing it: a coral pill on its shadow, a navy pill, or an
 * outline for what removes something.
 */
export function Button({
  title,
  subtitle,
  onPress,
  variant = 'secondary',
  platform,
  testID,
}: {
  title: string;
  subtitle?: string;
  onPress: () => unknown;
  variant?: Variant;
  /** A feature of one platform: the button is off on the other one. */
  platform?: PlatformName;
  testID?: string;
}) {
  const t = useTheme();
  const off = isOtherPlatform(platform);
  const look = {
    primary: { bg: brand.coral, fg: brand.deep, border: brand.coral, radius: 23 },
    secondary: {
      bg: t.dark ? '#2B3754' : brand.navy,
      fg: brand.cream,
      border: 'transparent',
      radius: 23,
    },
    danger: { bg: 'transparent', fg: t.danger, border: brand.red, radius: 14 },
  }[variant];
  const button = (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      disabled={off}
      onPress={() => {
        Promise.resolve()
          .then(onPress)
          .catch((error) => console.warn(`[${title}]`, error));
      }}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: look.bg,
          borderColor: look.border,
          borderRadius: look.radius,
          opacity: pressed ? 0.8 : 1,
        },
      ]}
    >
      <View style={styles.texts}>
        <Text style={[styles.title, { color: look.fg }]}>{title}</Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: look.fg }]} numberOfLines={2}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {platform ? <PlatformBadge platform={platform} /> : null}
    </Pressable>
  );
  return (
    <View style={off && styles.off}>
      {variant === 'primary' ? (
        <Raised color={brand.coralDeep} radius={look.radius}>
          {button}
        </Raised>
      ) : (
        button
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  off: { opacity: 0.45 },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 46,
    borderWidth: 2,
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  texts: { flexShrink: 1, alignItems: 'center' },
  title: { fontFamily: fonts.bodySemiBold, fontSize: 15.5, textAlign: 'center' },
  subtitle: {
    fontFamily: fonts.body,
    fontSize: 12,
    lineHeight: 16,
    marginTop: 2,
    textAlign: 'center',
    opacity: 0.8,
  },
});
