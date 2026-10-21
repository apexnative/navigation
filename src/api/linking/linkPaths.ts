// URLs into a path and a query, and paths matched against the patterns of `routes`.

/** A URL taken apart: the path without the prefix, and the query. */
export interface ParsedLink {
  url: string;
  path: string;
  query: Record<string, string>;
}

/** A path matched by a route. */
export interface LinkMatch extends ParsedLink {
  /** The pattern of the route. */
  route: string;
  params: Record<string, string>;
}

/** A pattern split into parts, ready to match. */
export interface CompiledRoute {
  pattern: string;
  parts: { name: string; param: boolean; optional: boolean }[];
}

/** The path and the query of a URL; undefined when it starts with none of `prefixes`. */
export function parseLink(
  url: string,
  prefixes: readonly string[] | undefined
): ParsedLink | undefined {
  let rest: string;
  if (prefixes) {
    // The longest prefix that ends where a part of the path ends: `https://example.com` doesn't take
    // `https://example.community`.
    const prefix = prefixes
      .filter((candidate) => startsWithPrefix(url, candidate) && endsAtBoundary(candidate, url))
      .sort((a, b) => b.length - a.length)[0];
    if (prefix === undefined) return undefined;
    rest = url.slice(prefix.length);
  } else {
    const scheme = /^([a-z][a-z0-9+.-]*):\/\/(.*)$/is.exec(url);
    if (!scheme) return undefined;
    rest = /^https?$/i.test(scheme[1]!) ? scheme[2]!.replace(/^[^/?#]*/, '') : scheme[2]!;
  }
  const [path, query] = splitOnce(rest.split('#')[0]!, '?');
  return { url, path: trimSlashes(path), query: parseQuery(query) };
}

// The scheme and the host don't care about case (`HTTPS://Example.com`); the path of a prefix does.
function startsWithPrefix(url: string, prefix: string): boolean {
  const authority = /^[a-z][a-z0-9+.-]*:\/\/[^/?#]*/i.exec(prefix)?.[0].length ?? 0;
  return (
    url.slice(0, authority).toLowerCase() === prefix.slice(0, authority).toLowerCase() &&
    url.startsWith(prefix.slice(authority), authority)
  );
}

function endsAtBoundary(prefix: string, url: string): boolean {
  const next = url[prefix.length];
  return (
    next === undefined || /[/?#]$/.test(prefix) || prefix.endsWith('://') || /[/?#]/.test(next)
  );
}

function splitOnce(text: string, separator: string): [string, string] {
  const index = text.indexOf(separator);
  return index < 0 ? [text, ''] : [text.slice(0, index), text.slice(index + 1)];
}

function trimSlashes(path: string): string {
  return path.replace(/^\/+|\/+$/g, '');
}

function decode(text: string): string {
  try {
    return decodeURIComponent(text);
  } catch {
    return text;
  }
}

function parseQuery(query: string): Record<string, string> {
  const values: Record<string, string> = {};
  for (const pair of query.split('&')) {
    if (!pair) continue;
    const [key, value] = splitOnce(pair, '=');
    values[decode(key.replace(/\+/g, ' '))] = decode(value.replace(/\+/g, ' '));
  }
  return values;
}

/**
 * The routes in the order they are tried: more required parts first, then a static part before a param at the same
 * place, then fewer parts, then as written.
 */
export function compileRoutes(patterns: readonly string[]): CompiledRoute[] {
  const compiled = patterns.map((pattern) => ({
    pattern,
    parts: trimSlashes(pattern)
      .split('/')
      .filter(Boolean)
      .map((part) => ({
        name: part.replace(/^:/, '').replace(/\?$/, ''),
        param: part.startsWith(':'),
        optional: part.startsWith(':') && part.endsWith('?'),
      })),
  }));
  if (__DEV__) {
    for (const route of compiled) {
      const firstOptional = route.parts.findIndex((part) => part.optional);
      if (firstOptional >= 0 && route.parts.slice(firstOptional).some((part) => !part.optional)) {
        console.warn(
          `[navigation] The optional params of '${route.pattern}' in linking go at its end`
        );
      }
    }
  }
  return compiled
    .map((route, order) => ({ route, order }))
    .sort((a, b) => compareRoutes(a.route, b.route) || a.order - b.order)
    .map(({ route }) => route);
}

function compareRoutes(a: CompiledRoute, b: CompiledRoute): number {
  const required = requiredParts(b) - requiredParts(a);
  if (required) return required;
  for (let index = 0; index < Math.min(a.parts.length, b.parts.length); index++) {
    const rank = partRank(a.parts[index]!) - partRank(b.parts[index]!);
    if (rank) return rank;
  }
  return a.parts.length - b.parts.length;
}

function requiredParts(route: CompiledRoute): number {
  return route.parts.filter((part) => !part.optional).length;
}

function partRank(part: CompiledRoute['parts'][number]): number {
  return part.optional ? 2 : part.param ? 1 : 0;
}

/** The first route that takes the path, with its params; undefined when none does. */
export function matchLink(
  link: ParsedLink,
  routes: readonly CompiledRoute[]
): LinkMatch | undefined {
  // `user//42` is `user/42`: an empty part is no param.
  const segments = link.path.split('/').filter(Boolean);
  for (const route of routes) {
    if (segments.length < requiredParts(route) || segments.length > route.parts.length) continue;
    const params: Record<string, string> = {};
    const fits = route.parts.every((part, index) => {
      const segment = segments[index];
      if (segment === undefined) return part.optional;
      if (!part.param) return segment === part.name;
      params[part.name] = decode(segment);
      return true;
    });
    if (fits) return { ...link, route: route.pattern, params };
  }
  return undefined;
}
