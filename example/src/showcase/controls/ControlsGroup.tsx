import React from 'react';
import { StyleSheet, View } from 'react-native';
import type { ControlValues } from '../../sections/controls';
import type { Scenario } from '../../sections/scenario';
import { Button, Code, isOtherPlatform, Label, Muted, RichText, useTheme } from '../../ui';
import { ScenarioRow } from '../ScenarioRow';
import { ControlRow } from './ControlRow';

/**
 * A group of options on a panel, a row of its card: the row opens and closes it; open, it says what to check and has a
 * row for each option, and the buttons of what the options don't cover. A group of another platform is dimmed and
 * doesn't open.
 */
export function ControlsGroup({
  scenario,
  values,
  onChange,
  open,
  onToggle,
  onRun,
}: {
  scenario: Scenario;
  values: ControlValues;
  onChange: (path: string, value: unknown) => void;
  open: boolean;
  onToggle: () => void;
  /** Called before a run of the group, so Details knows where it comes from. */
  onRun?: () => void;
}) {
  const t = useTheme();
  const controls = Object.entries(scenario.controls ?? {});
  const set = controls.filter(([path]) => values[path] !== undefined).length;
  const runs = Object.entries(scenario.runs ?? {});
  const shown = open && !isOtherPlatform(scenario.platform);
  return (
    <View>
      <ScenarioRow
        scenario={scenario}
        onPress={onToggle}
        chevron={shown ? '▾' : '▸'}
        check={!shown}
        setCount={set}
      />
      {shown ? (
        <View style={styles.body}>
          <RichText style={styles.check}>{scenario.check}</RichText>
          {scenario.known ? <Muted>{scenario.known}</Muted> : null}
          {controls.length > 0 ? <Label style={styles.options}>Options</Label> : null}
          {controls.map(([path, control], index) => (
            <View key={path} style={index > 0 && { borderTopWidth: 1, borderColor: t.divider }}>
              <ControlRow
                path={path}
                control={control}
                value={values[path]}
                onChange={(value) => onChange(path, value)}
              />
            </View>
          ))}
          {runs.map(([title, run]) => (
            <View key={title} style={styles.run}>
              <Button
                title={title}
                onPress={() => {
                  onRun?.();
                  return run();
                }}
              />
              {scenario.code[title] ? <Code>{scenario.code[title]}</Code> : null}
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  body: { paddingHorizontal: 16, paddingBottom: 16, gap: 4 },
  check: { fontSize: 14.5, lineHeight: 22, marginBottom: 4 },
  options: { marginTop: 10, marginBottom: 0, marginLeft: 0 },
  run: { gap: 10, marginTop: 10 },
});
