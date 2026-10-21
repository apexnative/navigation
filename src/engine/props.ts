// The props of the screens and of the components in options, by their ids. A command puts them here before the native
// side creates the views, and each view takes its own as it mounts: React Native passes a root only its id. New props
// under the id of a view already shown (options that name the same component again) render it again with them.
const byId = new Map<string, object>();
const watchers = new Map<string, Set<() => void>>();
// The same object each time, so a view without props is not told they changed.
const none = {};
// The roots that are the components of bar buttons: their wrapper centers the content in the button's slot.
const buttonRoots = new Set<string>();

export function setProps(id: string, props: object | undefined): void {
  if (props === undefined) byId.delete(id);
  else byId.set(id, props);
  watchers.get(id)?.forEach((watcher) => watcher());
}

export function propsOf(id: string): object {
  return byId.get(id) ?? none;
}

/** Calls back when the props under the id change; returns what stops it. */
export function watchProps(id: string, watcher: () => void): () => void {
  let set = watchers.get(id);
  if (!set) watchers.set(id, (set = new Set()));
  set.add(watcher);
  return () => {
    set.delete(watcher);
    if (set.size === 0) watchers.delete(id);
  };
}

export function rememberButtonRoot(id: string): void {
  buttonRoots.add(id);
}

/** Whether the root under the id is the component of a bar button (top bar or toolbar). */
export function isButtonRoot(id: string): boolean {
  return buttonRoots.has(id);
}

/** The view is gone: its props, and whether it is a button's root, are not needed again. */
export function forgetProps(id: string): void {
  byId.delete(id);
  buttonRoots.delete(id);
}
