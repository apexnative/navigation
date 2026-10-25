import { Platform } from 'react-native';
import { push } from './commands';
import { commands as native } from './native';
import { placeOf } from './places';
import { propsOf } from './props';
import * as screens from './screens';

jest.mock('./native', () => ({
  commands: { push: jest.fn() },
  onNativeEvent: () => () => {},
}));

const mocked = native as unknown as Record<'push', jest.Mock>;

// A preview's push: the screen waits for a press on the view with the `nativeID`.
function preview(id: string) {
  return {
    component: {
      id,
      name: 'Details',
      passProps: { item: 1 },
      options: { preview: { nativeID: 'row' } },
    },
  };
}

describe('a preview that goes before it is pressed (iOS)', () => {
  beforeAll(() => {
    screens.registerScreen('Details', { component: () => () => null, options: () => ({}) });
  });

  beforeEach(() => {
    jest.replaceProperty(Platform, 'OS', 'ios');
    jest.useFakeTimers();
    mocked.push.mockRejectedValue(new Error('[navigation] push: the preview went'));
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it('forgets the props and the place of its screen, which never mounted', async () => {
    await expect(push('Component1', preview('Preview1'))).rejects.toThrow('the preview went');
    expect(propsOf('Preview1')).toEqual({ item: 1 });

    await jest.advanceTimersByTimeAsync(1000);
    expect(propsOf('Preview1')).toEqual({});
    expect(placeOf('Preview1')).toBeUndefined();
  });

  it('leaves them to the root of a screen that was pressed and mounted', async () => {
    jest.spyOn(screens, 'hasRoot').mockReturnValue(true);

    await expect(push('Component1', preview('Preview2'))).rejects.toThrow('the preview went');
    await jest.advanceTimersByTimeAsync(1000);
    expect(propsOf('Preview2')).toEqual({ item: 1 });
  });

  it('leaves them to a root that mounts just after the push failed', async () => {
    const hasRoot = jest.spyOn(screens, 'hasRoot').mockReturnValue(false);

    await expect(push('Component1', preview('Preview3'))).rejects.toThrow('the preview went');
    hasRoot.mockReturnValue(true);
    await jest.advanceTimersByTimeAsync(1000);
    expect(propsOf('Preview3')).toEqual({ item: 1 });
  });

  it('keeps the props of a push that is no preview', async () => {
    const layout = { component: { id: 'Component4', name: 'Details', passProps: { item: 4 } } };

    await expect(push('Component1', layout)).rejects.toThrow('the preview went');
    await jest.advanceTimersByTimeAsync(1000);
    expect(propsOf('Component4')).toEqual({ item: 4 });
  });
});
