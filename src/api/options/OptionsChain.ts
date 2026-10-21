import { Platform } from 'react-native';
import type {
  AnimationOptions,
  Color,
  HardwareBackButtonOptions,
  LayoutOrientation,
  NavigationBarOptions,
  Options,
  OptionsBottomTabs,
  OptionsLayout,
  OptionsScrollEdgeEffect,
  OptionsSideMenu,
  OptionsStatusBar,
  OptionsTopBar,
  OptionsTopBarBackButton,
  OptionsTopBarLargeTitle,
  SafeAreaEdge,
} from '../../interfaces/Options';
import { mergeOptions } from '../../engine/mergeOptions';

/**
 * The modifiers of the options of a screen. Each modifier returns a copy with the options merged over the ones before,
 * so the last one wins; `options()` takes anything the modifiers don't cover.
 */
export abstract class OptionsChain {
  constructor(protected readonly built: Options = {}) {}

  /** A copy with other options. */
  protected abstract withOptions(options: Options): this;

  /** Any options. */
  options(options: Options): this {
    return this.withOptions(mergeOptions(this.built, options));
  }

  // Top bar

  topBar(topBar: OptionsTopBar): this {
    return this.options({ topBar });
  }

  /** #### (iOS specific) */
  largeTitle(largeTitle: boolean | OptionsTopBarLargeTitle = true): this {
    return this.topBar({
      largeTitle: typeof largeTitle === 'boolean' ? { visible: largeTitle } : largeTitle,
    });
  }

  backButton(backButton: OptionsTopBarBackButton): this {
    return this.topBar({ backButton });
  }

  /** Hides the top bar; `hideTopBar(fullScreen)` takes the value from state, `false` shows it. */
  hideTopBar(hidden = true): this {
    return this.topBar({ visible: !hidden });
  }

  /** Hides the tab bar under this screen; `false` shows it. */
  hideBottomTabs(hidden = true): this {
    return this.bottomTabs({ visible: !hidden });
  }

  // Screen and system

  layout(layout: OptionsLayout): this {
    return this.options({ layout });
  }

  /**
   * The edges on which the screen keeps its content out of its safe area (bars, status bar, home indicator, cutout);
   * none with no edge. Without it: all four on Android, and on iOS every edge but those where the content goes under
   * a bar. On iOS a large title leaves the top out by itself.
   */
  safeArea(...edges: SafeAreaEdge[]): this {
    return this.layout({ safeAreaEdges: edges });
  }

  backgroundColor(backgroundColor: Color): this {
    return this.layout({ backgroundColor });
  }

  orientation(...orientation: LayoutOrientation[]): this {
    return this.layout({ orientation });
  }

  /** `false`: the bars of the screen stay horizontal on iPhone Duo. #### (iOS specific) */
  verticalBars(verticalBars = true): this {
    return this.layout({ verticalBars });
  }

  statusBar(statusBar: OptionsStatusBar): this {
    return this.options({ statusBar });
  }

  navigationBar(navigationBar: NavigationBarOptions): this {
    return this.options({ navigationBar });
  }

  /** #### (iOS specific) */
  scrollEdgeEffect(scrollEdgeEffect: OptionsScrollEdgeEffect): this {
    return this.options({ scrollEdgeEffect });
  }

  // Behavior

  /** #### (iOS specific) */
  popGesture(popGesture: boolean | 'edge'): this {
    return this.options({ popGesture });
  }

  hardwareBackButton(hardwareBackButton: HardwareBackButtonOptions): this {
    return this.options({ hardwareBackButton });
  }

  // Transitions

  animations(animations: Pick<AnimationOptions, 'push' | 'pop'>): this {
    return this.options({ animations });
  }

  // The containers, while a screen with these options is on top

  bottomTabs(bottomTabs: OptionsBottomTabs): this {
    return this.options({ bottomTabs });
  }

  sideMenu(sideMenu: OptionsSideMenu): this {
    return this.options({ sideMenu });
  }

  // Platforms

  /** Modifiers for iOS only. */
  ios(build: (options: this) => this): this {
    return Platform.OS === 'ios' ? build(this) : this;
  }

  /** Modifiers for Android only. */
  android(build: (options: this) => this): this {
    return Platform.OS === 'android' ? build(this) : this;
  }
}
