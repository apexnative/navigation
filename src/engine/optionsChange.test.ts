import type { Options } from '../interfaces/Options';
import { optionsChange } from './optionsChange';

describe('the change of a screen’s options after an edit', () => {
  it('is nothing for the same options', () => {
    const make = (): Options => ({
      topBar: { title: { text: 'Home' } },
      layout: { orientation: ['portrait'] },
    });
    expect(optionsChange(make(), make())).toEqual({ removed: [] });
  });

  it('gives only what has another value or is new', () => {
    expect(
      optionsChange(
        { topBar: { title: { text: 'Home' }, subtitle: { text: 'All' } } },
        {
          topBar: {
            title: { text: 'Start' },
            subtitle: { text: 'All' },
            largeTitle: { visible: true },
          },
        }
      )
    ).toEqual({
      merge: { topBar: { title: { text: 'Start' }, largeTitle: { visible: true } } },
      removed: [],
    });
  });

  it('gives an array whole when any item changed', () => {
    const before = { topBar: { rightButtons: [{ id: 'save', text: 'Save' }, { id: 'more' }] } };
    const after = { topBar: { rightButtons: [{ id: 'save', text: 'Done' }, { id: 'more' }] } };
    expect(optionsChange(before, after).merge).toEqual(after);
  });

  it('compares functions by their source, as a module that ran again makes them anew', () => {
    const make = () => ({ topBar: { rightButtons: [{ id: 'save', onPress: () => {} }] } });
    expect(optionsChange(make(), make())).toEqual({ removed: [] });
    const other = {
      topBar: { rightButtons: [{ id: 'save', onPress: () => console.log('saved') }] },
    };
    expect(optionsChange(make(), other).merge).toEqual(other);
  });

  it('names the paths of the options taken out', () => {
    expect(
      optionsChange(
        {
          topBar: { title: { text: 'Home' }, subtitle: { text: 'All' } },
          statusBar: { style: 'dark' },
        },
        { topBar: { title: { text: 'Home' } } }
      )
    ).toEqual({ removed: [['topBar', 'subtitle'], ['statusBar']] });
  });

  it('gives a color, an icon or a menu whole when a part of it is taken out', () => {
    expect(
      optionsChange(
        { topBar: { title: { color: { light: 'black', dark: 'white' } } } },
        { topBar: { title: { color: { light: 'black' } } } }
      )
    ).toEqual({
      merge: { topBar: { title: { color: { light: 'black' } } } },
      removed: [['topBar', 'title', 'color']],
    });
  });
});
