import type { Layout } from '../interfaces/Layout';
import { toNativeLayout } from './layout';
import { forgetPlace, layoutsOf, notePlaces } from './places';
import { propsOf } from './props';
import { registerScreen } from './screens';

jest.mock('./native', () => ({
  commands: {},
  onNativeEvent: jest.fn(() => () => {}),
}));

type Fields = Record<string, any>;

const screen = (id: string): Layout => ({ component: { id, name: 'Details' } });

// A stack whose options name a title component with props; the id the library gave it.
function showStack(id: string, screens: string[]): string {
  const node = toNativeLayout({
    stack: {
      id,
      children: screens.map(screen),
      options: { topBar: { title: { component: { name: 'Clock', passProps: { count: 3 } } } } },
    },
  });
  notePlaces(node, 'root');
  return (node.data.options as Fields).topBar.title.component.componentId;
}

describe('the props of a component in the options of a layout', () => {
  beforeAll(() => {
    registerScreen('Details', { component: () => () => null, options: () => ({}) });
  });

  it('stay while a screen of the layout lives, and go with its last one', () => {
    const title = showStack('aStack', ['aFirst', 'aSecond']);
    expect(propsOf(title)).toEqual({ count: 3 });
    forgetPlace('aFirst');
    expect(propsOf(title)).toEqual({ count: 3 });
    forgetPlace('aSecond');
    expect(propsOf(title)).toEqual({});
  });

  it('stay for a screen that came into the layout later', () => {
    const title = showStack('bStack', ['bFirst']);
    notePlaces(
      toNativeLayout(screen('bPushed')),
      'root',
      'bStack',
      1,
      undefined,
      'root',
      layoutsOf('bFirst')
    );
    forgetPlace('bFirst');
    expect(propsOf(title)).toEqual({ count: 3 });
    forgetPlace('bPushed');
    expect(propsOf(title)).toEqual({});
  });
});
