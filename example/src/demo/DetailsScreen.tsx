import React, { useState } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { ControlsGroup } from '../showcase/controls/ControlsGroup';
import { takeOpened } from '../showcase/controls/openDetails';
import { buildOptions, setOptionsCode } from '../showcase/controls/optionsCode';
import { sectionControls } from '../sections/scenario';
import { Button, Card, Code, Muted, Screen, TextField, useTheme } from '../ui';

const ROWS = Array.from({ length: 40 }, (_, index) => `Row ${index + 1}`);

/**
 * The screen a panel shows, with its options. On it: the same options, to change while it shows; a way on and back; a
 * text field for the keyboard; and rows to scroll under the bar.
 */
function Details() {
  const t = useTheme();
  const nav = useNavigation();
  const [{ section, values: openedWith }] = useState(takeOpened);
  const [values, setValues] = useState(openedWith);
  const [last, setLast] = useState<string>();
  const [open, setOpen] = useState(() => new Set<string>());
  const controls = section ? sectionControls(section) : {};

  const change = (path: string, value: unknown) => {
    setValues((current) => ({ ...current, [path]: value }));
    if (value === undefined) {
      setLast('// Unset is not sent: the screen keeps what it has.');
      return;
    }
    // A path into the items of an array (`fab.actions[].iconColor`) needs the array: the whole option goes again.
    const root = path.split('.')[0];
    const changed = path.includes('[]')
      ? Object.fromEntries(Object.entries(controls).filter(([key]) => key.split('.')[0] === root))
      : { [path]: controls[path] };
    const options = buildOptions(changed, { ...values, [path]: value });
    nav.setOptions(options);
    setLast(setOptionsCode(options));
  };

  const toggle = (id: string) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  // A large title on Android puts the screen under the bar; `automatic` takes it from the section below, which has one.
  const largeTitleMode = values['topBar.largeTitle.displayMode'];
  const largeTitle =
    Platform.OS === 'android' &&
    (largeTitleMode === undefined
      ? values['topBar.largeTitle.visible'] === true
      : largeTitleMode === 'always' || largeTitleMode === 'automatic');

  return (
    <Screen
      underBar={
        values['topBar.hideOnScroll'] === true ||
        values['topBar.searchBar.hideOnScroll'] === true ||
        largeTitle
      }
    >
      {section ? (
        <Card title="Change while it shows">
          {section.scenarios.map((scenario) => (
            <ControlsGroup
              key={scenario.id}
              scenario={scenario}
              values={values}
              onChange={change}
              open={open.has(scenario.id)}
              onToggle={() => toggle(scenario.id)}
            />
          ))}
          {last ? <Code>{last}</Code> : <Muted>Pick a value: it goes to the screen at once.</Muted>}
        </Card>
      ) : null}
      <Card title="Around">
        <Button title="Push another Details" onPress={() => nav.push('Details')} />
        <Button title="Back" onPress={() => nav.pop()} />
        <Button title="Dismiss (in a modal)" onPress={() => nav.dismiss()} />
        <TextField placeholder="Type: the keyboard shows" />
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

export const DetailsScreen = defineScreen({
  component: Details,
  options: (s) => s.title('Details').hideBottomTabs(),
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
