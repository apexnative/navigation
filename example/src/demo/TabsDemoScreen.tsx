import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, navigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen, useTheme } from '../ui';

const ROWS = Array.from({ length: 30 }, (_, index) => `Row ${index + 1}`);

/**
 * A tab of the demo app the Tabs panel shows: the tab bar has the options of the panel. Rows to scroll, for the tab
 * bar that minimizes; a button back to the Showcase. With tabs that hide on scroll, Push in the bar: tabs hidden by
 * the rows here show again on the screen it shows.
 */
function TabsDemo({ title, tabsHideOnScroll }: { title: string; tabsHideOnScroll?: boolean }) {
  const t = useTheme();
  return (
    <Screen underBar overTabs={tabsHideOnScroll}>
      <Card title={`The ${title} tab`}>
        <Muted>A tab of a demo app, with the options of the Tabs panel.</Muted>
        <Button
          title="Back to the Showcase"
          variant="primary"
          onPress={() => navigation.switchTo('showcase')}
        />
      </Card>
      <View style={[styles.rows, { backgroundColor: t.card, borderColor: t.border }]}>
        {ROWS.map((row) => (
          <Text key={row} style={[styles.row, { color: t.text, borderBottomColor: t.border }]}>
            {row}
          </Text>
        ))}
      </View>
    </Screen>
  );
}

export const TabsDemoScreen = defineScreen({
  component: TabsDemo,
  options: (s, props: { title: string; tabsHideOnScroll?: boolean }) => {
    const screen = s.title(props.title).largeTitle();
    return props.tabsHideOnScroll
      ? screen.rightButtons({
          id: 'tabs-demo-push',
          text: 'Push',
          onPress: () => navigation.push('Details'),
        })
      : screen;
  },
});

const styles = StyleSheet.create({
  rows: { borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  row: {
    paddingVertical: 14,
    paddingHorizontal: 16,
    fontSize: 15,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
});
