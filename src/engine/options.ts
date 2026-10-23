import { DynamicColorIOS, Image, Platform, processColor } from 'react-native';
import type { ColorValue, ImageSourcePropType } from 'react-native';
import { nextId } from './ids';
import { rememberButtonRoot, rememberOptionsComponent, setProps } from './props';

type Fields = Record<string, unknown>;

/**
 * The options as the native side reads them: colors in the format of the platform, images resolved, and every
 * component in them with an id of its own (its props stay in JavaScript). Options come in their own form only:
 * animations with `enter` and `exit`, `searchBar` and `interpolation` as objects. `owner` is the id of the node the
 * options are for, or `'defaultOptions'`: a component without an id of its own gets one made of the owner, its place
 * in the options and its name, the same for every `mergeOptions` of the node, so the native side keeps the mounted
 * component and only its props change. Without an owner each conversion gives it a new one.
 */
export function toNativeOptions<T>(options: T, owner?: string): T {
  return convert(options, undefined, { owner, path: '' }) as T;
}

// Where a value is in the options being converted: the node they are for, and the path to the value
// (`topBar.rightButtons[0].component`).
interface Place {
  owner: string | undefined;
  path: string;
}

/**
 * Options as the native side reads them over others, merged as the native side merges them: groups key by key, while
 * a color or an image, an object here too, replaces what was there as any value does. `undefined` keeps it.
 */
export function mergeNativeOptions<T extends object>(base: T, over: T): T {
  const result = { ...base } as Fields;
  for (const [key, next] of Object.entries(over)) {
    if (next === undefined) continue;
    const previous = result[key];
    result[key] =
      !isColor(key) && !isImage(key) && isGroup(previous) && isGroup(next)
        ? mergeNativeOptions(previous, next)
        : next;
  }
  return result as T;
}

function isGroup(value: unknown): value is Fields {
  return isFields(value) && !Array.isArray(value);
}

function convert(value: unknown, key: string | undefined, place: Place): unknown {
  if (key !== undefined && isColor(key)) return nativeColor(value);
  if (key !== undefined && isImage(key)) return nativeImage(value);
  if (Array.isArray(value)) {
    // An item is known by its id where it has one (a button, a suggestion), so a component keeps its root when the
    // list around it changes; by its place in the list otherwise.
    const items = value.map((item, index) => {
      const id = isFields(item) && typeof item.id === 'string' && item.id ? item.id : index;
      return convert(item, undefined, { ...place, path: `${place.path}[${id}]` });
    });
    if (key?.endsWith('Buttons') || key === 'buttons') items.forEach(noteButtonRoot);
    return key?.endsWith('Buttons') ? items.map(takeButtonProps) : items;
  }
  if (!isFields(value)) return value;
  const result: Fields = {};
  for (const [name, item] of Object.entries(withSymbols(value))) {
    // The props of a component are its own, not options.
    const path = place.path ? `${place.path}.${name}` : name;
    result[name] = name === 'passProps' ? item : convert(item, name, { ...place, path });
  }
  return key === 'component' || key === 'resultsComponent'
    ? takeComponentProps(result, place)
    : result;
}

function isColor(key: string): boolean {
  return (key === 'color' || key.endsWith('Color')) && key !== 'bkgColor';
}

function isImage(key: string): boolean {
  return key === 'icon' || key === 'image' || key.endsWith('Icon') || key.endsWith('Image');
}

// The SF Symbol of an icon goes in the field the native side draws symbols from (a tab draws its filled variant).
const SYMBOL_FIELDS = { icon: 'sfSymbol', selectedIcon: 'sfSelectedSymbol' } as const;

/** An icon for each platform (`{ ios, android }`), as opposed to an image source. */
function isPlatformIcon(value: unknown): value is { ios?: unknown; android?: unknown } {
  return (
    isFields(value) &&
    !Array.isArray(value) &&
    !('uri' in value) &&
    ('ios' in value || 'android' in value)
  );
}

/** The fields with the icon of this platform in place of each `{ ios, android }` icon. */
function withSymbols(fields: Fields): Fields {
  let result = fields;
  for (const [field, symbolField] of Object.entries(SYMBOL_FIELDS)) {
    const icon = fields[field];
    if (!isPlatformIcon(icon)) continue;
    const own = Platform.OS === 'ios' ? icon.ios : icon.android;
    result = { ...result };
    delete result[field];
    if (own === undefined) continue;
    if (Platform.OS === 'ios' && typeof own === 'string') result[symbolField] = own;
    else result[field] = own;
  }
  return result;
}

// An image: a source to resolve, the name of an Android drawable, or an icon for each platform.
function nativeImage(value: unknown): unknown {
  if (!value || typeof value === 'string') return value;
  if (isPlatformIcon(value)) {
    const own = Platform.OS === 'ios' ? value.ios : value.android;
    if (own === undefined) return undefined;
    if (typeof own === 'string') return Platform.OS === 'ios' ? { system: own } : own;
    return Image.resolveAssetSource(own as ImageSourcePropType);
  }
  return Image.resolveAssetSource(value as ImageSourcePropType);
}

function isFields(value: unknown): value is Fields {
  return typeof value === 'object' && value !== null;
}

// A component in the options gets an id; the native side creates it with that id, and its props wait here. Without
// an id of its own it is the owner's, the place of the component (`topBar.title`, `topBar.rightButtons[Save]` by the
// button's id, `topBar.searchBar.resultsComponent`) and its name: another name there is another component.
function takeComponentProps({ passProps, ...component }: Fields, place: Place): Fields {
  const componentId = (component.id as string | undefined) || componentIdAt(place, component.name);
  if (passProps) setProps(componentId, passProps as object);
  return { ...component, componentId };
}

function componentIdAt({ owner, path }: Place, name: unknown): string {
  if (owner === undefined) return nextId('CustomComponent');
  const at = path.endsWith('.component') ? path.slice(0, -'.component'.length) : path;
  const id = `${owner}/${at}/${String(name)}`;
  rememberOptionsComponent(owner, id);
  return id;
}

// The component of a bar button (the top bar's `*Buttons`, the toolbar's `buttons`) is a root the native side only
// sizes: its wrapper centers the content in the button's slot.
function noteButtonRoot(button: unknown): void {
  const component = isFields(button) ? button.component : undefined;
  if (isFields(component) && typeof component.componentId === 'string') {
    rememberButtonRoot(component.componentId);
  }
}

// The props of a button with a component of its own go with the button's id.
function takeButtonProps(button: unknown): unknown {
  if (!isFields(button) || !button.passProps || !button.id) return button;
  const { passProps, ...rest } = button;
  setProps(button.id as string, passProps as object);
  return rest;
}

function nativeColor(value: unknown): unknown {
  if (value === undefined) return undefined;
  return Platform.OS === 'ios' ? iosColor(value) : androidColor(value);
}

// One color, a pair for the light and the dark appearance, or a semantic color; null takes the color away.
function iosColor(value: unknown): unknown {
  if (value === null) return 'NoColor';
  if (!isFields(value)) return processColor(value as ColorValue);
  if ('semantic' in value) return value;
  const pair = ('dynamic' in value ? value.dynamic : value) as {
    light: ColorValue;
    dark: ColorValue;
  };
  return DynamicColorIOS({
    light: processColor(pair.light) as ColorValue,
    dark: processColor(pair.dark) as ColorValue,
  });
}

// Android always takes a pair; one color goes to both, and the side without a color has none.
function androidColor(value: unknown): unknown {
  const pair: Fields = { dark: 'NoColor', light: 'NoColor' };
  if (value === null) return pair;
  if (!isFields(value)) {
    const color = processColor(value as ColorValue);
    return { dark: color, light: color };
  }
  if ('semantic' in value || 'resource_paths' in value) return value;
  for (const [appearance, color] of Object.entries(value)) {
    pair[appearance] = processColor(color as ColorValue);
  }
  return pair;
}
