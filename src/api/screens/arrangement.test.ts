import { defineArrangement, toEngineArrangement } from './arrangement';

describe('the settings of a pair', () => {
  it('puts the primary of a split first and that of an overlay at the trailing side by default', () => {
    expect(toEngineArrangement('Profile', { secondary: 'Details' }).primaryEdge).toBe('leading');
    expect(
      toEngineArrangement('Profile', { secondary: 'Details', style: 'overlay' }).primaryEdge
    ).toBe('trailing');
  });

  it('keeps the edge an entry sets, on a split too', () => {
    const { definition } = defineArrangement({
      primary: 'Details',
      secondary: 'Profile',
      primaryEdge: 'trailing',
    });

    expect(toEngineArrangement(definition.primary, definition)).toMatchObject({
      primary: 'Details',
      secondary: 'Profile',
      style: 'split',
      primaryEdge: 'trailing',
    });
  });
});
