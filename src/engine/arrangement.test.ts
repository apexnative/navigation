import { toNativeLayout } from './layout';
import { getStartInfo } from './native';
import { notePlaces, placeOf } from './places';
import { propsOf } from './props';
import {
  arrangementOf,
  checkArrangements,
  type EngineArrangement,
  registerScreen,
  screenOptions,
} from './screens';

jest.mock('./native', () => ({
  commands: {},
  getStartInfo: jest.fn(),
  onNativeEvent: () => () => {},
}));

const startInfo = getStartInfo as jest.Mock;

const PAIR: Omit<EngineArrangement, 'primary' | 'secondary'> = {
  style: 'split',
  axes: 'both',
  fraction: 0.5,
  primaryEdge: 'leading',
  compact: 'primary',
};

const screen = (arrangement?: () => EngineArrangement | undefined) => ({
  component: () => () => null,
  options: () => ({}),
  ...(arrangement && { arrangement }),
});

describe('pairs of screens', () => {
  beforeAll(() => {
    registerScreen('Home', screen());
    registerScreen('Details', screen());
    registerScreen('Profile', {
      ...screen(() => ({ ...PAIR, primary: 'Profile', secondary: 'Details' })),
      key: () => 'one',
    });
    registerScreen('ProfileDetails', {
      pair: {
        ...PAIR,
        primary: 'Home',
        secondary: 'Details',
        style: 'overlay',
        compact: 'pair',
        secondaryProps: (props) => ({ id: (props as { userId: string }).userId }),
      },
    });
    registerScreen('Settings', {
      ...screen(),
      options: () => ({ topBar: { title: { text: 'Settings' } } }),
    });
    registerScreen('SettingsDetails', { pair: { ...PAIR, primary: 'Settings', secondary: 'Details' } });
  });

  beforeEach(() => {
    startInfo.mockReturnValue({ coldStart: true, splashLogo: null, arrangements: true });
  });

  it('shows a screen with a secondary as a pair, the options of the show on the primary', () => {
    const node = toNativeLayout({
      component: {
        name: 'Profile',
        passProps: { id: '7' },
        options: { topBar: { title: { text: 'A' } } },
      },
    });

    expect(node.type).toBe('Arrangement');
    expect(node.data).toEqual({
      name: 'Profile',
      style: 'split',
      axes: 'both',
      fraction: 0.5,
      primaryEdge: 'leading',
      compact: 'primary',
    });
    const [primary, secondary] = node.children;
    expect(primary!.data.name).toBe('Profile');
    expect(primary!.data.options?.topBar?.title?.text).toBe('A');
    // The key of a screen is its place in a stack, which is the pair's.
    expect(primary!.data.key).toBeUndefined();
    expect(secondary!.data.name).toBe('Details');
    expect(secondary!.data.options?.topBar).toBeUndefined();
  });

  it('shows a pair entry with its settings and the props of its secondary', () => {
    const node = toNativeLayout({
      component: { name: 'ProfileDetails', passProps: { userId: '9' } },
    });

    expect(node.data).toMatchObject({ name: 'ProfileDetails', style: 'overlay', compact: 'pair' });
    expect(node.children.map((child) => child.data.name)).toEqual(['Home', 'Details']);
    expect(propsOf(node.children[0]!.id)).toEqual({ userId: '9' });
    expect(propsOf(node.children[1]!.id)).toEqual({ id: '9' });
  });

  it('gives an id of the show to the primary, the pair one of its own', () => {
    const node = toNativeLayout({ component: { id: 'ModalScreen1', name: 'Profile' } });

    expect(node.id).not.toBe('ModalScreen1');
    expect(node.children[0]!.id).toBe('ModalScreen1');
  });

  it('takes the options of a pair entry from its primary', () => {
    expect(screenOptions('SettingsDetails', {})).toEqual({
      topBar: { title: { text: 'Settings' } },
    });
  });

  it('notes both screens of a pair at the place of the pair in its stack', () => {
    const node = toNativeLayout({
      stack: { children: [{ component: { name: 'Home' } }, { component: { name: 'Profile' } }] },
    });
    notePlaces(node, 'root');

    const [home, pair] = node.children;
    const [primary, secondary] = pair!.children;
    expect(placeOf(primary!.id)).toMatchObject({
      stack: node.id,
      index: 1,
      arrangement: { id: pair!.id, placement: 'primary' },
    });
    expect(placeOf(secondary!.id)).toMatchObject({
      stack: node.id,
      index: 1,
      arrangement: { placement: 'secondary' },
    });
    expect(placeOf(home!.id)?.arrangement).toBeUndefined();
  });

  it('shows the primary alone where the native side shows no pairs', () => {
    startInfo.mockReturnValue({ coldStart: true, splashLogo: null, arrangements: false });

    const node = toNativeLayout({
      component: { id: 'ModalScreen2', name: 'Profile', passProps: { id: '7' } },
    });
    notePlaces(node, 'root');

    expect(node.type).toBe('Component');
    expect(node.id).toBe('ModalScreen2');
    expect(node.data.name).toBe('Profile');
    // Alone it is a plain screen of its stack: its key goes with it.
    expect(node.data.key).toBe('Profile:one');
    expect(placeOf(node.id)?.arrangement).toEqual({
      id: node.id,
      placement: 'primary',
      alone: true,
    });
  });

  it('refuses a pair that names a screen not in screens, or another pair', () => {
    registerScreen('Broken', { pair: { ...PAIR, primary: 'Home', secondary: 'Nowhere' } });
    expect(() => checkArrangements()).toThrow(
      "[navigation] The pair of 'Broken' names 'Nowhere', which is not in screens"
    );
    registerScreen('Broken', { pair: { ...PAIR, primary: 'ProfileDetails', secondary: 'Home' } });
    expect(() => checkArrangements()).toThrow(
      "[navigation] The pair of 'Broken' names the pair 'ProfileDetails'"
    );
    registerScreen('Broken', screen());
  });

  it('knows the pair of a name: an entry, a screen with a secondary, none for a plain screen', () => {
    expect(arrangementOf('ProfileDetails')?.primary).toBe('Home');
    expect(arrangementOf('Profile')?.primary).toBe('Profile');
    expect(arrangementOf('Home')).toBeUndefined();
  });
});
