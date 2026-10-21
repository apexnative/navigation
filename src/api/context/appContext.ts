// The app's context: one value for every screen, whatever React root it is in. It starts with `context` of
// createNavigation, and changes with `navigation.setContext` only.
let value: unknown;
const listeners = new Set<() => void>();

export function getAppContext<Context>(): Context {
  return value as Context;
}

export function setAppContext<Context>(next: Context | ((previous: Context) => Context)) {
  const resolved =
    typeof next === 'function' ? (next as (previous: Context) => Context)(value as Context) : next;
  if (Object.is(resolved, value)) return;
  value = resolved;
  listeners.forEach((listener) => listener());
}

export function subscribeAppContext(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
