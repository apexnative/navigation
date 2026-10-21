import React, { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from '@apexnative/navigation';
import { useTheme } from './theme';

/**
 * A bar at the bottom of a screen, under its scrolling content: the actions of the screen, side by side. Its background
 * goes to the bottom edge, under a tab bar; the actions stay above it.
 */
export function Footer({ children }: { children: ReactNode }) {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <View
      style={[
        styles.footer,
        { backgroundColor: t.card, borderTopColor: t.border, paddingBottom: 12 + insets.bottom },
      ]}
    >
      {React.Children.map(children, (child) =>
        child ? <View style={styles.item}>{child}</View> : null
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  footer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    paddingHorizontal: 16,
    paddingTop: 12,
    borderTopWidth: 1,
  },
  item: { flex: 1 },
});
