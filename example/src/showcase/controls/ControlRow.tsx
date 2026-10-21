import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { Control } from '../../sections/controls';
import {
  Chip,
  isOtherPlatform,
  PlatformBadge,
  Row,
  Segmented,
  Swatch,
  TextField,
  Toggle,
  useTheme,
} from '../../ui';
import { fonts } from '../../ui/fonts';
import { brand, swatches } from '../../ui/theme';

const COLORS = [...swatches, '#FFFFFF', brand.deep, 'transparent'];
const UNSET = '—';

/** An option that is on or off. */
function isToggle(control: Control) {
  return (
    control.kind === 'oneOf' &&
    control.values.length === 2 &&
    control.values.includes(true) &&
    control.values.includes(false)
  );
}

/** A few short values fit side by side in a segmented control, with the unset one first. */
function fitsSegments(values: readonly unknown[]) {
  const longest = { 1: 16, 2: 12, 3: 9, 4: 7 }[values.length];
  return longest !== undefined && values.every((value) => String(value).length <= longest);
}

function ValuePicker({
  control,
  value,
  onChange,
}: {
  control: Control;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const unset = (
    <Chip label={UNSET} selected={value === undefined} onPress={() => onChange(undefined)} />
  );
  switch (control.kind) {
    case 'oneOf':
      return fitsSegments(control.values) ? (
        <Segmented
          segments={[undefined, ...control.values].map((item) => ({
            key: String(item),
            label: item === undefined ? UNSET : String(item),
            selected: value === item,
            onPress: () => onChange(item),
          }))}
        />
      ) : (
        <Row>
          {unset}
          {control.values.map((item) => (
            <Chip
              key={String(item)}
              label={String(item)}
              code
              selected={value === item}
              onPress={() => onChange(item)}
            />
          ))}
        </Row>
      );
    case 'presets':
      return (
        <Row>
          {unset}
          {Object.keys(control.presets).map((name) => (
            <Chip
              key={name}
              label={name}
              selected={value === name}
              onPress={() => onChange(name)}
            />
          ))}
        </Row>
      );
    case 'text':
      return (
        <TextField
          placeholder="Not set"
          value={typeof value === 'string' ? value : ''}
          onChangeText={(text) => onChange(text === '' ? undefined : text)}
        />
      );
    case 'color':
      return (
        <Row>
          {unset}
          {COLORS.map((color) => (
            <Swatch
              key={color}
              color={color}
              selected={value === color}
              onPress={() => onChange(color)}
            />
          ))}
        </Row>
      );
  }
}

/**
 * One option on a panel: its path and its value. Unset, the option is not sent. An option that is on or off has a
 * switch in its row, and `—` to unset it. An option of another platform is dimmed and can't be picked.
 */
export function ControlRow({
  path,
  control,
  value,
  onChange,
}: {
  path: string;
  control: Control;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const t = useTheme();
  const other = isOtherPlatform(control.platform);
  if (isToggle(control)) {
    return (
      <View style={[styles.inline, other && styles.other]} pointerEvents={other ? 'none' : 'auto'}>
        <View style={[styles.header, styles.flex]}>
          <Text style={[styles.path, { color: t.label }]}>{path}</Text>
          {control.platform ? <PlatformBadge platform={control.platform} /> : null}
        </View>
        <Chip label={UNSET} selected={value === undefined} onPress={() => onChange(undefined)} />
        <Toggle label={path} on={value === true} onPress={() => onChange(value !== true)} />
      </View>
    );
  }
  return (
    <View style={[styles.row, other && styles.other]} pointerEvents={other ? 'none' : 'auto'}>
      <View style={styles.header}>
        <Text style={[styles.path, { color: t.label }]}>{path}</Text>
        {control.platform ? <PlatformBadge platform={control.platform} /> : null}
      </View>
      <ValuePicker control={control} value={value} onChange={onChange} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { gap: 8, paddingVertical: 12 },
  inline: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12 },
  flex: { flex: 1 },
  other: { opacity: 0.45 },
  header: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap' },
  path: { fontFamily: fonts.mono, fontSize: 11.5, flexShrink: 1 },
});
