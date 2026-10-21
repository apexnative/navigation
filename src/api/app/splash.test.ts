import type * as SplashModule from './splash';
import type * as RootModule from '../layout/Root';

// Each test takes the module afresh (resetModules): hide() and the start info are kept in it.
function splashModule(): typeof SplashModule {
  return require('./splash');
}

const CONFIG = { component: () => null };

// Lets the promises run, and the timers up to `ms`.
async function advance(ms = 0) {
  await jest.advanceTimersByTimeAsync(ms);
}

function track(promise: Promise<void>) {
  const state = { done: false };
  promise.then(() => {
    state.done = true;
  });
  return state;
}

describe('the splash in React Native', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.dontMock('../../engine/native');
  });

  it('may leave once it shows, without minDuration', async () => {
    const { showSplash } = splashModule();
    let shown!: () => void;
    const leaves = track(
      showSplash(CONFIG, () => new Promise<void>((resolve) => (shown = resolve)))
    );
    await advance();
    expect(leaves.done).toBe(false);

    shown();
    await advance();

    expect(leaves.done).toBe(true);
  });

  it('stays for minDuration from when it shows', async () => {
    const { showSplash } = splashModule();
    let shown!: () => void;
    const leaves = track(
      showSplash(
        { ...CONFIG, minDuration: 800 },
        () => new Promise<void>((resolve) => (shown = resolve))
      )
    );
    await advance(1000);
    shown();
    await advance(799);
    expect(leaves.done).toBe(false);

    await advance(1);

    expect(leaves.done).toBe(true);
  });

  it("waits for hide() with hide: 'manual', also when it comes before the splash shows", async () => {
    const { showSplash, useSplash } = splashModule();
    let shown!: () => void;
    const leaves = track(
      showSplash(
        { ...CONFIG, hide: 'manual' },
        () => new Promise<void>((resolve) => (shown = resolve))
      )
    );
    shown();
    await advance(5000);
    expect(leaves.done).toBe(false);

    useSplash().hide();
    await advance();

    expect(leaves.done).toBe(true);
  });

  it('holds nothing when it fails to show', async () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const { showSplash } = splashModule();
    const leaves = track(
      showSplash({ ...CONFIG, hide: 'manual', minDuration: 800 }, () =>
        Promise.reject(new Error('gone'))
      )
    );

    await advance();

    expect(leaves.done).toBe(true);
    expect(warn).toHaveBeenCalledWith("[navigation] splash: it didn't show (Error: gone)");
  });

  it('lets the start go on at once when it is released', async () => {
    const { showSplash, releaseSplash } = splashModule();
    const leaves = track(
      showSplash({ ...CONFIG, hide: 'manual', minDuration: 800 }, () => new Promise<void>(() => {}))
    );
    await advance(5000);
    expect(leaves.done).toBe(false);

    releaseSplash();
    await advance();

    expect(leaves.done).toBe(true);
  });

  it('says nothing when a failed start replaced it before it showed', async () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const { showSplash, splashDone } = splashModule();
    let replaced!: (error: Error) => void;
    showSplash(CONFIG, () => new Promise<void>((_, reject) => (replaced = reject)));
    splashDone();

    replaced(new Error('replaced'));
    await advance();

    expect(warn).not.toHaveBeenCalled();
  });

  it('shows on a cold start only', () => {
    jest.doMock('../../engine/native', () => ({
      getStartInfo: () => ({ coldStart: false, splashLogo: null }),
    }));
    const { showsSplash } = splashModule();

    expect(showsSplash(CONFIG)).toBe(false);
    expect(showsSplash(undefined)).toBe(false);
  });

  it("gives the size of the native splash's logo, the same object each time", () => {
    jest.doMock('../../engine/native', () => ({
      getStartInfo: () => ({ coldStart: true, splashLogo: { width: 120, height: 40 } }),
    }));
    const { useSplash } = splashModule();

    expect(useSplash().logo).toEqual({ width: 120, height: 40 });
    expect(useSplash()).toBe(useSplash());
  });

  it('gives no logo without a splash made by the CLI', () => {
    expect(splashModule().useSplash().logo).toBeUndefined();
  });
});

describe('the first root after the splash', () => {
  const { Root, buildRoot, withDefaultTransition } = require('../layout/Root') as typeof RootModule;
  const setRoot = (layout: ReturnType<typeof buildRoot>) =>
    layout.root.stack?.options?.animations?.setRoot;

  it("comes in with the splash's transition, once its content is drawn", () => {
    expect(setRoot(withDefaultTransition(buildRoot('Home'), 'fade'))).toEqual({
      enter: { alpha: { from: 0, to: 1, duration: 250 }, waitForRender: true },
    });
  });

  it('keeps a transition of its own', () => {
    const own = buildRoot(Root('Home').transition('slideUp'));
    expect(withDefaultTransition(own, 'fade')).toBe(own);
  });

  it("stays as it is with the splash's 'platform'", () => {
    const root = buildRoot('Home');
    expect(withDefaultTransition(root, 'platform')).toBe(root);
  });
});
