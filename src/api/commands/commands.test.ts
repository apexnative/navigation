import { holdLeave, replace, setStack, showModal } from './commands';
import { commands as native } from '../../engine/native';
import { registerScreen } from '../../engine/screens';
import { forgetPlace } from '../../engine/places';
import { screenUnmounted } from '../events/screenLifecycle';
import type { LayoutNode } from '../../engine/layout';

jest.mock('../../engine/native', () => {
  const listeners = new Map<string, (mockEvent: unknown) => void>();
  return {
    commands: {
      showModal: jest.fn(),
      setStackRoot: jest.fn(),
      dismissModal: jest.fn(),
    },
    onNativeEvent: (mockName: string, mockListener: (mockEvent: unknown) => void) => {
      listeners.set(mockName, mockListener);
      return () => listeners.delete(mockName);
    },
    listeners,
  };
});

const mocked = native as unknown as Record<
  'showModal' | 'setStackRoot' | 'dismissModal',
  jest.Mock
>;
const { listeners } = jest.requireMock('../../engine/native') as {
  listeners: Map<string, (event: unknown) => void>;
};

// The ids of the left buttons of a screen node as the native side gets it.
function leftButtons(node: LayoutNode): (string | undefined)[] {
  const options = node.data.options as { topBar?: { leftButtons?: { id?: string }[] } } | undefined;
  return options?.topBar?.leftButtons?.map((button) => button.id) ?? [];
}

function press(componentId: string, buttonId: string) {
  listeners.get('NavigationButtonPressed')?.({ componentId, buttonId });
}

// The modal's stack node and its first screen, from the last showModal.
function shownModal(): { stack: LayoutNode; first: LayoutNode } {
  const stack = mocked.showModal.mock.calls.at(-1)![1] as LayoutNode;
  return { stack, first: stack.children[0]! };
}

// The screens of the last setStackRoot.
function newStack(): LayoutNode[] {
  return mocked.setStackRoot.mock.calls.at(-1)![2] as LayoutNode[];
}

describe('the close button of a modal', () => {
  beforeAll(() => {
    const own = () => ({ topBar: { leftButtons: [{ id: 'Own', text: 'Own' }] } });
    registerScreen('Details', { component: () => () => null, options: () => ({}) });
    registerScreen('Profile', { component: () => () => null, options: own });
  });

  beforeEach(() => {
    mocked.showModal.mockImplementation((_commandId, node: LayoutNode) => Promise.resolve(node.id));
    mocked.setStackRoot.mockResolvedValue('');
    mocked.dismissModal.mockResolvedValue('');
  });

  it('goes to the new first screen of setStack, before its own buttons', async () => {
    await showModal('Details', { closeButton: true });
    const { first } = shownModal();
    expect(leftButtons(first)).toEqual(['CloseButton']);

    await setStack(first.id, ['Profile', 'Details']);
    const [top, second] = newStack();
    expect(leftButtons(top!)).toEqual(['CloseButton', 'Own']);
    expect(leftButtons(second!)).toEqual([]);

    press(top!.id, 'CloseButton');
    expect(mocked.dismissModal).toHaveBeenCalledWith(expect.any(String), top!.id, undefined);
  });

  it('goes to the screen of replace, and asks a screen that holds the user', async () => {
    await showModal('Details', { closeButton: true });
    const id = await replace(shownModal().first.id, 'Profile');
    const [top] = newStack();
    expect(top!.id).toBe(id);
    expect(leftButtons(top!)).toEqual(['CloseButton', 'Own']);

    const attempt = jest.fn();
    const release = holdLeave(id, attempt);
    press(id, 'CloseButton');
    expect(attempt).toHaveBeenCalledTimes(1);
    expect(mocked.dismissModal).not.toHaveBeenCalled();
    release();
  });

  it('follows the first screen through several commands', async () => {
    await showModal('Details', { closeButton: true });
    const first = shownModal().first.id;
    const second = await replace(first, 'Details');
    // The old first screen goes after the command noted the new one.
    forgetPlace(first);
    screenUnmounted(first);

    await setStack(second, ['Profile']);
    expect(leftButtons(newStack()[0]!)).toEqual(['CloseButton', 'Own']);
  });

  it('stays off a modal without it', async () => {
    await showModal('Details');
    await setStack(shownModal().first.id, ['Profile']);
    expect(leftButtons(newStack()[0]!)).toEqual(['Own']);
  });

  it('is gone with its modal', async () => {
    await showModal('Details', { closeButton: true });
    const { stack, first } = shownModal();
    forgetPlace(first.id);
    screenUnmounted(first.id);

    // A screen noted in that stack again (an id reused by mistake) no longer gets it.
    mocked.showModal.mockImplementation(() => Promise.resolve(stack.id));
    await showModal('Details');
    await setStack(shownModal().first.id, ['Profile']);
    expect(leftButtons(newStack()[0]!)).toEqual(['Own']);
  });
});
