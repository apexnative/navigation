import React from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { defineScreen, navigation, useNavigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen, useTheme } from '../ui';

const ROWS = Array.from({ length: 30 }, (_, index) => `Row ${index + 1}`);

/**
 * A tab of the demo app the Tabs panel shows: the tab bar has the options of the panel. Rows to scroll, for the tab
 * bar that minimizes; a button back to the Showcase. With tabs that hide on scroll, Push in the bar: tabs hidden by
 * the rows here show again on the screen it shows. A push that keeps the tab bar, to switch tabs while it runs; on
 * Android, how a switch to this tab animates, merged into this screen's options (it decides while it is on top).
 */
function TabsDemo({ title }: { title: string; tabsHideOnScroll?: boolean }) {
  const t = useTheme();
  const nav = useNavigation();
  return (
    <Screen underBar>
      <Card title={`The ${title} tab`}>
        <Muted>A tab of a demo app, with the options of the Tabs panel.</Muted>
        <Button
          title="Push Details"
          onPress={() => nav.push('PlainDetails', { props: { title: 'Details' } })}
        />
        {Platform.OS === 'android' ? (
          <>
            <Button
              title="Switch to this tab at once"
              onPress={() => nav.setOptions({ bottomTabs: { switchAnimation: 'none' } })}
            />
            <Button
              title="Switch to this tab with the platform's animation"
              onPress={() => nav.setOptions({ bottomTabs: { switchAnimation: 'platform' } })}
            />
          </>
        ) : null}
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
