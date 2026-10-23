// The events the native side sends for a screen, as the JavaScript side receives them (`ANN.<Name>`). Each carries the
// id of the screen as `componentId`; the public events give it to the app as `screen: { id, name }`.

/**
 * What a component that appears is. The three appearance events are sent for screens and for the React components of
 * the bars; the app is told only about screens.
 */
export type ComponentType =
  'Component' | 'TopBarTitle' | 'TopBarButton' | 'TopBarBackground' | 'BottomTabItem';

/** The fields every appearance event has. */
interface ComponentAppearanceEvent {
  /** The id of the component: of the screen, or the bar component's own id. */
  componentId: string;
  /** The name of the component: for a screen, its name without any internal prefix. */
  componentName: string;
  /**
   * What the component is. `'Component'` is a screen; the other values are the React components set in the options of
   * a bar (`BottomTabItem` on Android only).
   */
  componentType: ComponentType;
}

/**
 * A screen starts to appear: its transition has begun. At most one is sent per appearance, after the content of the
 * screen is drawn or the wait for it has ended.
 */
export interface ComponentWillAppearEvent extends ComponentAppearanceEvent {}

/** A screen is in place: its transition has ended. At most one is sent per appearance. */
export interface ComponentDidAppearEvent extends ComponentAppearanceEvent {}

/**
 * A screen disappeared: another screen covers it, or it left. It is sent only for a screen that had appeared, and it
 * also makes the navigation state recalculate.
 */
export interface ComponentDidDisappearEvent extends ComponentAppearanceEvent {}

/**
 * A screen left its stack, by any means: the back button, a swipe, `pop`, `popTo`, `popToRoot`, a new stack root, or
 * a tab left with `popToRootOnBlur`. It is sent before the content of the screen stops, once for each screen that
 * left (for both screens of a pair). A cancelled swipe sends nothing.
 */
export interface ScreenPoppedEvent {
  /** The id of the screen that left. */
  componentId: string;
}

/**
 * A button was pressed. The sources are the buttons of the bar and of the toolbar, the items of their menus, the item
 * of the title menu (iOS), the back button when it is set not to pop, a swipe back with
 * `navigationButtonEventOnSwipeBack` (iOS), the hardware back in a modal that stays open (Android) and the items of
 * the menu of a preview (iOS).
 */
export interface NavigationButtonPressedEvent {
  /** The id of the screen whose button it is: the visible screen, and for a pair its primary. */
  componentId: string;
  /**
   * The id of the button or of the menu item. The back button has the id set in `topBar.backButton.id`, or
   * `ApexNavigation.back`; the hardware back of a modal has `ApexNavigation.hardwareBackButton`.
   */
  buttonId: string;
  /** (iOS) For a button with `toggle`: its new state, already written into the options of the screen. */
  selected?: boolean;
}

/**
 * A modal went away, however it was closed: a command, a swipe, the hardware back, a tap on the dimmed area of a
 * sheet or outside a popover. Closing every modal at once sends one event with their count. Replacing the root and
 * reloading JavaScript send none.
 */
export interface ModalDismissedEvent {
  /** The id of the visible screen of the modal (of the topmost modal when several were closed). */
  componentId: string;
  /** The name of that screen. */
  componentName: string;
  /** How many modals were closed. */
  modalsDismissed: number;
}

/**
 * The user tried to dismiss a modal whose `modal.swipeToDismiss` is `false`: a swipe down on a sheet, or a tap on the
 * dimmed area or outside a popover. The modal stays. The hardware back button of Android sends a button event
 * instead.
 */
export interface ModalAttemptedToDismissEvent {
  /** The id of the visible screen of the modal. */
  componentId: string;
}

/**
 * The user dragged a sheet to another detent. A change made through `mergeOptions` is not reported.
 */
export interface SheetDetentChangedEvent {
  /** The id of the visible screen of the modal. */
  componentId: string;
  /**
   * The detent the sheet stopped at: `'medium'`, `'large'`, or a height (points on iOS, dp on Android). `'content'`
   * is sent on iOS for a detent that fits the content; Android names such a detent `'medium'`.
   */
  detent: 'medium' | 'large' | 'content' | number;
}

/**
 * The text, the focus or the scope of a search field changed, or a token was removed from it. Changes made through
 * `mergeOptions` are not reported. The screen and its results component each get the event.
 */
export interface SearchBarUpdatedEvent {
  /** The id of the screen with the search field. */
  componentId: string;
  /** The text in the field. */
  text: string;
  /** Whether the field has the focus. */
  isFocused: boolean;
  /** The id of the selected scope filter. Set only when the field has a scope bar. */
  scope?: string;
  /** (iOS) The ids of the tokens in the field. Set only when the field uses tokens. */
  tokens?: string[];
}

/** The search key of the keyboard was pressed. The keyboard hides; the text and the results stay. */
export interface SearchBarSubmittedEvent {
  /** The id of the screen with the search field. */
  componentId: string;
  /** The text in the field. */
  text: string;
  /** The id of the selected scope filter. Set only when the field has a scope bar. */
  scope?: string;
  /** (iOS) The ids of the tokens in the field. Set only when the field uses tokens. */
  tokens?: string[];
}

/**
 * The search was cancelled: the Cancel button (iOS), or closing the search with the arrow or the back button
 * (Android, followed by a search update).
 */
export interface SearchBarCancelPressedEvent {
  /** The id of the screen with the search field. */
  componentId: string;
}

/** (iOS 16+, Android) The user picked a suggestion of `searchBar.suggestions`. The text stays. */
export interface SearchSuggestionPickedEvent {
  /** The id of the screen with the search field. */
  componentId: string;
  /** The id of the suggestion that was picked. */
  suggestionId: string;
}

/**
 * (iOS 16+) The user renamed the title with the Rename item of the title menu (`topBar.title.rename`). The title text
 * of the screen already is the new one. An empty name restores the old title and sends nothing.
 */
export interface TitleRenamedEvent {
  /** The id of the screen whose title was renamed. */
  componentId: string;
  /** The new title. */
  text: string;
}

/**
 * (iOS 26+) The system moved the accessory above the tabs. It is sent when the accessory is first shown and on each
 * change of its place, never twice for the same place.
 */
export interface BottomAccessoryPlacementChangedEvent {
  /** The id of the accessory component (`bottomTabs.bottomAccessory.component`). */
  componentId: string;
  /** Where the accessory is: above the tab bar (`'regular'`) or beside the collapsed tab bar (`'inline'`). */
  placement: 'regular' | 'inline';
}

/** (iOS) A key command of a screen was pressed on a hardware keyboard. */
export interface KeyCommandPressedEvent {
  /** The id of the screen that has the key command. */
  componentId: string;
  /** The id of the key command, as set in the `keyCommands` of the screen. */
  keyCommandId: string;
}

/**
 * (iOS) The user pressed the preview of a pushed screen (the context menu of `usePreview`). It is sent before the
 * push. If the preview is to be committed and the source screen is still in its stack, the preview screen is pushed
 * afterwards.
 */
export interface PreviewCompletedEvent {
  /** The id of the screen the preview was opened from. */
  componentId: string;
  /** The id of the screen of the preview. */
  previewComponentId: string;
}

/** The modes a split view can show its columns in. */
export type SplitViewDisplayMode =
  | 'automatic'
  | 'secondaryOnly'
  | 'oneBesideSecondary'
  | 'oneOverSecondary'
  | 'twoBesideSecondary'
  | 'twoOverSecondary'
  | 'twoDisplaceSecondary';

/**
 * (iOS) The display mode of a split view changed: the user used the button of the sidebar or swiped, the width
 * changed, or `mergeOptions` set a mode. It is also sent at the first layout, and when the inspector shows or hides.
 * The same state is not sent twice.
 */
export interface SplitViewDisplayModeChangedEvent {
  /** The id of the split view. */
  componentId: string;
  /** The mode the columns are shown in. */
  displayMode: SplitViewDisplayMode;
  /** (iPadOS 26+) Whether the inspector column is visible. Set only for a split view that has an inspector. */
  inspectorVisible?: boolean;
}
