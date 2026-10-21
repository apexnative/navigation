import type {
  Options,
  OptionsKeyCommand,
  OptionsSearchBar,
  OptionsToolbar,
  OptionsToolbarSearchField,
  OptionsToolbarSpace,
  OptionsTopBarSubtitle,
  OptionsTopBarTitle,
} from '../../interfaces/Options';
import type { NavigationButton, NavigationFab } from './buttons';
import { OptionsChain } from './OptionsChain';

/** The toolbar, with buttons that take `onPress`. */
export type ToolbarOptions = Omit<OptionsToolbar, 'buttons'> & {
  buttons?: (NavigationButton | OptionsToolbarSpace | OptionsToolbarSearchField)[];
};

/**
 * The options of a screen as a chain: the modifiers of `OptionsChain` and those that belong to one screen only, such
 * as its title and buttons.
 */
export class ScreenOptions extends OptionsChain {
  protected withOptions(options: Options): this {
    return new ScreenOptions(options) as this;
  }

  /** The options built by a chain. */
  static read(options: ScreenOptions): Options {
    return options.built;
  }

  title(title: string | OptionsTopBarTitle): this {
    return this.topBar({
      title: typeof title === 'string' ? { text: title } : title,
    });
  }

  subtitle(subtitle: string | OptionsTopBarSubtitle): this {
    return this.topBar({
      subtitle: typeof subtitle === 'string' ? { text: subtitle } : subtitle,
    });
  }

  /** Buttons take `onPress`. */
  leftButtons(...leftButtons: NavigationButton[]): this {
    return this.topBar({ leftButtons });
  }

  /** Buttons take `onPress`. */
  rightButtons(...rightButtons: NavigationButton[]): this {
    return this.topBar({ rightButtons });
  }

  /** #### (iOS specific) Buttons take `onPress`. */
  centerButtons(...centerButtons: NavigationButton[]): this {
    return this.topBar({ centerButtons });
  }

  searchBar(searchBar: OptionsSearchBar): this {
    return this.topBar({ searchBar });
  }

  /** #### (iOS specific) Buttons take `onPress`. */
  toolbar(toolbar: ToolbarOptions): this {
    return this.options({ toolbar });
  }

  /** #### (Android specific) The FAB and its `actions` take `onPress`. */
  fab(fab: NavigationFab): this {
    return this.options({ fab: fab as Options['fab'] });
  }

  keyCommands(...keyCommands: OptionsKeyCommand[]): this {
    return this.options({ keyCommands });
  }
}

export function readScreenOptions(options: ScreenOptions): Options {
  return ScreenOptions.read(options);
}
