import { commands as native } from '../../engine/native';
import { isReactScreen, screenOptions } from '../../engine/screens';
import { getTheme, setTheme } from '../theme/theme';
import { createNavigation, noteReplacingApp, startApp } from './createNavigation';

jest.mock('../../engine/native', () => ({
  commands: {
    setRoot: jest.fn(),
    setDefaultOptions: jest.fn(),
    mergeOptions: jest.fn(),
  },
  getStartInfo: () => ({ coldStart: false, splashLogo: null, arrangements: false }),
  getLaunchLink: async () => null,
  onNativeEvent: () => () => {},
}));

// React Refresh as React Native gives it in development: Metro puts what a module exports in a family, and a module that
// runs again puts its new exports in the families of the old ones.
const families = new Map<unknown, { current: unknown }>();
function register(type: unknown, older?: unknown) {
  families.set(type, (older !== undefined && families.get(older)) || { current: type });
}
beforeEach(() => {
  // resetMocks takes the implementations away before each test.
  (native.setRoot as jest.Mock).mockImplementation(async () => 'Component1');
  families.clear();
  (globalThis as Record<string, unknown>).__ReactRefresh = {
    getFamilyByType: (type: unknown) => families.get(type),
  };
});
afterEach(() => {
  delete (globalThis as Record<string, unknown>).__ReactRefresh;
});

const Home = () => null;
const Profile = () => null;
const setRoot = native.setRoot as jest.Mock;
const setDefaultOptions = native.setDefaultOptions as jest.Mock;

describe('the module of the app running again after an edit', () => {
  it('gives the app that runs its new screens and default options, without a new start', async () => {
    const willMount = jest.fn();
    const first = createNavigation({
      screens: { Home },
      root: 'Home',
      options: { topBar: { title: { text: 'Old' } } },
      willMount,
    });
    register(first);
    await startApp(first);
    expect(setRoot).toHaveBeenCalledTimes(1);
    expect(isReactScreen('Profile')).toBe(false);
    setDefaultOptions.mockClear();

    // The module ran again: Metro registers its new export in the family of the app that launched.
    const second = createNavigation({
      screens: { Home, Profile: { component: Profile, options: { topBar: { visible: false } } } },
      root: 'Home',
      options: { topBar: { title: { text: 'New' } } },
      willMount,
    });
    register(second, first);
    await Promise.resolve();

    expect(isReactScreen('Profile')).toBe(true);
    expect(screenOptions('Profile', {})).toEqual({ topBar: { visible: false } });
    expect(setDefaultOptions).toHaveBeenCalledWith({ topBar: { title: { text: 'New' } } });
    // A new launch of the native side that names the new app starts nothing new: the app that runs sets its root again.
    await startApp(second);
    expect(willMount).toHaveBeenCalledTimes(1);
    expect(setRoot).toHaveBeenCalledTimes(2);
  });

  it('keeps the default options when the theme and the options are the same', async () => {
    const options = { topBar: { title: { text: 'Old' } } };
    const first = createNavigation({ screens: { Home }, root: 'Home', options });
    register(first);
    await startApp(first);
    setDefaultOptions.mockClear();
    const second = createNavigation({ screens: { Home }, root: 'Home', options: { ...options } });
    register(second, first);
    await Promise.resolve();
    expect(setDefaultOptions).not.toHaveBeenCalled();
  });

  it('is a new app of its own when it is not the module of the app that launched', async () => {
    const first = createNavigation({ screens: { Home }, root: 'Home' });
    register(first);
    await startApp(first);
    const other = createNavigation({ screens: { Profile }, root: 'Profile' });
    register(other);
    await Promise.resolve();
    await startApp(other);
    expect(setRoot).toHaveBeenCalledTimes(2);
    expect(JSON.stringify(setRoot.mock.calls[1]![1])).toContain('Profile');
  });

  it('keeps the theme the app set since, when the edit leaves the theme as it was', async () => {
    const first = createNavigation({ screens: { Home }, root: 'Home', theme: { colors: {} } });
    register(first);
    await startApp(first);
    setTheme({ colors: { primary: 'red' } });
    const second = createNavigation({ screens: { Home }, root: 'Home', theme: { colors: {} } });
    register(second, first);
    await Promise.resolve();
    expect(getTheme()).toEqual({ colors: { primary: 'red' } });
  });

  it("takes an app the entry registers again under the app's name, one it doesn't export", async () => {
    const willMount = jest.fn();
    const first = createNavigation({ screens: { Home }, root: 'Home', willMount });
    register(first);
    await startApp(first);
    const second = createNavigation({ screens: { Home, Profile }, root: 'Home', willMount });
    noteReplacingApp(second);
    await startApp(second);
    expect(isReactScreen('Profile')).toBe(true);
    expect(willMount).toHaveBeenCalledTimes(1);
  });
});
