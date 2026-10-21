import React, { ReactNode } from 'react';
import { Platform, ScrollView, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { useSafeAreaInsets } from '@apexnative/navigation';

/**
 * The body of a screen: scrolls under large titles. The library keeps it out of the safe area of the screen. `underBar`:
 * the screen is under its top bar (`hideOnScroll`, a large title); iOS keeps the first rows clear of the bar with the
 * content inset, on Android they start below it here. `overTabs`: the screen is over its tabs (`bottomTabs.hideOnScroll`), the last rows
 * end above them on Android. The color behind it is the screen's (`backgroundColor`, the theme's by default).
 */
export function Screen({
  children,
  scroll = true,
  underBar = false,
  overTabs = false,
  style,
}: {
  children: ReactNode;
  scroll?: boolean;
  underBar?: boolean;
  overTabs?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const insets = useSafeAreaInsets();
  // The sides the screen leaves to its content (the vertical bar of iPhone Duo): the rows keep clear of them here.
  const sides = { paddingLeft: 16 + insets.left, paddingRight: 16 + insets.right };
  const top = underBar && Platform.OS === 'android' ? { paddingTop: 16 + insets.top } : null;
  const bottom =
    overTabs && Platform.OS === 'android' ? { paddingBottom: 56 + insets.bottom } : null;
  if (!scroll) {
    return <View style={[styles.flex, sides, style]}>{children}</View>;
  }
  return (
    <ScrollView
      style={styles.flex}
      contentContainerStyle={[styles.content, sides, top, bottom, style]}
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { paddingTop: 16, paddingBottom: 56 },
});
