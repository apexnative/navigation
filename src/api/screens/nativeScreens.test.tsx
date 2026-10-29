import { DevSettings } from 'react-native';
import { commands as native } from '../../engine/native';
import { showNativeScreen } from '../../engine/nativeScreens';
import { nativeScreenController, screenOptions } from '../../engine/screens';
import { isNativeScreen, nativeScreen } from './nativeScreens';
import { registerScreens } from './registerScreens';

jest.mock('../../engine/native', () => ({
  commands: { mergeOptions: jest.fn() },
  getStartInfo: () => ({ coldStart: true, arrangements: false }),
  onNativeEvent: () => () => {},
}));

// React Refresh as React Native gives it in development (see defineScreen.test.tsx).
const families = new Map<unknown, { current: unknown }>();
function register(type: unknown, older?: unknown) {
  const family = (older !== undefined && families.get(older)) || { current: type };
  families.set(type, family);
}
beforeEach(() => {
  families.clear();
  (globalThis as Record<string, unknown>).__ReactRefresh = {
    getFamilyByType: (type: unknown) => families.get(type),
  };
});
afterEach(() => {
  delete (globalThis as Record<string, unknown>).__ReactRefresh;
});

const node = (id: string, name: string) => ({
  id,
  type: 'ExternalComponent' as const,
  data: { name },
  children: [],
});

describe('a native screen', () => {
  it('is a component, so a file exporting it stays a boundary of Fast Refresh', () => {
    const settings = nativeScreen('Settings', { useProps: () => ({ count: 1 }) });
    expect(typeof settings).toBe('function');
    expect((settings as unknown as { name: string }).name).toMatch(/^[A-Z]/);
    expect(isNativeScreen(settings)).toBe(true);
    expect(settings.nativeName).toBe('Settings');
  });

  it('runs its useProps in its own component, the controller renders it', () => {
    const profile = nativeScreen('Profile', { useProps: () => ({ name: 'Ann' }) });
    registerScreens([{ source: 'screens', screens: { Profile: profile } }]);
    expect(nativeScreenController('Profile')).toBeDefined();
  });

  it('shows with the options of the newest version after Fast Refresh', () => {
    const before = nativeScreen('Help', { options: (s) => s.title('Help') });
    register(before);
    registerScreens([{ source: 'screens', screens: { Help: before } }]);
    const after = nativeScreen('Help', { options: (s) => s.title('Support') });
    register(after, before);
    families.get(before)!.current = after;
    expect(screenOptions('Help', {})).toEqual({ topBar: { title: { text: 'Support' } } });
  });

  it('sends an edit of its options to its screens that are open', async () => {
    const before = nativeScreen<{ name: string }>('Card', {
      options: (s, props) => s.title(props.name),
    });
    register(before);
    registerScreens([{ source: 'screens', screens: { Card: before } }]);
    showNativeScreen(node('ExternalComponent4', 'Card'), 'Card', { name: 'Visa' });

    const after = nativeScreen<{ name: string }>('Card', {
      options: (s, props) => s.title(props.name).subtitle('Default'),
    });
    register(after, before);
    await Promise.resolve();
    expect((native.mergeOptions as jest.Mock).mock.calls).toEqual([
      ['ExternalComponent4', { topBar: { subtitle: { text: 'Default' } } }],
    ]);
  });

  it('starts the app again when an edit changes what it is made of', async () => {
    const reload = jest.spyOn(DevSettings, 'reload').mockImplementation(() => {});
    const before = nativeScreen('Map', {});
    register(before);
    registerScreens([{ source: 'screens', screens: { Map: before } }]);
    const after = nativeScreen('Map', { useProps: () => ({ zoom: 3 }) });
    register(after, before);
    await Promise.resolve();
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
