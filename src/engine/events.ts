import { placeOf } from './places';

/** A screen as the app knows it: its id, and its name in `screens`. */
export interface ScreenRef {
  id: string;
  name: string;
}

const NATIVE_FIELDS = ['componentId', 'componentName', 'componentType', 'passProps'] as const;
type NativeFields = (typeof NATIVE_FIELDS)[number];

/** An event of a screen as the app gets it: the screen as `{ id, name }`, without the fields of the native side. */
export type ScreenEventOf<Event> = Omit<Event, NativeFields> & { screen: ScreenRef };

/** The event of a screen as the app gets it. */
export function toScreenEvent<Event extends { componentId: string }>(
  event: Event
): ScreenEventOf<Event> {
  const { componentId, componentName } = event as Event & { componentName?: string };
  const rest: Record<string, unknown> = { ...event };
  for (const field of NATIVE_FIELDS) delete rest[field];
  const name = componentName ?? placeOf(componentId)?.name ?? '';
  return { ...rest, screen: { id: componentId, name } } as unknown as ScreenEventOf<Event>;
}
