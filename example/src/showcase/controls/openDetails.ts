import { navigation } from '@apexnative/navigation';
import type { ControlValues } from '../../sections/controls';
import { sectionControls, type Section } from '../../sections/scenario';
import { buildOptions } from './optionsCode';

/** How the panel shows Details: pushed, in a modal, or as a plain list pushed with the same options. */
export type ShowKind = 'push' | 'modal' | 'list';

let active: Section | undefined;
let opened: ControlValues | undefined;

/** The section of the button pressed last: Details shows its controls. */
export function setActiveSection(section: Section) {
  active = section;
}

/** Shows Details with the options the values make. */
export function openDetails(section: Section, values: ControlValues, kind: ShowKind) {
  active = section;
  opened = values;
  const options = buildOptions(sectionControls(section), values);
  if (kind === 'list') return navigation.push('PlainList', { options });
  return kind === 'push'
    ? navigation.push('Details', { options })
    : navigation.showModal('Details', { options });
}

/**
 * For a Details screen as it mounts: the active section, and the values it was opened with. Only the screen
 * `openDetails` showed gets them; one pushed from Details starts with none.
 */
export function takeOpened(): { section: Section | undefined; values: ControlValues } {
  const values = opened ?? {};
  opened = undefined;
  return { section: active, values };
}
