import type { Options } from '../interfaces/Options';

/** What an edit of a screen's options changed: the options to merge, and the paths of the options it took out. */
export interface OptionsChange {
  /** The options that are new or have another value; undefined when none. */
  merge?: Options;
  /** The paths of the options set before and not now, such as `['topBar', 'subtitle']`. */
  removed: string[][];
}

/**
 * What `next` changes of `previous`, both the options of one screen. Objects compare key by key; anything else, an
 * array such as buttons too, changes as a whole, and a function by identity (`sameValue`).
 */
export function optionsChange(previous: Options, next: Options): OptionsChange {
  const removed: string[][] = [];
  const merge = changed(previous, next, [], removed) as Options | undefined;
  return merge ? { merge, removed } : { removed };
}

function changed(previous: unknown, next: unknown, path: string[], removed: string[][]): unknown {
  if (isWholeValue(path) && isPlainObject(previous) && isPlainObject(next)) {
    if (sameValue(previous, next)) return undefined;
    // A part of it taken out: the native side may merge the parts, so the value goes first.
    if (Object.keys(previous).some((key) => next[key] === undefined)) removed.push(path);
    return next;
  }
  if (!isPlainObject(previous) || !isPlainObject(next)) {
    return sameValue(previous, next) ? undefined : next;
  }
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(next)) {
    if (value === undefined) continue;
    const change = changed(previous[key], value, [...path, key], removed);
    if (change !== undefined) result[key] = change;
  }
  for (const [key, value] of Object.entries(previous)) {
    if (value !== undefined && next[key] === undefined) removed.push([...path, key]);
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

// A color (`{ light, dark }`), an icon or an image, and a menu are one value each on the native side, not a group: a
// part of one taken out goes as the whole value again.
function isWholeValue(path: string[]): boolean {
  const key = path[path.length - 1];
  return key !== undefined && /^(color|icon|image|menu)$|(Color|Icon|Image)$/.test(key);
}

/**
 * Whether two values are the same: plain objects key by key, arrays item by item, anything else, functions too, by
 * identity. A module that ran again makes its functions anew, so they count as changed: Hermes keeps no source to tell
 * an edited one, and sending a button's handler again costs little.
 */
export function sameValue(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, index) => sameValue(item, b[index]));
  }
  if (isPlainObject(a) && isPlainObject(b)) {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].every((key) => sameValue(a[key], b[key]));
  }
  return Object.is(a, b);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

/**
 * The part of an edit's change that the screen shows the old definition's value of: where the command that showed it,
 * or a merge since, set another value, that value stays. `before` is what the old definition gave, `own` the screen's
 * own options now.
 */
export function changeOverOwn(change: OptionsChange, before: Options, own: Options): OptionsChange {
  const fromDefinition = (path: string[]) => sameValue(valueAt(own, path), valueAt(before, path));
  const removed = change.removed.filter(fromDefinition);
  const merge =
    change.merge && (keptWhere(change.merge, [], fromDefinition) as Options | undefined);
  return merge ? { merge, removed } : { removed };
}

function keptWhere(value: unknown, path: string[], keep: (path: string[]) => boolean): unknown {
  if (!isPlainObject(value) || isWholeValue(path)) return keep(path) ? value : undefined;
  const result: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(value)) {
    const kept = keptWhere(item, [...path, key], keep);
    if (kept !== undefined) result[key] = kept;
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

/** The value at a path of options, such as `['topBar', 'title', 'text']`; undefined where there is none. */
export function valueAt(options: unknown, path: readonly string[]): unknown {
  return path.reduce<unknown>(
    (value, key) => (isPlainObject(value) ? value[key] : undefined),
    options
  );
}
