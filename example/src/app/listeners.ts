import type { NavigationListeners } from '@apexnative/navigation';
import { log } from '../showcase/journal';

/** Every event of the navigation goes to the Journal. */
export const listeners: NavigationListeners = {
  screenDidAppear: (event) =>
    log('event', 'screenDidAppear', `${event.screen.name} ${event.screen.id}`),
  screenDidDisappear: (event) =>
    log('event', 'screenDidDisappear', `${event.screen.name} ${event.screen.id}`),
  screenPopped: (event) => log('event', 'screenPopped', event.screen.id),
  modalDismissed: (event) => log('event', 'modalDismissed', event.screen.id),
  modalDismissAttempted: (event) => log('event', 'modalDismissAttempted', event.screen.id),
  sheetDetentChanged: (event) => log('event', 'sheetDetentChanged', String(event.detent)),
  buttonPressed: (event) =>
    log('event', 'buttonPressed', `${event.buttonId} on ${event.screen.id}`),
  tabSelected: (event) =>
    log('event', 'tabSelected', `${event.unselectedTabIndex} → ${event.selectedTabIndex}`),
  tabPressed: (event) => log('event', 'tabPressed', event.tabIndex),
  searchChanged: (event) =>
    log(
      'event',
      'searchChanged',
      `"${event.text}"${event.scope ? `, scope ${event.scope}` : ''}${event.tokens ? `, tokens [${event.tokens.join(', ')}]` : ''}`
    ),
  searchSubmitted: (event) =>
    log(
      'event',
      'searchSubmitted',
      `"${event.text}"${event.scope ? `, scope ${event.scope}` : ''}`
    ),
  searchCancelled: (event) => log('event', 'searchCancelled', event.screen.id),
  searchSuggestionPicked: (event) =>
    log('event', 'searchSuggestionPicked', `${event.suggestionId} on ${event.screen.id}`),
  keyCommandPressed: (event) => log('event', 'keyCommandPressed', event.keyCommandId),
  splitViewDisplayModeChanged: (event) =>
    log(
      'event',
      'splitViewDisplayModeChanged',
      `${event.displayMode}${event.inspectorVisible === undefined ? '' : `, inspector ${event.inspectorVisible ? 'shown' : 'hidden'}`}`
    ),
  titleRenamed: (event) => log('event', 'titleRenamed', `"${event.text}" on ${event.screen.id}`),
  stateChanged: (state) =>
    log('state', 'stateChanged', `${state.screen?.name ?? 'no screen'}, tab ${state.tab ?? '—'}`),
};
