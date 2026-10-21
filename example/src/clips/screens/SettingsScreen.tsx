import React, { useState } from 'react';
import { ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { LIST_LAYOUT, useSidePadding } from '../insets';

const ROWS = ['Notifications', 'Sync', 'Dark icons', 'Sounds', 'Backups'];

/** Grouped switches, like the settings of any app. */
function Settings() {
  const c = useColors();
  const sides = useSidePadding();
  const [on, setOn] = useState<Record<string, boolean>>({ Notifications: true, Sync: true });
  return (
    <ScrollView
      contentContainerStyle={sides}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <View style={[styles.group, { backgroundColor: c.card, borderColor: c.line }]}>
        {ROWS.map((title) => (
          <View key={title} style={[styles.row, { borderBottomColor: c.line }]}>
            <Text style={[styles.title, { color: c.text }]}>{title}</Text>
            <Switch
              value={on[title] === true}
              onValueChange={(value) => setOn((current) => ({ ...current, [title]: value }))}
            />
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

export const SettingsScreen = defineScreen({
  component: Settings,
  options: (s) => s.title('Settings').layout(LIST_LAYOUT),
});

const styles = StyleSheet.create({
  group: { margin: 16, borderRadius: 22, borderWidth: 1, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: { fontFamily: fonts.body, fontSize: 17 },
});
