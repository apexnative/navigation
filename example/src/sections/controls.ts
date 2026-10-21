import type { PlatformName } from '../ui';

interface ControlBase {
  /** An option of one platform: it shows with a badge, and the other platform ignores it. */
  platform?: PlatformName;
}

/**
 * How the value of one option is picked on the scenario screen and on Details. Unset, the option is not sent.
 * - `oneOf`: one of the values; a toggle is `oneOf([true, false])`.
 * - `presets`: one of named values, for options that take objects: menus, components.
 * - `text`: typed text.
 * - `color`: one of the colors of the palette.
 */
export type Control = ControlBase &
  (
    | { kind: 'oneOf'; values: readonly unknown[]; initial?: unknown }
    | { kind: 'presets'; presets: Readonly<Record<string, unknown>>; initial?: string }
    | { kind: 'text'; initial?: string }
    | { kind: 'color'; initial?: string }
  );

/** The controls of a scenario by the path of the option each one sets, such as `topBar.subtitle.text`. */
export type Controls = Readonly<Record<string, Control>>;

/** The value of each control by its path: a preset by its name. */
export type ControlValues = Readonly<Record<string, unknown>>;

interface ControlOptions<Value> extends ControlBase {
  /** The value it starts with; without it the option starts unset. */
  initial?: Value;
}

export function oneOf<Value>(
  values: readonly Value[],
  options: ControlOptions<Value> = {}
): Control {
  return { kind: 'oneOf', values, ...options };
}

export function presets(
  named: Readonly<Record<string, unknown>>,
  options: ControlOptions<string> = {}
): Control {
  return { kind: 'presets', presets: named, ...options };
}

export function text(options: ControlOptions<string> = {}): Control {
  return { kind: 'text', ...options };
}

export function color(options: ControlOptions<string> = {}): Control {
  return { kind: 'color', ...options };
}

/** The values the controls start with. */
export function initialValues(controls: Controls): ControlValues {
  const values: Record<string, unknown> = {};
  for (const [path, control] of Object.entries(controls)) {
    if (control.initial !== undefined) values[path] = control.initial;
  }
  return values;
}
