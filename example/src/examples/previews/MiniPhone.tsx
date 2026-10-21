import React, { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { useTheme } from '../../ui';

/** A small phone that frames the preview of an example. */
export function MiniPhone({
  background = '#F4ECDC',
  children,
}: {
  background?: string;
  children: ReactNode;
}) {
  const t = useTheme();
  return (
    <View
      style={[styles.phone, { backgroundColor: background, borderColor: t.text }]}
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  phone: { width: 92, height: 168, borderWidth: 3, borderRadius: 20, overflow: 'hidden' },
});
