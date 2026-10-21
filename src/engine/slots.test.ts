import {
  enableSlots,
  holdSlot,
  isSlotName,
  preparedPadding,
  removeSlots,
  showSlot,
  slotCommitted,
  slotName,
  slotStore,
} from './slots';

const FRAME = { width: 411, height: 801, padding: { top: 88, right: 0, bottom: 24, left: 0 } };

function slot(id: string) {
  return slotStore.get().slots.find((other) => other.id === id);
}

describe('slots of a prepared push', () => {
  beforeEach(() => {
    enableSlots(({ children }) => children);
  });

  it('names a React screen as a slot', () => {
    expect(isSlotName(slotName('Component1', 'Home'))).toBe(true);
    expect(isSlotName('Home')).toBe(false);
    expect(isSlotName(undefined)).toBe(false);
  });

  it('holds a slot until it is shown, without a frame at once', async () => {
    slotName('Component2', 'Details');
    holdSlot('Component2');
    expect(slot('Component2')?.held).toBe(true);

    await showSlot('Component2');

    expect(slot('Component2')?.held).toBe(false);
    expect(slot('Component2')?.frame).toBeUndefined();
    expect(preparedPadding('Component2')).toBeUndefined();
  });

  it('shows a slot at its frame and resolves once React committed it', async () => {
    slotName('Component3', 'Details');
    holdSlot('Component3');
    let shown = false;
    const showing = showSlot('Component3', FRAME).then(() => {
      shown = true;
    });

    expect(slot('Component3')).toMatchObject({ held: false, frame: FRAME });
    expect(preparedPadding('Component3')).toEqual([88, 0, 24, 0]);
    await Promise.resolve();
    expect(shown).toBe(false);

    slotCommitted(slot('Component3')!.key);
    await showing;
    expect(shown).toBe(true);
  });

  it('fails the push that waits for a slot that goes', async () => {
    slotName('Component4', 'Details');
    holdSlot('Component4');
    const showing = showSlot('Component4', FRAME);

    removeSlots(['Component4']);

    await expect(showing).rejects.toThrow("the screen 'Component4' is no longer shown");
  });

  it('fails to show a slot that is not there', async () => {
    await expect(showSlot('Component5', FRAME)).rejects.toThrow(
      "the screen 'Component5' is no longer shown"
    );
  });
});
