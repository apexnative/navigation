import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, Card, Muted, Screen, useTheme } from '../ui';

const ROWS = Array.from({ length: 20 }, (_, index) => `Row ${index + 1}`);

/** The center of the side menu demo: buttons that open the menus, and one back to the Showcase. */
function SideMenuCenter() {
  const t = useTheme();
  const nav = useNavigation();
  return (
    <Screen>
      <Card title="A side menu">
        <Muted>The menus have the options of the Side menu panel. Swipe from an edge, or:</Muted>
        <Button title="Open the left menu" onPress={() => nav.openMenu('left')} />
        <Button title="Open the right menu" onPress={() => nav.openMenu('right')} />
        <Button
          title="Back to the Showcase"
          variant="primary"
          onPress={() => nav.switchTo('showcase')}
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

export const SideMenuCenterScreen = defineScreen({
  component: SideMenuCenter,
  options: (s) => s.title('Home'),
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
