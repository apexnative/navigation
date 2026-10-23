import { toNativeOptions } from './options';
import { forgetProps, isOptionsComponent, propsOf } from './props';

type Fields = Record<string, any>;

const titleOf = (options: Fields) => options.topBar.title.component;

describe('the id of a component in options', () => {
  it('stays the same for the same node, place and name', () => {
    const first = toNativeOptions<Fields>(
      { topBar: { title: { component: { name: 'Clock', passProps: { count: 1 } } } } },
      'Component1'
    );
    const second = toNativeOptions<Fields>(
      { topBar: { title: { component: { name: 'Clock', passProps: { count: 2 } } } } },
      'Component1'
    );
    expect(titleOf(first).componentId).toBe('Component1/topBar.title/Clock');
    expect(titleOf(second).componentId).toBe(titleOf(first).componentId);
    expect(propsOf(titleOf(second).componentId)).toEqual({ count: 2 });
  });

  it('differs for another name, another place and another node', () => {
    const ids = [
      toNativeOptions<Fields>(
        { topBar: { title: { component: { name: 'Clock' } } } },
        'Component1'
      ),
      toNativeOptions<Fields>({ topBar: { title: { component: { name: 'Logo' } } } }, 'Component1'),
      toNativeOptions<Fields>(
        { topBar: { title: { component: { name: 'Clock' } } } },
        'Component2'
      ),
    ].map((options) => titleOf(options).componentId);
    const background = toNativeOptions<Fields>(
      { topBar: { background: { component: { name: 'Clock' } } } },
      'Component1'
    ).topBar.background.component.componentId;
    expect(new Set([...ids, background]).size).toBe(4);
    expect(background).toBe('Component1/topBar.background/Clock');
  });

  it('names the component of a bar button by the button, wherever it is in the list', () => {
    const first = toNativeOptions<Fields>(
      { topBar: { rightButtons: [{ id: 'Count', component: { name: 'Badge' } }] } },
      'Component1'
    );
    const second = toNativeOptions<Fields>(
      {
        topBar: {
          rightButtons: [
            { id: 'Share', component: { name: 'Badge' } },
            { id: 'Count', component: { name: 'Badge' } },
          ],
        },
      },
      'Component1'
    );
    expect(first.topBar.rightButtons[0].component.componentId).toBe(
      'Component1/topBar.rightButtons[Count]/Badge'
    );
    expect(second.topBar.rightButtons[1].component.componentId).toBe(
      first.topBar.rightButtons[0].component.componentId
    );
    expect(second.topBar.rightButtons[0].component.componentId).toBe(
      'Component1/topBar.rightButtons[Share]/Badge'
    );
  });

  it('names an item without an id by its place in the list', () => {
    const options = toNativeOptions<Fields>(
      { topBar: { rightButtons: [{ component: { name: 'Badge' } }] } },
      'Component1'
    );
    expect(options.topBar.rightButtons[0].component.componentId).toBe(
      'Component1/topBar.rightButtons[0]/Badge'
    );
  });

  it('names the results of a search by their field', () => {
    const options = toNativeOptions<Fields>(
      { topBar: { searchBar: { resultsComponent: { name: 'Results' } } } },
      'Component1'
    );
    expect(options.topBar.searchBar.resultsComponent.componentId).toBe(
      'Component1/topBar.searchBar.resultsComponent/Results'
    );
  });

  it('keeps an id of its own', () => {
    const options = toNativeOptions<Fields>(
      { topBar: { title: { component: { id: 'Title', name: 'Clock' } } } },
      'Component1'
    );
    expect(titleOf(options).componentId).toBe('Title');
  });

  it('is new for each conversion without an owner', () => {
    const options = { topBar: { title: { component: { name: 'Clock' } } } };
    expect(titleOf(toNativeOptions<Fields>(options)).componentId).not.toBe(
      titleOf(toNativeOptions<Fields>(options)).componentId
    );
  });
});

describe('the props of a component in options', () => {
  it('stay when its root goes, and go with the node', () => {
    const options = toNativeOptions<Fields>(
      { topBar: { title: { component: { name: 'Clock', passProps: { count: 3 } } } } },
      'Component7'
    );
    const id = titleOf(options).componentId;
    expect(isOptionsComponent(id)).toBe(true);
    expect(propsOf(id)).toEqual({ count: 3 });
    forgetProps('Component7');
    expect(propsOf(id)).toEqual({});
    expect(isOptionsComponent(id)).toBe(false);
  });

  it('of an id the app gave go with its root as before', () => {
    toNativeOptions<Fields>(
      { topBar: { title: { component: { id: 'Title', name: 'Clock' } } } },
      'Component8'
    );
    expect(isOptionsComponent('Title')).toBe(false);
  });
});
