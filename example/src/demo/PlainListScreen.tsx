import React from 'react';
import { FlatList, StyleSheet, Text } from 'react-native';
import { defineScreen, useSafeAreaInsets } from '@apexnative/navigation';
import { useTheme } from '../ui';

const ROWS = Array.from({ length: 60 }, (_, index) => `Row ${index + 1}`);

/**
 * A screen that is a list and nothing else, as a plain UIKit table — on the system background, not the Showcase's: to
 * compare what scrolls with the system.
 */
function PlainList() {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const sides = { paddingLeft: 16 + insets.left, paddingRight: 16 + insets.right };
  return (
    <FlatList
      data={ROWS}
      keyExtractor={(row) => row}
      contentInsetAdjustmentBehavior="automatic"
      renderItem={({ item }) => (
        <Text style={[styles.row, sides, { color: t.text, borderBottomColor: t.border }]}>
          {item}
        </Text>
      )}
    />
  );
}

/**
 * The panel's List shows it with the options of the panel. Its bar is transparent, with no background of its own, so
 * the list goes under it, with the system's edge effect; nothing keeps it off the bottom edge, so it goes under a
 * bottom search field or toolbar too. The list keeps its rows clear of the bars itself.
 */
export const PlainListScreen = defineScreen({
  component: PlainList,
  options: (s) =>
    s
      .title('List')
      .hideBottomTabs()
      .topBar({ background: { color: 'transparent', translucent: true } })
      .layout({ componentBackgroundColor: { light: '#FFFFFF', dark: '#000000' } })
      .safeArea('left', 'right')
      // The list goes to the sides on iOS, as a UIKit table: its scroll bar stands at the edge, the rows keep clear of
      // the vertical bar of iPhone Duo.
      .ios((i) => i.safeArea()),
});

const styles = StyleSheet.create({
  row: {
    fontSize: 17,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
});
