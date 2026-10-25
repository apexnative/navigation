import type { TransformsStyle } from 'react-native';

export type Transform = NonNullable<TransformsStyle['transform']>;

/**
 * A shape of the turned shared elements: how it is turned in the grid (`from`) and on its screen (`to`), and what kind
 * of view flies — a view with a mark in its top left corner (its pictures fly), a plain view without children (it moves
 * itself) or a text.
 */
export interface Shape {
  title: string;
  color: string;
  kind: 'marked' | 'plain' | 'text';
  from: Transform;
  to: Transform;
  /** The scale of the tile's parent in the grid: the shape flies from the size it shows at. */
  parentScale?: number;
  note: string;
}

export const SHAPES: Shape[] = [
  {
    title: '0° → 90°',
    color: '#6C5CE7',
    kind: 'marked',
    from: [],
    to: [{ rotate: '90deg' }],
    note: 'Turns a quarter clockwise and keeps its size on the way.',
  },
  {
    title: '0° → 180°',
    color: '#00B894',
    kind: 'marked',
    from: [],
    to: [{ rotate: '180deg' }],
    note: 'Half a turn: it keeps its size half way instead of flattening to a line.',
  },
  {
    title: '45° → −45°',
    color: '#E17055',
    kind: 'marked',
    from: [{ rotate: '45deg' }],
    to: [{ rotate: '-45deg' }],
    note: 'Turns back a quarter through upright.',
  },
  {
    title: '270°',
    color: '#0984E3',
    kind: 'marked',
    from: [],
    to: [{ rotate: '270deg' }],
    note: '270° is −90°: it turns back a quarter, the shorter way, on both platforms.',
  },
  {
    title: 'Scaled parent',
    color: '#FDCB6E',
    kind: 'marked',
    from: [],
    to: [],
    parentScale: 0.5,
    note: 'Its tile is at half size: it grows from the size it shows at, without a jump.',
  },
  {
    title: 'Own transform',
    color: '#D63031',
    kind: 'marked',
    from: [{ translateX: 12 }, { rotate: '20deg' }, { scale: 0.8 }],
    to: [{ rotate: '-30deg' }, { scale: 1.1 }],
    note: 'Moved, turned and scaled in both places: it starts where it shows and lands turned −30°.',
  },
  {
    title: 'Mirrored',
    color: '#E84393',
    kind: 'marked',
    from: [{ scaleX: -1 }, { rotate: '30deg' }],
    to: [],
    note: 'Mirrored and turned 30° in the grid: it starts mirrored as it shows and turns back the right way.',
  },
  {
    title: 'Plain 0° → 90°',
    color: '#00CEC9',
    kind: 'plain',
    from: [],
    to: [{ rotate: '90deg' }],
    note: 'A view without children moves itself: it turns about its middle, wide to tall.',
  },
  {
    title: 'Turned text',
    color: '#A29BFE',
    kind: 'text',
    from: [{ rotate: '-12deg' }],
    to: [],
    note: 'A text turned −12° in the grid straightens on the way.',
  },
];
