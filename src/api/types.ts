import type { ComponentType } from 'react';
import type { NavigationApp } from './app/createNavigation';
import type { RootsMap } from './app/roots';
import type { Arrangement } from './screens/arrangement';
import type { ScreenDefinition } from './screens/defineScreen';
import type { NativeScreen } from './screens/nativeScreens';
import type { ScreenEntry } from './screens/registerScreens';

/**
 * The app's navigation, for the types of its names and params. An app registers it once:
 * `declare module '@apexnative/navigation' { interface Register { app: typeof App } }`.
 */
export interface Register {}

/** The roots of the registered app; without the registration any name goes. */
export type RegisteredRoots = Register extends { app: NavigationApp<infer Roots, any> }
  ? Roots
  : RootsMap;

/** The screens of the registered app, those of its groups too; without the registration any name goes. */
export type RegisteredScreens = Register extends { app: NavigationApp<any, infer Screens> }
  ? Screens
  : Record<string, ScreenEntry>;

export type ScreenName = keyof RegisteredScreens & string;

/**
 * The type of the app's context, for `useAppContext`, `navigation.getContext` and the options. It is registered next
 * to the app, `interface Register { app: typeof App; context: AppContext }`: read from the app it would go round,
 * since the screens' options are part of the app's type.
 */
export type RegisteredContext = Register extends { context: infer Context } ? Context : unknown;

/** The props a screen takes: what a command gives it. */
export type ScreenProps<Name extends ScreenName> = PropsOf<RegisteredScreens[Name]>;

/** The settings of a command with the props of the screen, which are required when the screen has required ones. */
export type ShowArgs<Name extends ScreenName, Config> =
  Partial<ScreenProps<Name>> extends ScreenProps<Name>
    ? [config?: Config & { props?: ScreenProps<Name> }]
    : [config: Config & { props: ScreenProps<Name> }];

/**
 * What is wrong with a screen a layout shows, by the screens of the app: a message, or never when it fits. A screen
 * named alone needs all its props to be optional; an untyped element is not checked.
 */
type ScreenUseError<Use, AppScreens> = 0 extends 1 & Use
  ? never
  : Use extends { name: infer Name extends string; props: infer Props }
    ? string extends Name
      ? 'a screen goes by its name in screens, not by any string'
      : Name extends keyof AppScreens
        ? [Props] extends [undefined]
          ? Partial<PropsOf<AppScreens[Name]>> extends PropsOf<AppScreens[Name]>
            ? never
            : `'${Name}' takes props: Screen('${Name}', props)`
          : Props extends AsWritten<PropsOf<AppScreens[Name]>>
            ? never
            : `the props of '${Name}' are not those its component takes`
        : `there is no screen '${Name}' in screens`
    : never;

// The props of a layout are inferred as written (`const`): readonly, with their arrays readonly too.
type AsWritten<Value> = Value extends (...args: any[]) => unknown
  ? Value
  : Value extends readonly (infer Item)[]
    ? readonly AsWritten<Item>[]
    : Value extends object
      ? { readonly [Key in keyof Value]: AsWritten<Value[Key]> }
      : Value;

/**
 * A layout whose screens are screens of the app with their props: unknown. Otherwise a property named by what is
 * wrong, which no layout has, so the type error says why.
 */
export type CheckedScreens<Uses, AppScreens> = [ScreenUseError<Uses, AppScreens>] extends [never]
  ? unknown
  : { readonly [Message in `[navigation] ${ScreenUseError<Uses, AppScreens>}`]: never };

type PropsOf<Entry> =
  Entry extends Arrangement<infer Props>
    ? Props
    : Entry extends NativeScreen<infer Props>
      ? Props
      : Entry extends ScreenDefinition<infer Props>
        ? Props
        : Entry extends ComponentType<infer Props>
          ? Props
          : Entry extends { load: () => infer Module }
            ? PropsOfModule<Awaited<Module>>
            : object;

type PropsOfModule<Module> = Module extends { default: infer Default }
  ? PropsOf<Default>
  : PropsOf<Module>;
