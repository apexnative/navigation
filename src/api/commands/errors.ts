import { isGone, placeOf, type ScreenPlace } from '../../engine/places';

// A command that can't be made rejects with the command and the reason, the same text on iOS and Android. What the
// navigation knows itself it tells before it asks the native side: the screen is gone, it is not in a stack, there is
// nothing to go back to.

// The commands on their way, by the order they started in, for what waits until the commands of a moment are done: a
// pending link after a sign-in.
let started = 0;
const active = new Set<number>();
const waiters = new Set<() => void>();

/** Runs a command; what it can't do rejects with the command in the error. */
export function command<T>(name: string, run: () => Promise<T>): Promise<T> {
  let result: Promise<T>;
  try {
    result = run();
  } catch (error) {
    result = Promise.reject(error);
  }
  return trackCommand(
    result.catch((error: unknown) => {
      throw commandError(name, error);
    })
  );
}

/** Counts a command as on its way until it settles; `command` does it, and the commands made without it. */
export function trackCommand<T>(result: Promise<T>): Promise<T> {
  const id = ++started;
  active.add(id);
  return result.finally(() => {
    active.delete(id);
    waiters.forEach((check) => check());
  });
}

/** How many commands started since the app did: what `whenCommandsSettle` waits from. */
export function commandsStarted(): number {
  return started;
}

/**
 * Resolves once the commands started after `since` (a value of `commandsStarted`) settled, at the latest after
 * `timeout`. Older ones don't count: a command the native side never answers (Android without an activity) doesn't
 * hold what comes later.
 */
export function whenCommandsSettle(since: number, timeout = 3000): Promise<void> {
  const busy = () => [...active].some((id) => id > since);
  if (!busy()) return Promise.resolve();
  return new Promise((resolve) => {
    const check = () => {
      if (!busy()) done();
    };
    const done = () => {
      clearTimeout(timer);
      waiters.delete(check);
      resolve();
    };
    const timer = setTimeout(done, timeout);
    waiters.add(check);
  });
}

function commandError(name: string, error: unknown): Error {
  const message = error instanceof Error ? error.message : String(error);
  const reason = message.replace(/^\[navigation\] /, '');
  if (reason.startsWith(`${name}: `)) return error instanceof Error ? error : new Error(message);
  // A sentence goes on after the command; a name stays as it is.
  const sentence = /^[A-Z][a-z]/.test(reason) ? reason[0]!.toLowerCase() + reason.slice(1) : reason;
  return new Error(`[navigation] ${name}: ${sentence}`);
}

/**
 * The place of the screen a command acts from, when the command can be made from there; otherwise it throws why: the
 * screen is gone, it is not in a stack (`stack`), it is the first of its stack (`below`), or it is not in a modal or an
 * overlay (`dismissible`). A screen the engine doesn't know is left to the native side.
 */
export function placeFor(
  name: string,
  screenId: string,
  needs: { stack?: boolean; below?: boolean; dismissible?: boolean } = {}
): ScreenPlace | undefined {
  const fail = (reason: string) => new Error(`[navigation] ${name}: ${reason}`);
  if (isGone(screenId))
    throw fail(`the screen '${screenId}' is gone: it closed before the command came`);
  const place = placeOf(screenId);
  if (!place) return undefined;
  if ((needs.stack || needs.below) && place.stack === null) {
    throw fail(`${place.name} is not in a stack`);
  }
  if (needs.below && place.index === 0) {
    throw fail(`${place.name} is the first screen of its stack, there is nothing to go back to`);
  }
  if (needs.dismissible && place.container === 'root') {
    throw fail(`${place.name} is not in a modal or an overlay`);
  }
  return place;
}
