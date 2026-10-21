import type { Section } from '../scenario';
import pushAndPop from './12.1-push-and-pop';
import aStackOfItsOwn from './12.2-a-stack-of-its-own';
import fromAnywhere from './12.3-from-anywhere';
import deepLink from './12.4-deep-link';
import oneScreenPerItem from './12.5-one-screen-per-item';

/** Section 12: the commands of a stack. */
export const stackSection: Section = {
  id: 12,
  title: 'Stack',
  intro:
    'The commands of a stack: push and pop, back to a screen or to the first one, new screens in place of the stack, `navigation` from outside the screens, and one screen per item with `key`. Each group runs its commands with a button; the code of the button is under it.',
  scenarios: [pushAndPop, aStackOfItsOwn, fromAnywhere, deepLink, oneScreenPerItem],
  runsOnly: true,
};
