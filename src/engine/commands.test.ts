import { Platform } from 'react-native';
import { pop, push } from './commands';
import { commands as native } from './native';
import { registerScreen } from './screens';
import { enableSlots, removeSlots, slotCommitted, slotStore } from './slots';

jest.mock('./native', () => ({
  commands: {
    preparePush: jest.fn(),
    cancelPreparedPush: jest.fn(),
    push: jest.fn(),
    pop: jest.fn(),
  },
  onNativeEvent: () => () => {},
}));

const FRAME = { width: 411, height: 801, padding: { top: 88, right: 0, bottom: 24, left: 0 } };
const mocked = native as unknown as Record<
  'preparePush' | 'cancelPreparedPush' | 'push' | 'pop',
  jest.Mock
>;

// Lets the promises of the engine run.
const settle = () => new Promise<void>((resolve) => setImmediate(() => resolve()));

function slotOf(name: string) {
  const slots = slotStore.get().slots.filter((slot) => slot.name === name);
  return slots[slots.length - 1];
}

describe('a push on Android in one tree', () => {
  beforeAll(() => {
    registerScreen('Details', { component: () => () => null, options: () => ({}) });
    enableSlots(({ children }) => children);
  });

  beforeEach(() => {
    // Every test: Jest resets replaced properties with the mocks.
    jest.replaceProperty(Platform, 'OS', 'android');
    mocked.push.mockImplementation((_commandId, _componentId, node) => Promise.resolve(node.id));
    mocked.pop.mockResolvedValue('Component1');
  });

  it('renders the screen at its prepared frame, then pushes it once React committed it', async () => {
    mocked.preparePush.mockResolvedValue(FRAME);

    const pushing = push('Component1', { component: { name: 'Details' } });
    expect(slotOf('Details').held).toBe(true);
    await settle();

    expect(slotOf('Details')).toMatchObject({ held: false, frame: FRAME });
    expect(mocked.push).not.toHaveBeenCalled();

    slotCommitted(slotOf('Details').key);
    await expect(pushing).resolves.toBe(slotOf('Details').id);
    expect(mocked.push).toHaveBeenCalledTimes(1);
  });

  it('pushes the usual way when the native side does not prepare the screen', async () => {
    mocked.preparePush.mockResolvedValue(null);

    const pushed = await push('Component1', { component: { name: 'Details' } });

    expect(pushed).toBe(slotOf('Details').id);
    expect(slotOf('Details')).toMatchObject({ held: false });
    expect(slotOf('Details').frame).toBeUndefined();
  });

  it('drops the prepared screen when its slot goes before the push', async () => {
    mocked.preparePush.mockResolvedValue(FRAME);

    const pushing = push('Component1', { component: { name: 'Details' } });
    await settle();
    removeSlots([slotOf('Details').id]);

    await expect(pushing).rejects.toThrow('is no longer shown');
    expect(mocked.cancelPreparedPush).toHaveBeenCalledTimes(1);
    expect(mocked.push).not.toHaveBeenCalled();
  });

  it('sends a pop made after a prepared push once the push went out', async () => {
    mocked.preparePush.mockResolvedValue(FRAME);

    const pushing = push('Component1', { component: { name: 'Details' } });
    const popping = pop('Component1');
    await settle();
    expect(mocked.pop).not.toHaveBeenCalled();

    slotCommitted(slotOf('Details').key);
    await Promise.all([pushing, popping]);
    expect(mocked.push.mock.invocationCallOrder[0]).toBeLessThan(
      mocked.pop.mock.invocationCallOrder[0]
    );
  });

  it('pushes after half a second when React never commits the slot', async () => {
    jest.useFakeTimers();
    try {
      mocked.preparePush.mockResolvedValue(FRAME);

      const pushing = push('Component1', { component: { name: 'Details' } });
      await jest.advanceTimersByTimeAsync(499);
      expect(mocked.push).not.toHaveBeenCalled();

      await jest.advanceTimersByTimeAsync(1);
      await expect(pushing).resolves.toBe(slotOf('Details').id);
    } finally {
      jest.useRealTimers();
    }
  });
});
