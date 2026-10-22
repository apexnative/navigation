// The animations of the commands that change the hierarchy (`animations`): push, pop, setStackRoot, setRoot, showModal
// and dismissModal. The native side reads them in one shape only: every side of a transition (`enter`, `exit`) is a set
// of animated properties, each with its own range, duration, delay and curve.

/**
 * How a value gets from its start to its end over the time of a property: a curve `type` and the parameters that
 * curve takes. Both platforms draw the same curves. Without it, or with an unknown `type`, the value moves linearly.
 * The last frame always sets the end value exactly, whatever the curve does on the way.
 */
export type Interpolation =
  | {
      /** Constant speed. */
      type: 'linear';
    }
  | {
      /** Starts slowly and speeds up. */
      type: 'accelerate';
      /**
       * The strength of the curve: the progress is the time raised to the power of twice this number, so `1` gives the
       * square and a larger number starts slower.
       * @default 1
       */
      factor?: number;
    }
  | {
      /** Starts fast and slows down towards the end. */
      type: 'decelerate';
      /**
       * The strength of the curve: the way left is raised to the power of twice this number, so `1` gives the
       * mirror of the square and a larger number ends slower.
       * @default 1
       */
      factor?: number;
    }
  | {
      /** Slow at both ends and fast in the middle. */
      type: 'accelerateDecelerate';
    }
  | {
      /** Fast at both ends and slow in the middle. */
      type: 'decelerateAccelerate';
    }
  | {
      /** The standard curve of Material motion: a quick start and a long, soft landing. */
      type: 'fastOutSlowIn';
    }
  | {
      /** Runs past the end value and comes back to it. */
      type: 'overshoot';
      /**
       * How far the value runs past its end; `0` does not overshoot.
       * @default 1
       */
      tension?: number;
    }
  | {
      /**
       * A mass on a spring with damping, played in real time: the `duration` of the property is the time the spring
       * has to settle, and a spring that is not settled by then jumps to the end value on the last frame. With the
       * default values the spring needs about 0.8 s, so a short duration cuts it off. A `mass`, `damping` or `stiffness`
       * of 0 or less counts as its default.
       */
      type: 'spring';
      /**
       * The mass.
       * @default 3
       */
      mass?: number;
      /**
       * The damping of the spring.
       * @default 500
       */
      damping?: number;
      /**
       * The stiffness of the spring.
       * @default 200
       */
      stiffness?: number;
      /**
       * Whether a spring damped more than critically is allowed to creep. Without it, such a spring is treated as
       * critically damped and reaches the end as fast as it can without swinging.
       * @default false
       */
      allowsOverdamping?: boolean;
      /**
       * The speed the spring starts with, as a share of the way per second.
       * @default 0
       */
      initialVelocity?: number;
    };

/**
 * The animation of one property of a view: its start, its end, how long it takes and with which curve. A number that
 * is not finite is ignored, a `duration` or `startDelay` below 0 or not a number counts as not set. A property with
 * neither `from` nor `to` does not animate. While `startDelay` runs the property holds its start value; when it is
 * done it holds its end value while other properties still run.
 */
export interface OptionsAnimationPropertyConfig {
  /**
   * The value the property starts from. Without it, the view's own value: for `x` and `y` where the view stands, for
   * `alpha` what it was before the transition, for a translation `0`, for a scale `1`, for a rotation `0`.
   */
  from?: number;
  /**
   * The value the property ends at. Without it, the view's own value, as for `from`. The last frame sets it exactly.
   */
  to?: number;
  /**
   * The time the property takes, in milliseconds. The transition lasts until the latest `startDelay` plus `duration`
   * of everything that animates.
   * @default 300
   */
  duration?: number;
  /**
   * The time to wait before the property starts, in milliseconds. It counts into the length of the transition.
   * @default 0
   */
  startDelay?: number;
  /** The curve of the property. Without it, the property moves linearly. */
  interpolation?: Interpolation;
}

/**
 * The animated properties of a view. Each one is an {@link OptionsAnimationPropertyConfig}. Properties are independent
 * of each other: scaling and rotating happen around the center of the view, and a translation is neither scaled nor
 * rotated with it. The one exception is a position with a translation along the same axis, `x` with `translationX` and
 * `y` with `translationY`: they move the same thing, so set only one of them on a side. With both, the translation
 * plays and the position doesn't. Distances are in points on iOS and dp on Android.
 */
export interface ViewAnimationOptions {
  /**
   * The position of the view in its parent. `{ to: 300 }` moves the view to 300, `{ from: 0 }` brings it from 0 to
   * where it stands.
   */
  x?: OptionsAnimationPropertyConfig;
  /** The position of the view in its parent, vertically; like `x`. */
  y?: OptionsAnimationPropertyConfig;
  /** The shift of the view from its place, horizontally. A missing end is `0`. */
  translationX?: OptionsAnimationPropertyConfig;
  /** The shift of the view from its place, vertically. A missing end is `0`. */
  translationY?: OptionsAnimationPropertyConfig;
  /** The opacity of the view, from 0 to 1. A missing end is the opacity the view had before the transition. */
  alpha?: OptionsAnimationPropertyConfig;
  /** The scale of the view horizontally, around its center. A missing end is `1`. */
  scaleX?: OptionsAnimationPropertyConfig;
  /** The scale of the view vertically, around its center. A missing end is `1`. */
  scaleY?: OptionsAnimationPropertyConfig;
  /** The tilt of the view around the horizontal axis, in degrees, drawn with perspective. A missing end is `0`. */
  rotationX?: OptionsAnimationPropertyConfig;
  /** The tilt of the view around the vertical axis, in degrees, drawn with perspective. A missing end is `0`. */
  rotationY?: OptionsAnimationPropertyConfig;
  /** The turn of the view in the plane of the screen, clockwise, in degrees. A missing end is `0`. */
  rotation?: OptionsAnimationPropertyConfig;
}

/**
 * One side of the transition of a screen: the screen that comes (`enter`) or the one that goes (`exit`). It animates
 * the view of the screen. A property with a `from` or a `to` makes the animation the library's own; the side without
 * one stands still. Properties set in a layer above replace the set of the layers below, while `enabled` and
 * `waitForRender` merge key by key.
 */
export interface ScreenAnimationOptions extends ViewAnimationOptions {
  /**
   * Whether the side plays. `false` turns the side off, also when its properties come from the default options; on
   * `showModal.enter`, `dismissModal.exit` and `setRoot.enter` it turns off the whole animation of the command. A
   * sheet on Android keeps the Material sheet's own motion: it has no way to show or close at once.
   * @default true
   */
  enabled?: boolean;
  /**
   * Whether the transition waits for the screen to be drawn. Read on `setRoot.enter` and `showModal.enter`: without it
   * the transition waits for the first mount for half a second at most, `true` waits as long as it takes, `false`
   * starts at once, also with an animation of its own. Other sides don't read it.
   */
  waitForRender?: boolean;
}

/**
 * One side of the animation of the top bar, played when the bar shows or hides during a transition. (Android) iOS
 * moves the bar together with the transition by itself and ignores it. The `waitForRender` of a bar is not read.
 */
export interface TopBarAnimationOptions extends ViewAnimationOptions {
  /** Whether the bar shows or hides with this side's animation; `false` shows or hides it without animation. */
  enabled?: boolean;
}

/**
 * One side of the animation of the bottom tabs, played when the tab bar shows or hides during a transition.
 * (Android) iOS moves the tab bar together with the transition by itself and ignores it.
 */
export interface BottomTabsAnimationOptions extends ViewAnimationOptions {
  /** Whether the tab bar shows or hides with this side's animation; `false` shows or hides it without animation. */
  enabled?: boolean;
}

/**
 * The animations of the two sides of a transition: `enter` for what comes, `exit` for what goes. A side left out
 * stays still; one that is set without the other does not move the other.
 */
export interface EnterExitAnimationOptions<Side = ScreenAnimationOptions> {
  /** The animation of the side that comes: the new screen, the modal, the new root. */
  enter?: Side;
  /** The animation of the side that goes: the screen that is covered or left, the modal, the old root. */
  exit?: Side;
}

/**
 * The animation of a property of an element that comes into its place. It starts from `from` and ends at the value
 * the element has on its screen. `duration` is required; without a `startDelay` it starts at once.
 */
export interface AppearingElementAnimation {
  /** The start value. The element holds it while `startDelay` runs. */
  from: number;
  /** The end value. Leave it out to end at the element's own value. */
  to?: number;
  /** The time the property takes, in milliseconds. */
  duration: number;
  /**
   * The time to wait before the property starts, in milliseconds.
   * @default 0
   */
  startDelay?: number;
  /** The curve of the property. Without it, the property moves linearly. */
  interpolation?: Interpolation;
}

/**
 * The animation of a property of an element that leaves. It starts from the value the element has on its screen and
 * ends at `to`. When the transition ends or is cancelled, the element gets its own value back, so an element that left
 * with `alpha: { to: 0 }` is visible again when its screen comes back. `duration` is required.
 */
export interface DisappearingElementAnimation {
  /** The start value. Leave it out to start from the element's own value. */
  from?: number;
  /** The end value. */
  to: number;
  /** The time the property takes, in milliseconds. */
  duration: number;
  /**
   * The time to wait before the property starts, in milliseconds. The element holds its start value meanwhile.
   * @default 0
   */
  startDelay?: number;
  /** The curve of the property. Without it, the property moves linearly. */
  interpolation?: Interpolation;
}

/**
 * The animation of one property of an element: appearing (with a `from`) or disappearing (with a `to`). Giving both
 * ends is allowed. A property with neither does not animate.
 */
export type ElementAnimation = AppearingElementAnimation | DisappearingElementAnimation;

/**
 * An animation of one view of a screen that moves together with the transition, with or without a counterpart on the
 * other screen. The view is found by its `nativeID`, first on the screen that goes, then on the one that comes; one
 * that is not found is skipped while the rest plays. The transition waits until it is found. An element that fails on
 * the way (an error in its animation) is skipped too, with a warning in the log: it shows and hides with its screen,
 * and the transition and its command go on. Without an animation of the screens of its own, the screen that comes fades
 * in (or the one that goes fades out) in 300 ms around the elements. When the transition ends or is cancelled, the
 * element gets its own values back. `x` and `y` are the position in the parent of the element on both platforms. As on
 * a side of a screen, don't set `x` with `translationX` or `y` with `translationY` on one element: the translation
 * plays and the position doesn't. (Android) A view whose parent is a React view with `removeClippedSubviews` turned on,
 * such as a child of a clipping list, is not moved: it counts as not found.
 */
export interface ElementTransition {
  /** The `nativeID` of the view to animate. */
  id: string;
  /** The opacity of the element. */
  alpha?: ElementAnimation;
  /** The position of the element in its parent; a missing end is where the element stands. */
  x?: ElementAnimation;
  /** The position of the element in its parent, vertically; like `x`. */
  y?: ElementAnimation;
  /** The shift of the element from its place, horizontally. */
  translationX?: ElementAnimation;
  /** The shift of the element from its place, vertically. */
  translationY?: ElementAnimation;
  /** The scale of the element horizontally, around its center. */
  scaleX?: ElementAnimation;
  /** The scale of the element vertically, around its center. */
  scaleY?: ElementAnimation;
  /** The tilt of the element around the horizontal axis, in degrees, drawn with perspective. */
  rotationX?: ElementAnimation;
  /** The tilt of the element around the vertical axis, in degrees, drawn with perspective. */
  rotationY?: ElementAnimation;
  /** The turn of the element in the plane of the screen, clockwise, in degrees. */
  rotation?: ElementAnimation;
}

/**
 * A view that flies from the screen that goes to the screen that comes, changing its place, size, corners and look on
 * the way. A pair whose view is not found on either screen is skipped while the rest plays, and the transition waits
 * until the views are found. A pair that fails on the way (an error in its start or its flight) is skipped too, with a
 * warning in the log: its views show and hide with their screens, and the transition and its command go on. (iOS) So is
 * a pair with a view without a size, or one whose picture can't be taken. Without an animation of the screens of its
 * own, the screen that comes fades in (or the one that goes fades out) in 300 ms around the pair. Text keeps its
 * proportions and changes its picture within a quarter of the flight. (Android) A picture that doesn't load within a
 * second drops out of the transition, and so does a pair with a view whose parent is a React view with
 * `removeClippedSubviews` turned on: it counts as not found.
 */
export interface SharedElementTransition {
  /** The `nativeID` of the view on the screen that goes. */
  fromId: string;
  /** The `nativeID` of the view on the screen that comes. */
  toId: string;
  /**
   * The time of the flight, in milliseconds. Without it, 300 ms on iOS; on Android the length of the fade of the
   * screen in this transition, 300 ms unless set.
   */
  duration?: number;
  /**
   * The time to wait before the flight, in milliseconds.
   * @default 0
   */
  startDelay?: number;
  /**
   * The curve of the whole flight (place, size, corners, look). A spring runs in real time over the `duration`. Without
   * it, the flight is linear.
   */
  interpolation?: Interpolation;
}

/**
 * (iOS 18+) The system zoom transition: the new screen or modal grows out of a view of the screen it comes from, and
 * shrinks back into it when it goes, also under the finger of a swipe. An animation of the screens of your own or a list
 * of elements takes precedence over the zoom. Without the view, or before iOS 18, the transition is the usual one.
 * Android doesn't zoom.
 */
export interface ZoomTransitionOptions {
  /**
   * The `nativeID` of the view the screen grows out of, looked for on the screen that is shown when the command runs.
   * An empty string or `null` turns the zoom off.
   */
  fromId: string | null;
  /**
   * Whether the zoom is on. `fromId` alone turns it on; `false` gives the usual transition.
   * @default true
   */
  enabled?: boolean;
}

/**
 * The animations of a command that changes a stack: `push`, `pop` (also for `popTo` and `popToRoot`) and
 * `setStackRoot`. Without them, the transition is the platform's own: UIKit's on iOS, the transition between activities
 * on Android.
 */
export interface StackAnimationOptions {
  /**
   * Whether the command is animated; `false` shows or removes the screens at once, at the moment an animated one would
   * have started. On `pop` it is read from the screen that goes.
   * @default true
   */
  enabled?: boolean;
  /**
   * Whether to wait for the new top screen to be drawn before the transition. Without it, the transition waits for the
   * content of a screen with an animation of its own for half a second at most, `true` waits as long as it takes,
   * `false` starts at once, also with an animation of its own.
   */
  waitForRender?: boolean;
  /**
   * The animation of the screens: `enter` for the screen that comes (on a pop, the one that was below), `exit` for the
   * one that goes. A property with a `from` or a `to` makes it an animation of its own, which replaces the platform's
   * transition; the side left out stays still. (Android) With one of its own, the title and buttons of the bar and the
   * tab bar don't travel with the screens. (Android) When the transition has elements, a push plays only `enter` and a
   * pop only `exit`, and only when that side is a single `alpha`; any other side is not played, and the screen fades in
   * or out in 300 ms around the elements instead. iOS plays the sides as they are set.
   */
  content?: EnterExitAnimationOptions;
  /**
   * (Android) The animation of the top bar when it shows or hides because the screens differ in `topBar.visible`.
   * Without it, the bar slides up by its height and back in 300 ms. iOS animates the bar with UIKit.
   */
  topBar?: EnterExitAnimationOptions<TopBarAnimationOptions>;
  /**
   * (Android) The animation of the tab bar when it shows or hides because the screens differ in their tabs. Without
   * it, the bar travels with the screens in the platform's transition, or slides down by its height and back in 300
   * ms. iOS animates the bar with UIKit.
   */
  bottomTabs?: EnterExitAnimationOptions<BottomTabsAnimationOptions>;
  /**
   * The views that fly from the screen that goes to the screen that comes. A list that is not empty makes the
   * animation the library's own; `[]` removes the list the layers below give and means no elements. On a pop, set the
   * pairs the other way round.
   */
  sharedElementTransitions?: SharedElementTransition[];
  /**
   * The animations of single views of the screens, with or without shared elements. A list that is not empty makes the
   * animation the library's own; `[]` removes the list the layers below give.
   */
  elementTransitions?: ElementTransition[];
}

/**
 * The animation of a push, read from the screen that comes. As {@link StackAnimationOptions}, with the zoom of iOS.
 */
export interface StackPushAnimationOptions extends StackAnimationOptions {
  /** (iOS 18+) The zoom of the new screen out of a view of the screen it is pushed from; the pop zooms back. */
  zoom?: ZoomTransitionOptions;
}

/**
 * The animation of `setStackRoot`, read from the new top screen. As {@link StackAnimationOptions}: without an animation
 * of its own, iOS plays the push animation of the new top screen or UIKit's transition, and Android plays a fade
 * through. With `enabled: false` the screens are set at once; (Android) the command doesn't wait for the new screens to
 * be drawn first.
 */
export interface SetStackRootAnimationOptions extends StackAnimationOptions {
  /**
   * Whether the stack root comes in with the push animation (Android), as `replace` does instead of the fade through.
   * Set by `replace`; apps don't set it. iOS ignores it.
   * @internal
   */
  asPush?: boolean;
}

/**
 * The animation of a modal as it shows (`showModal`) or is dismissed (`dismissModal`, `dismissAllModals`). On show,
 * `enter` animates the modal and `exit` the screen under it; on dismiss, `exit` animates the modal and `enter` the
 * screen under it. The screen under a modal takes part (iOS) only under a modal that covers the full screen.
 *
 * A sheet keeps the system's sheet motion on both platforms: (iOS) a modal shown as `'pageSheet'`, `'formSheet'` or
 * without a style, (Android) a Material bottom sheet. The sides `enter` and `exit` and the lists of elements set here
 * don't apply to it.
 *
 * (Android) When the modal has elements, `enter` of a show and `exit` of a dismiss play only when that side is a single
 * `alpha`; any other side is not played, and the modal fades in or out in 300 ms around the elements instead.
 */
export interface ModalAnimationOptions extends EnterExitAnimationOptions {
  /**
   * The views that fly from the screen under the modal to the modal. On dismiss, the pairs run the other way round.
   * `[]` removes the list the layers below give.
   */
  sharedElementTransitions?: SharedElementTransition[];
  /** The animations of single views of the screens. `[]` removes the list the layers below give. */
  elementTransitions?: ElementTransition[];
  /**
   * (iOS 18+) The zoom of the modal out of a view of the screen that shows it, and back into it on dismiss. Read on
   * `showModal` only. An animation of the sides or elements takes precedence over it.
   */
  zoom?: ZoomTransitionOptions;
}

/**
 * The transitions of the commands that change the hierarchy. Each key is read at the moment of its command, from the
 * options of the screen the command acts on, merged with the options of its containers, the default options and the
 * options of the command. A change by `mergeOptions` is for the next command and animates nothing itself. A theme
 * switch or a rotation does not change an animation. Without a key, the transition is the platform's own.
 *
 * ```ts
 * const options: Options = {
 *   animations: {
 *     setRoot: {
 *       enter: { alpha: { from: 0, to: 1, duration: 400, interpolation: { type: 'decelerate' } } },
 *     },
 *   },
 * };
 * ```
 */
export interface AnimationOptions {
  /** The push of a screen, read from the screen that comes. */
  push?: StackPushAnimationOptions;
  /** The pop of a screen, also `popTo` and `popToRoot`, read from the screen that goes. A swipe back is always the system's. */
  pop?: StackAnimationOptions;
  /** The replacing of the screens of a stack, read from the new top screen. */
  setStackRoot?: SetStackRootAnimationOptions;
  /**
   * The new root of the app, from `setRoot` or `switchTo`: `enter` animates the new root, `exit` the old one (on iOS a
   * snapshot of the old window). Without it, iOS dissolves the old root in 0.3 s and Android plays a fade through.
   */
  setRoot?: EnterExitAnimationOptions;
  /**
   * The showing of a modal, read from the modal with its containers. A sheet keeps the system's sheet motion: (iOS)
   * `'pageSheet'`, `'formSheet'` and a modal without a style, (Android) the bottom sheet. The `enter`, `exit` and
   * elements set here don't apply to it.
   */
  showModal?: ModalAnimationOptions;
  /**
   * The dismissing of a modal, also `dismissAllModals` (from the top modal), read from the modal that goes. A sheet
   * keeps the system's sheet motion: (iOS) `'pageSheet'`, `'formSheet'` and a modal without a style, (Android) the
   * bottom sheet. The `enter`, `exit` and elements set here don't apply to it.
   */
  dismissModal?: ModalAnimationOptions;
}
