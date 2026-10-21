import React, { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

/** Children in a row that wraps. */
export function Row({ children }: { children: ReactNode }) {
  return <View style={styles.row}>{children}</View>;
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center' },
});
