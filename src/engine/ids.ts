let last = 0;

/** A new id for this run of JavaScript: the prefix and a number all prefixes share, such as `Component12`. */
export function nextId(prefix: string): string {
  last += 1;
  return `${prefix}${last}`;
}
