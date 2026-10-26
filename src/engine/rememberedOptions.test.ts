import { Platform } from 'react-native';
import type { LayoutRoot } from '../interfaces/Layout';
import { mergeOptions, push, setRoot } from './commands';
import { launchState } from './launchState';
import type { LayoutNode } from './layout';
import { commands as native } from './native';
import { mergeNativeOptions } from './options';
import { forgetMergedOptions, mergedOptionsOf } from './rememberedOptions';
import { registerScreen } from './screens';
import { enableSlots, slotCommitted, slotStore } from './slots';

jest.mock('./native', () => ({
  commands: {
    setRoot: jest.fn(),
    push: jest.fn(),
    mergeOptions: jest.fn(),
    preparePush: jest.fn(),
    cancelPreparedPush: jest.fn(),
  },
  getStartInfo: () => ({ coldStart: true, splashLogo: null, arrangements: false }),
  onNativeEvent: () => () => {},
}));

const FRAME = { width: 411, height: 801, padding: { top: 88, right: 0, bottom: 24, left: 0 } };
const mocked = native as unknown as Record<
  'setRoot' | 'push' | 'mergeOptions' | 'preparePush' | 'cancelPreparedPush',
  jest.Mock
>;

// Lets the promises of the engine run.
const settle = () => new Promise<void>((resolve) => setImmediate(() => resolve()));

// The module of the lazy screen loads while this is set.
let loading: Promise<void> | undefined;

const screen = { component: () => () => null, options: () => ({}) };

// The order the native side got its commands in: the name of each, and the id it acts on.
function nativeCalls(): string[] {
  const calls = (['setRoot', 'push', 'mergeOptions'] as const).flatMap((name) =>
    mocked[name].mock.calls.map((args, index) => ({
      order: mocked[name].mock.invocationCallOrder[index]!,
      call: name === 'mergeOptions' ? `mergeOptions ${args[0]}` : name,
    }))
  );
  return calls.sort((a, b) => a.order - b.order).map(({ call }) => call);
}

function sentRoots(): { root: LayoutNode; modals: LayoutNode[] }[] {
  return mocked.setRoot.mock.calls.map((args) => args[1]);
}

beforeAll(() => {
  registerScreen('Home', screen);
  registerScreen('Profile', screen);
  registerScreen('Details', screen);
  registerScreen('Settings', { ...screen, load: () => loading });
});

beforeEach(() => {
  loading = undefined;
  mocked.setRoot.mockImplementation((_commandId, layout) => Promise.resolve(layout.root.id));
  mocked.push.mockImplementation((_commandId, _componentId, node) => Promise.resolve(node.id));
});

describe('mergeOptions in the order of the shows', () => {
  it('goes at once while no show waits', async () => {
    await push('Home1', { component: { name: 'Details' } });
    mergeOptions('Home1', { topBar: { title: { text: 'Home' } } });

    expect(mocked.mergeOptions).toHaveBeenCalledWith('Home1', {
      topBar: { title: { text: 'Home' } },
    });
  });

  it('goes after a push that waits for its lazy screen, in the order the merges were made', async () => {
    let loaded!: () => void;
    loading = new Promise((resolve) => (loaded = resolve));

    const pushing = push('Home1', { component: { name: 'Settings' } });
    mergeOptions('Home1', { topBar: { title: { text: 'Home' } } });
    mergeOptions('Home1', { topBar: { title: { text: 'Inbox' } } });
    expect(mocked.mergeOptions).not.toHaveBeenCalled();

    loading = undefined;
    loaded();
    await pushing;

    expect(nativeCalls()).toEqual(['push', 'mergeOptions Home1', 'mergeOptions Home1']);
    expect(mocked.mergeOptions.mock.calls[1]![1]).toEqual({ topBar: { title: { text: 'Inbox' } } });
  });

  it('waits for every show made before it, not for one made after', async () => {
    let loaded!: () => void;
    loading = new Promise((resolve) => (loaded = resolve));

    const lazy = push('Home1', { component: { name: 'Settings' } });
    mergeOptions('Home1', { topBar: { title: { text: 'Home' } } });
    await push('Home1', { component: { name: 'Details' } });
    expect(mocked.mergeOptions).not.toHaveBeenCalled();

    loading = undefined;
    loaded();
    await lazy;

    expect(nativeCalls()).toEqual(['push', 'push', 'mergeOptions Home1']);
  });

  it('goes once a show fails before it went out', async () => {
    loading = Promise.reject(new Error('the module failed to load'));

    const pushing = push('Home1', { component: { name: 'Settings' } });
    mergeOptions('Home1', { topBar: { title: { text: 'Home' } } });

    await expect(pushing).rejects.toThrow('the module failed to load');
    expect(mocked.push).not.toHaveBeenCalled();
    expect(mocked.mergeOptions).toHaveBeenCalledTimes(1);
  });
});

describe('the merged options of the last root, set again for a new launch', () => {
  // What a new activity does: its launch counts, and the last root is set again as it was passed.
  function launchAgain(): Promise<string> {
    launchState.launch += 1;
    return setRoot(launchState.root!);
  }

  it('go over the nodes in the same places, under their new ids', async () => {
    const root: LayoutRoot = {
      root: {
        bottomTabs: {
          id: 'tabs',
          children: [
            { stack: { children: [{ component: { name: 'Home' } }] } },
            { component: { id: 'profile', name: 'Profile' } },
          ],
        },
      },
    };
    await setRoot(root);
    const [first] = sentRoots();
    const homeId = first!.root.children[0]!.children[0]!.id;
    mergeOptions(homeId, { topBar: { title: { text: 'Home' }, rightButtons: [{ id: 'edit' }] } });
    mergeOptions(homeId, {
      topBar: { subtitle: { text: '3 new' }, rightButtons: [{ id: 'done' }] },
    });
    mergeOptions('profile', { bottomTab: { badge: '2' } });
    mergeOptions('tabs', { bottomTabs: { currentTabIndex: 1, visible: false } });
    // The activity takes the screens of the root with it.
    forgetMergedOptions(homeId);
    forgetMergedOptions('profile');

    await launchAgain();

    const again = sentRoots()[1]!.root;
    const home = again.children[0]!.children[0]!;
    expect(home.id).not.toBe(homeId);
    expect(home.data.options).toEqual({
      topBar: {
        title: { text: 'Home' },
        subtitle: { text: '3 new' },
        rightButtons: [{ id: 'done' }],
      },
    });
    expect(again.children[1]!.data.options).toEqual({ bottomTab: { badge: '2' } });
    // Selecting a tab acts once.
    expect(again.data.options).toEqual({ bottomTabs: { visible: false } });
    expect(mergedOptionsOf(homeId)).toBeUndefined();
    expect(mergedOptionsOf(home.id)).toEqual(home.data.options);
  });

  it('are dropped once another root goes out', async () => {
    const before: LayoutRoot = { root: { component: { name: 'Home' } } };
    await setRoot(before);
    const homeId = sentRoots()[0]!.root.id;
    mergeOptions(homeId, { topBar: { title: { text: 'Home' } } });

    await setRoot({ root: { component: { name: 'Home' } } });
    await launchAgain();

    expect(mergedOptionsOf(homeId)).toBeUndefined();
    expect(sentRoots()[2]!.root.data.options).toEqual({});
  });

  it('leave out the screens shown after the root, whose merges are not kept', async () => {
    await setRoot({ root: { stack: { children: [{ component: { name: 'Home' } }] } } });
    const homeId = sentRoots()[0]!.root.children[0]!.id;
    const detailsId = await push(homeId, { component: { name: 'Details' } });
    mergeOptions(detailsId, { topBar: { title: { text: 'Details' } } });
    expect(mergedOptionsOf(detailsId)).toBeUndefined();

    forgetMergedOptions(detailsId);
    await launchAgain();

    expect(mergedOptionsOf(detailsId)).toBeUndefined();
    expect(sentRoots()[1]!.root.children).toHaveLength(1);
  });

  it('take a color or an image whole, as the native side does', () => {
    const own = { topBar: { background: { color: { light: 1, dark: 2 } } } };
    const merged = { topBar: { background: { color: { semantic: ['systemBackground'] } } } };

    expect(mergeNativeOptions<object>(own, merged)).toEqual(merged);
  });
});

describe('mergeOptions and a prepared push on Android', () => {
  beforeAll(() => {
    enableSlots(({ children }) => children);
  });

  beforeEach(() => {
    jest.replaceProperty(Platform, 'OS', 'android');
    mocked.preparePush.mockResolvedValue(FRAME);
  });

  it('sends the merges into the prepared screen at once and the others after its push', async () => {
    const pushing = push('Home1', { component: { name: 'Details' } });
    mergeOptions('Home1', { topBar: { title: { text: 'Home' } } });
    await settle();

    const slots = slotStore.get().slots;
    const slot = slots[slots.length - 1]!;
    mergeOptions(slot.id, { topBar: { title: { text: 'Details' } } });
    expect(nativeCalls()).toEqual([`mergeOptions ${slot.id}`]);

    slotCommitted(slot.key);
    await pushing;

    expect(nativeCalls()).toEqual([`mergeOptions ${slot.id}`, 'push', 'mergeOptions Home1']);
  });
});
