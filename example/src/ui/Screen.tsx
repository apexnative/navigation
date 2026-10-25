import React, { ReactNode } from 'react';
import { Platform, ScrollView, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import { useSafeAreaInsets } from '@apexnative/navigation';

/**
 * The body of a screen: scrolls under large titles. The library keeps it out of the safe area of the screen, but for
 * the bottom: the screen goes down to the bottom edge, and the last rows keep clear of what covers it (the home
 * indicator, the navigation bar, tabs that hide on scroll). iOS keeps them clear with the content inset, on Android
 * they end above it here. `underBar`: the screen is under its top bar (`hideOnScroll`, a large title); iOS keeps the
 * first rows clear of the bar with the content inset, on Android they start below it here. `aboveFooter`: a `Footer`
 * under the screen keeps clear of the bottom itself. The color behind it is the screen's (`backgroundColor`, the
 * theme's by default).
 */
export function Screen({
  children,
  scroll = true,
  underBar = false,
  aboveFooter = false,
  style,
}: {
  children: ReactNode;
  scroll?: boolean;
  underBar?: boolean;
  aboveFooter?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const insets = useSafeAreaInsets();
  // The sides the screen leaves to its content (the vertical bar of iPhone Duo): the rows keep clear of them here.
  const sides = { paddingLeft: 16 + insets.left, paddingRight: 16 + insets.right };
  const bottomInset = aboveFooter ? 0 : insets.bottom;
  const top = underBar && Platform.OS === 'android' ? { paddingTop: 16 + insets.top } : null;
  const bottom = Platform.OS === 'android' ? { paddingBottom: 56 + bottomInset } : null;
  if (!scroll) {
    return (
      <View style={[styles.flex, sides, { paddingBottom: bottomInset }, style]}>{children}</View>
    );
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
