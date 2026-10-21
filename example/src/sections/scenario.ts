import type { PlatformName } from '../ui';
import type { Control, Controls } from './controls';

/** A group of options of a section's panel, as its file writes it. */
export interface ScenarioDefinition {
  /** `section.number`, the same everywhere. */
  id: string;
  title: string;
  /** What to check; `backticks` mark code. */
  check: string;
  /** The options it sets, by their path: the panel opens Details with them, and Details changes them as it shows. */
  controls?: Controls;
  /** Buttons for what the controls don't cover, by title. */
  runs?: Record<string, () => unknown>;
  /** A feature of one platform. */
  platform?: PlatformName;
  /** A limitation known now. */
  known?: string;
  /** API names and words Search finds it by, besides its text. */
  keywords?: string[];
}

export interface Scenario extends ScenarioDefinition {
  /** The source of every run, by title, as it is in the file (babel/scenario-source.js puts it here). */
  code: Record<string, string>;
  /** The file of the group, from `example/`. */
  file: string;
}

/** A group of a section. The build adds the source of its runs, so the app shows the code they run. */
export function defineScenario(definition: ScenarioDefinition): Scenario {
  const built = definition as Partial<Scenario>;
  return { ...definition, code: built.code ?? {}, file: built.file ?? '' };
}

export interface Section {
  id: number;
  title: string;
  intro: string;
  scenarios: Scenario[];
  /** Its options are for a modal: the panel has one Show button, which shows Details as a modal. */
  modalOnly?: boolean;
  /** Its groups only have buttons of their own: the panel shows no Details and no code of options. */
  runsOnly?: boolean;
  /** Its options are for a layout of its own: Show switches to a demo app of tabs, with side menus or in columns. */
  demo?: 'tabs' | 'sideMenu' | 'splitView';
}

/**
 * Every control of a section: an option in two groups is one option, with one value. The presets of an option that
 * several groups show, each its own, are one list: a pick in one group replaces a pick in another.
 */
export function sectionControls(section: Section): Controls {
  const all: Record<string, Control> = {};
  for (const scenario of section.scenarios) {
    for (const [path, control] of Object.entries(scenario.controls ?? {})) {
      const known = all[path];
      all[path] =
        known?.kind === 'presets' && control.kind === 'presets'
          ? { ...known, presets: { ...known.presets, ...control.presets } }
          : control;
    }
  }
  return all;
}
