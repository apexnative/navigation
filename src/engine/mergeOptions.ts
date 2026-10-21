/**
 * Merges options over the ones before: objects merge key by key, anything else — arrays such as buttons, values,
 * images — replaces what was there. `undefined` keeps the value before.
 */
export function mergeOptions<T extends object>(base: T, override: T): T {
  const result = { ...base } as Record<string, unknown>;
  for (const [key, next] of Object.entries(override)) {
    if (next === undefined) continue;
    const previous = result[key];
    result[key] =
      isPlainObject(previous) && isPlainObject(next) ? mergeOptions(previous, next) : next;
  }
  return result as T;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}
