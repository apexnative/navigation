import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, navigation, useNavigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen, TextField, useTheme } from '../ui';

const ROWS = Array.from({ length: 30 }, (_, index) => `Row ${index + 1}`);

/**
 * A tab of the demo app the Tabs panel shows: the tab bar has the options of the panel. Rows to scroll, for the tab
 * bar that minimizes; a button back to the Showcase. With tabs that hide on scroll, Push in the bar: tabs hidden by
 * the rows here show again on the screen it shows. With the custom row: a push that hides the tabs, a sheet and a modal
 * over them, a text field for the keyboard, a turn to landscape, and a badge on this tab that counts up.
 */
function TabsDemo({ title, customRow }: TabsDemoProps) {
  const t = useTheme();
  const nav = useNavigation();
  const [landscape, setLandscape] = useState(false);
  const [badge, setBadge] = useState(0);
  return (
    <Screen underBar>
      <Card title={`The ${title} tab`}>
        <Muted>A tab of a demo app, with the options of the Tabs panel.</Muted>
        <Button
          title="Back to the Showcase"
          variant="primary"
          onPress={() => navigation.switchTo('showcase')}
        />
        {customRow ? (
          <>
            <Button title="Push" onPress={() => navigation.push('Details')} />
            <Button
              title="A sheet"
              onPress={() =>
                navigation.showModal('Details', {
                  sheet: { detents: ['medium', 'large'] },
                  closeButton: true,
                })
              }
            />
            <Button
              title="A modal"
              onPress={() =>
                navigation.showModal('Details', { style: 'fullScreen', closeButton: true })
              }
            />
            <TextField placeholder="The keyboard" />
            <Button
              title="Badge"
              onPress={() => {
                setBadge(badge + 1);
                nav.setOptions({ bottomTab: { badge: String(badge + 1) } });
              }}
            />
            <Button
              title={landscape ? 'Portrait' : 'Landscape'}
              onPress={() => {
                setLandscape(!landscape);
                nav.setOptions({
                  layout: { orientation: [landscape ? 'portrait' : 'landscape'] },
                });
              }}
            />
          </>
        ) : null}
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

type TabsDemoProps = { title: string; tabsHideOnScroll?: boolean; customRow?: boolean };

export const TabsDemoScreen = defineScreen({
  component: TabsDemo,
  options: (s, props: TabsDemoProps) => {
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
