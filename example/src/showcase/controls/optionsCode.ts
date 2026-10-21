import type { Options } from '@apexnative/navigation';
import type { Controls, ControlValues } from '../../sections/controls';
import { splitDemoColumns } from './splitDemoColumns';

/** `actions[]` in a path: the rest of the path goes on every item of that array, when there is one. */
function setPath(target: Record<string, unknown>, path: string, value: unknown) {
  setKeys(target, path.split('.'), value);
}

function setKeys(node: Record<string, unknown>, keys: string[], value: unknown) {
  const [key, ...rest] = keys;
  if (key.endsWith('[]')) {
    const name = key.slice(0, -2);
    const items = node[name];
    if (!Array.isArray(items)) return;
    node[name] = items.map((item) => {
      const copy = { ...(item as object) } as Record<string, unknown>;
      setKeys(copy, rest, value);
      return copy;
    });
    return;
  }
  if (rest.length === 0) {
    node[key] = value;
    return;
  }
  const next = node[key];
  node[key] = next !== null && typeof next === 'object' ? { ...(next as object) } : {};
  setKeys(node[key] as Record<string, unknown>, rest, value);
}

/** The options the set values make; a preset gives its value. Shorter paths go first, so deeper ones go into them. */
export function buildOptions(controls: Controls, values: ControlValues): Options {
  const options: Record<string, unknown> = {};
  const paths = Object.keys(controls).sort((a, b) => a.split('.').length - b.split('.').length);
  for (const path of paths) {
    const value = values[path];
    if (value === undefined) continue;
    const control = controls[path];
    setPath(options, path, control.kind === 'presets' ? control.presets[value as string] : value);
  }
  return options as Options;
}

const IDENTIFIER = /^[A-Za-z_$][\w$]*$/;

/** A value as TypeScript code, indented under `indent`. */
export function formatValue(value: unknown, indent = ''): string {
  if (typeof value === 'string') return `'${value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
  // A press handler: its code, which Hermes doesn't keep.
  if (typeof value === 'function') return (value as { code?: string }).code ?? '() => {}';
  const inner = `${indent}  `;
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]';
    return `[\n${value.map((item) => inner + formatValue(item, inner)).join(',\n')},\n${indent}]`;
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value);
    if (entries.length === 0) return '{}';
    const lines = entries.map(
      ([key, item]) =>
        `${inner}${IDENTIFIER.test(key) ? key : `'${key}'`}: ${formatValue(item, inner)}`
    );
    return `{\n${lines.join(',\n')},\n${indent}}`;
  }
  return String(value);
}

/** The code of the show the panel runs. */
export function showCode(kind: 'push' | 'modal', options: Options): string {
  const command = kind === 'push' ? 'push' : 'showModal';
  if (Object.keys(options).length === 0) return `navigation.${command}('Details')`;
  return `navigation.${command}('Details', {\n  options: ${formatValue(options, '  ')},\n})`;
}

/**
 * The code of the demo of tabs the Tabs panel shows: `bottomTabs` on the tab bar, the colors and font of `bottomTab` on
 * every tab, its badge, dot and symbol on the Inbox tab.
 */
export function showTabsCode(options: Options, everyTab: object, inbox: object): string {
  const tabs = options.bottomTabs ? `.options(${formatValue(options.bottomTabs, '  ')})` : '';
  const every = Object.keys(everyTab).length ? `.options(${formatValue(everyTab, '    ')})` : '';
  const inboxOnly = Object.keys(inbox).length ? `.options(${formatValue(inbox, '    ')})` : '';
  return `navigation.switchTo(\n  Tabs(\n    Tab(Stack('Home')).label('Home')${every},\n    Tab(Stack('Inbox')).label('Inbox')${every}${inboxOnly},\n    Tab(Stack('Profile')).label('Profile')${every},\n  )${tabs}\n)`;
}

/** The code of the side menu demo the Side menu panel shows. */
export function showSideMenuCode(options: Options): string {
  const sideMenu = options.sideMenu ? `.options(${formatValue(options.sideMenu, '  ')})` : '';
  return `navigation.switchTo(\n  SideMenu({\n    left: Screen('Menu'),\n    center: Stack(Screen('Home')),\n    right: Screen('Menu'),\n  })${sideMenu}\n)`;
}

/** The code of the split view demo the Split view panel shows. */
export function showSplitViewCode(options: Options): string {
  const split = options.splitView ?? {};
  const columns = splitDemoColumns(split);
  const lines = [
    "    primary: Screen('Library'),",
    columns.supplementary ? "    supplementary: Screen('Items')," : '',
    "    secondary: Screen('Details'),",
    columns.inspector ? "    inspector: Screen('Info')," : '',
    columns.compact ? "    compact: Stack(Screen('Library'))," : '',
  ].filter(Boolean);
  const splitOptions = options.splitView ? `.options(${formatValue(options.splitView, '  ')})` : '';
  return `navigation.switchTo(\n  SplitView({\n${lines.join('\n')}\n  })${splitOptions}\n)`;
}

/** The code of a change on Details. */
export function setOptionsCode(options: Options): string {
  return `screen.setOptions(${formatValue(options)})`;
}
