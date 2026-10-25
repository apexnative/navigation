import { screenProps } from './screens';

jest.mock('./native', () => ({
  commands: {},
  onNativeEvent: jest.fn(() => () => {}),
}));

describe('the props a screen renders with', () => {
  it('are the props kept by its id and its id; the root tag stays with the root', () => {
    expect(screenProps({ title: 'Home' }, { componentId: 'Component3', rootTag: 11 })).toEqual({
      title: 'Home',
      componentId: 'Component3',
    });
  });

  it("take a tab cell's state from the native side, over the props kept by its id", () => {
    const stored = { title: 'Inbox', selected: false };
    const root = {
      componentId: 'tabs1.1',
      rootTag: 21,
      tabIndex: 1,
      selected: true,
      badge: '3',
      dot: false,
    };
    expect(screenProps(stored, root)).toEqual({
      title: 'Inbox',
      componentId: 'tabs1.1',
      tabIndex: 1,
      selected: true,
      badge: '3',
      dot: false,
    });
  });

  it('keep the id of the root, never one in the props kept by it', () => {
    expect(screenProps({ componentId: 'Other' }, { componentId: 'Component5' })).toEqual({
      componentId: 'Component5',
    });
  });
});
