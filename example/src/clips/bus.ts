import { useEffect, useRef } from 'react';

type Handler = (arg?: unknown) => void;

const handlers = new Map<string, Set<Handler>>();

/** Sends a step of a clip to the screens that act on it: a scroll, typing, a press. */
export function emit(event: string, arg?: unknown) {
  handlers.get(event)?.forEach((handler) => handler(arg));
}

/** A step of a clip this component acts on, while it is mounted. */
export function useClipEvent(event: string, handler: Handler) {
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => {
    const set = handlers.get(event) ?? new Set<Handler>();
    const call: Handler = (arg) => latest.current(arg);
    set.add(call);
    handlers.set(event, set);
    return () => {
      set.delete(call);
    };
  }, [event]);
}

/** Listens to an event outside a component: the steps of a clip that wait for the device. Returns the unsubscribe. */
export function listen(event: string, handler: Handler): () => void {
  const set = handlers.get(event) ?? new Set<Handler>();
  set.add(handler);
  handlers.set(event, set);
  return () => {
    set.delete(handler);
  };
}
