import type {
  OptionsFab,
  OptionsTopBarButton,
  OptionsTopBarButtonMenu,
  OptionsTopBarButtonMenuItem,
  OptionsTopBarButtonSubmenu,
} from '../../interfaces/Options';
import type { ButtonPressedEvent } from '../events/navigationEvents';
import type { ScreenNavigation } from '../hooks/useNavigation';

/** A press of a button or of an item of its menu, with the navigation of the screen whose button it is. */
export type ButtonPressEvent = ButtonPressedEvent & { navigation: ScreenNavigation };

/**
 * Called when the button is pressed. The function stays in JavaScript; the native side gets the button's id. It gets
 * the navigation of its screen, so the options need no import: `onPress: ({ navigation }) => navigation.openMenu('left')`.
 */
export type ButtonPressHandler = (event: ButtonPressEvent) => void;

/** An item of a button menu, with what picking it does. */
export type NavigationMenuItem = OptionsTopBarButtonMenuItem & { onPress?: ButtonPressHandler };

/** A submenu of a button menu. */
export type NavigationSubmenu = Omit<OptionsTopBarButtonSubmenu, 'items'> & {
  items: (NavigationMenuItem | NavigationSubmenu)[];
};

/** The menu of a button, whose items take `onPress`. */
export type NavigationButtonMenu = Omit<OptionsTopBarButtonMenu, 'items'> & {
  items: (NavigationMenuItem | NavigationSubmenu)[];
};

/** The floating action button, and each of its `actions`, with what its press does. */
export type NavigationFab = Omit<OptionsFab, 'actions'> & {
  onPress?: ButtonPressHandler;
  actions?: NavigationFab[];
};

/** A button of the top bar or the toolbar, with what its press does. */
export type NavigationButton = Omit<OptionsTopBarButton, 'menu'> & {
  onPress?: ButtonPressHandler;
  menu?: NavigationButtonMenu;
};
