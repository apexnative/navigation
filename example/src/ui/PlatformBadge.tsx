import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { fonts } from './fonts';
import { platformLabel, type PlatformName } from './platform';
import { useTheme } from './theme';

/** "iOS", "iPad" or "Android", for a feature of one platform. */
export function PlatformBadge({ platform }: { platform: PlatformName }) {
  const t = useTheme();
  return (
    <View style={[styles.badge, { borderColor: t.outline }]}>
      <Text style={[styles.text, { color: t.textMuted }]}>{platformLabel(platform)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 7,
    paddingVertical: 2,
    marginLeft: 8,
  },
  text: { fontFamily: fonts.mono, fontSize: 10 },
});
