import { Platform } from 'react-native';
import type { Layout } from '../interfaces/Layout';
import { showInColumn } from './commands';
import { toNativeLayout } from './layout';
import { commands as native, onNativeEvent } from './native';
import { coversColumn, listenToSplitViews, notePlaces, placeOf } from './places';
import { registerScreen } from './screens';

jest.mock('./native', () => ({
  commands: { showInColumn: jest.fn() },
  onNativeEvent: jest.fn(() => () => {}),
}));

const mockedShow = (native as unknown as { showInColumn: jest.Mock }).showInColumn;

const screen = (id: string, name = 'Details'): Layout => ({ component: { id, name } });

// A split view whose columns are stacks, each with one screen; the ids carry the prefix.
function showSplit(prefix: string) {
  const column = (name: string) => ({
    stack: { id: `${prefix}${name}Stack`, children: [screen(`${prefix}${name}`)] },
  });
  const layout: Layout = {
    splitView: {
      id: `${prefix}Split`,
      master: column('Library'),
      detail: column('Details'),
      supplementary: column('Items'),
      compact: column('Compact'),
    },
  };
  notePlaces(toNativeLayout(layout), 'root');
}

// The listener of the stacked state; Jest clears the calls of the mocks before each test.
let stacked: (event: { componentId: string; stacked: boolean }) => void;

describe('the place of a screen shown in a column', () => {
  beforeAll(() => {
    registerScreen('Details', { component: () => () => null, options: () => ({}) });
    listenToSplitViews();
    const call = (onNativeEvent as jest.Mock).mock.calls.find(
      ([name]) => name === 'SplitViewColumnsStacked'
    );
    stacked = call[1];
  });

  beforeEach(() => {
    jest.replaceProperty(Platform, 'OS', 'ios');
    mockedShow.mockImplementation((_commandId, _componentId, _column, node) =>
      Promise.resolve(node.id)
    );
  });

  it('is the first of the column stack it shows in', async () => {
    showSplit('a');
    await showInColumn('aLibrary', 'secondary', screen('aShown'));
    expect(placeOf('aShown')).toMatchObject({ stack: 'aDetailsStack', index: 0 });

    await showInColumn('aLibrary', 'supplementary', screen('aShownItem'));
    expect(placeOf('aShownItem')).toMatchObject({ stack: 'aItemsStack', index: 0 });
  });

  it('is over the screen that asks in the compact column', async () => {
    showSplit('b');
    await showInColumn('bCompact', 'secondary', screen('bShown'));
    expect(placeOf('bShown')).toMatchObject({ stack: 'bCompactStack', index: 1 });

    await showInColumn('bShown', 'secondary', screen('bShownNext'));
    expect(placeOf('bShownNext')).toMatchObject({ stack: 'bCompactStack', index: 2 });
  });

  it('is in the secondary pane on Android, whatever the column', async () => {
    jest.replaceProperty(Platform, 'OS', 'android');
    showSplit('c');
    await showInColumn('cLibrary', 'supplementary', screen('cShown'));
    expect(placeOf('cShown')).toMatchObject({ stack: 'cDetailsStack', index: 0 });
  });

  it('goes back to the column before it while the columns show as one stack', async () => {
    showSplit('d');
    await showInColumn('dLibrary', 'secondary', screen('dShown'));
    expect(coversColumn('dShown')).toBe(false);

    stacked({ componentId: 'dSplit', stacked: true });
    expect(coversColumn('dShown')).toBe(true);
    expect(coversColumn('dLibrary')).toBe(false);
    expect(coversColumn('dCompact')).toBe(false);

    stacked({ componentId: 'dSplit', stacked: false });
    expect(coversColumn('dShown')).toBe(false);
  });

  it('knows the column of a screen in tabs of a column', async () => {
    const tab = (id: string) => ({ stack: { id: `${id}Stack`, children: [screen(id)] } });
    const layout: Layout = {
      splitView: {
        id: 'eSplit',
        master: { bottomTabs: { id: 'eTabs', children: [tab('eLibrary')] } },
        detail: { bottomTabs: { id: 'eDetailTabs', children: [tab('eDetails')] } },
      },
    };
    notePlaces(toNativeLayout(layout), 'root');
    await showInColumn('eLibrary', 'secondary', screen('eShown'));
    // The secondary column is tabs, not a stack: UIKit shows the screen in place of them.
    expect(placeOf('eShown')).toMatchObject({ stack: null, index: 0 });

    stacked({ componentId: 'eSplit', stacked: true });
    // The first screen of a stack in tabs of the column isn't the column's own stack.
    expect(coversColumn('eDetails')).toBe(false);
  });

  it('starts anew when the split view is sent again with its id', () => {
    showSplit('f');
    stacked({ componentId: 'fSplit', stacked: true });
    notePlaces(toNativeLayout(screen('fDetails')), 'root');
    showSplit('f');
    expect(coversColumn('fDetails')).toBe(false);

    // A stack of the old split view sent again out of a split view is in no column.
    notePlaces(
      toNativeLayout({ stack: { id: 'fDetailsStack', children: [screen('fHome')] } }),
      'root'
    );
    stacked({ componentId: 'fSplit', stacked: true });
    expect(coversColumn('fHome')).toBe(false);
  });
});
