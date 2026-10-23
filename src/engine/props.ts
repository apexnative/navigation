// The props of the screens and of the components in options, by their ids. A command puts them here before the native
// side creates the views, and each view takes its own as it mounts: React Native passes a root only its id. New props
// under the id of a view already shown (options that name the same component again) render it again with them.
const byId = new Map<string, object>();
const watchers = new Map<string, Set<() => void>>();
// The same object each time, so a view without props is not told they changed.
const none = {};
// The roots that are the components of bar buttons: their wrapper centers the content in the button's slot.
const buttonRoots = new Set<string>();
// The ids the library gave components in options, by the node whose options they are in. Such an id stays the same
// for every show of the node and may have several roots at once (a component of the default or a stack's options):
// its props go with the node, not with a root.
const optionComponents = new Map<string, Set<string>>();
const optionComponentIds = new Set<string>();

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

/** Notes an id the library gave a component in the options of the node `owner`. */
export function rememberOptionsComponent(owner: string, id: string): void {
  let ids = optionComponents.get(owner);
  if (!ids) optionComponents.set(owner, (ids = new Set()));
  ids.add(id);
  optionComponentIds.add(id);
}

/** Whether the library gave the id to a component in options: its props go with the node, not with a root. */
export function isOptionsComponent(id: string): boolean {
  return optionComponentIds.has(id);
}

/**
 * The view is gone: its props, and whether it is a button's root, are not needed again; nor are those of the
 * components the library named in its options.
 */
export function forgetProps(id: string): void {
  byId.delete(id);
  buttonRoots.delete(id);
  const ids = optionComponents.get(id);
  if (!ids) return;
  optionComponents.delete(id);
  for (const component of ids) {
    byId.delete(component);
    buttonRoots.delete(component);
    optionComponentIds.delete(component);
  }
}
