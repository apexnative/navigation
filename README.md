<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/apexnative/navigation/main/.github/assets/navigation-banner-dark.png">
  <img alt="@apexnative/navigation: native navigation for React Native on iOS, iPadOS and Android" src="https://raw.githubusercontent.com/apexnative/navigation/main/.github/assets/navigation-banner-light.png">
</picture>

<p align="center">
  <a href="https://www.npmjs.com/package/@apexnative/navigation"><img alt="npm version" src="https://img.shields.io/npm/v/@apexnative/navigation?label=npm&color=cb3837"></a>
  <a href="https://www.npmjs.com/package/@apexnative/navigation"><img alt="npm downloads" src="https://img.shields.io/npm/dm/@apexnative/navigation"></a>
  <a href="https://github.com/apexnative/navigation/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/apexnative/navigation/ci.yml?branch=main&label=CI"></a>
  <img alt="Platforms: iOS, iPadOS, Android" src="https://img.shields.io/badge/platforms-iOS%20%7C%20iPadOS%20%7C%20Android-informational">
  <img alt="React Native 0.85 or newer, New Architecture" src="https://img.shields.io/badge/React%20Native-0.85%2B%20%C2%B7%20New%20Architecture-61dafb">
  <img alt="TypeScript types included" src="https://img.shields.io/npm/types/@apexnative/navigation">
  <a href="https://github.com/apexnative/navigation/blob/main/LICENSE"><img alt="License: MIT" src="https://img.shields.io/npm/l/@apexnative/navigation"></a>
</p>

<p align="center">
  <a href="https://navigation.apexnative.dev/docs/start/introduction"><b>Documentation</b></a> ·
  <a href="https://navigation.apexnative.dev/docs/start/quick-start">Quick start</a> ·
  <a href="https://navigation.apexnative.dev/api/starting-the-app/createNavigation">API reference</a> ·
  <a href="https://github.com/apexnative/navigation/releases">Releases</a>
</p>

# @apexnative/navigation

**The platform owns the navigation. React describes it.**

`@apexnative/navigation` gives a React Native app the navigation of the platform itself. Stacks, tabs, modals, side
menus, split views and overlays are the real containers of iOS and Android: `UINavigationController`,
`UITabBarController`, sheets with detents, `UISplitViewController`, the Android back stack and the Material components.
You describe the screens and how they are arranged in TypeScript; the native side shows them and reports back.

> **Status:** a pre-release on npm; the API can still change before 1.0. The current version is on the
> [releases page](https://github.com/apexnative/navigation/releases).

## Why

**The platform already knows how to navigate.** iOS and Android hold years of decisions about gestures,
accessibility, every screen size, memory and multitasking. A router that keeps its own model of the navigation has to
mirror all of that in JavaScript and catch up with every OS release. This library gives React the platform's own
containers instead of replacing them, so a new tab bar or toolbar of the OS arrives with its beta, not a year later.

**Native behavior, not only a native look.** The swipe back, the back button and dragging a sheet are the system's
own, with the same transitions, accessibility and memory use as in an app written in Swift or Kotlin. Nothing is
replayed into a copy of the state in React: JavaScript reads the state of the platform through events and hooks.

**Describe, don't command.** Native navigation usually speaks in JSON layouts and string ids, so a wrong screen name
or a missing prop shows up at run time. Here screens, props and roots are typed, and the same mistake is an error in
the editor.

|                        | A router in the React tree                    | Native navigation by commands | This library                                      |
| ---------------------- | --------------------------------------------- | ----------------------------- | ------------------------------------------------- |
| **Who owns the state** | an object in the React tree                   | the OS                        | the OS                                            |
| **Gestures**           | native or drawn in JS, synced back into React | the system's own              | the system's own                                  |
| **How you navigate**   | navigator components or files as URLs         | JSON layouts and screen ids   | `Stack`, `Tabs`, `SideMenu`; commands without ids |
| **Types**              | typed route params                            | strings, props unchecked      | names, props and roots checked                    |

## A quick look

A screen is a component with its options:

```tsx
import { Text } from 'react-native';
import { defineScreen } from '@apexnative/navigation';

function Details({ title }: { title?: string }) {
  return <Text>{title ?? 'Details'}</Text>;
}

export const DetailsScreen = defineScreen({
  component: Details,
  options: (s, props) => s.title(props.title ?? 'Details'),
});
```

The navigation names the screens and the root:

```ts
import { createNavigation } from '@apexnative/navigation';

export const Navigation = createNavigation({
  screens: { Home: HomeScreen, Details: DetailsScreen },
  root: 'Home',
});
```

Any screen opens another one with the platform's own transition:

```ts
const navigation = useNavigation();
navigation.push('Details', { props: { title: 'Order 42' } });
// navigation.push('Detail') or a prop Details doesn't take: a TypeScript error
```

## What's in it

- **Containers:** native stacks, tabs, modals and sheets, side menus, split views on iPad, and overlays, nested in
  each other: tabs of stacks, a stack in a modal, tabs in a side menu.
- **Screen UI as options:** the top bar, bar buttons, search, the toolbar and the floating action button, the status
  bar and the system bars.
- **No safe area code:** every screen keeps its content clear of the bars, the cutout and the home indicator by itself.
- **Native screens:** SwiftUI, UIKit and Compose screens are pushed, presented and put in tabs the same way as React
  screens.
- **One React tree when you want it:** each screen is its own React root by default; with `appProvider` all screens
  share one tree.
- **The app's lifecycle:** cold start, the splash screen, deep links, notifications and switching roots on sign-in
  and sign-out.
- **A CLI**, `apex-navigation`: sets up an app, checks it (`doctor`), adds screens, deep links, icons and the splash
  screen, upgrades the navigation, migrates an app from another navigation library, and serves the commands and the
  docs to AI coding agents (`mcp`).

## When it fits

- Apps that should look and behave like the platform, on iPhone, iPad and Android.
- Apps with native screens, or a team that also writes Swift or Kotlin.
- Apps where startup, transitions and memory matter.

It doesn't fit an app that also ships to the web, or one that draws the same fully custom UI on every platform in
JavaScript.

## Requirements

|                |                                                               |
| -------------- | ------------------------------------------------------------- |
| React Native   | 0.85 or newer, with the New Architecture (bridgeless, Fabric) |
| iOS and iPadOS | 15.1 or newer                                                 |
| Android        | 7.0 or newer (minSdk 24)                                      |
| Expo           | not supported by `setup` yet                                  |
| Web            | not a target                                                  |

## Installation

```sh
npm install @apexnative/navigation
npx apex-navigation setup
```

`setup` shows what it changes in the native files and in `index.js`, asks before writing, and installs the pods on a
Mac. A new app starts with `npx @apexnative/navigation init MyApp`, and `npx apex-navigation doctor` checks an app
and the machine and fixes what it can. The [installation guide](https://navigation.apexnative.dev/docs/start/installation)
has the details, and the [quick start](https://navigation.apexnative.dev/docs/start/quick-start) builds a first app
with two screens.

Coming from another library: [React Navigation](https://navigation.apexnative.dev/docs/migration/from-react-navigation),
[Expo Router](https://navigation.apexnative.dev/docs/migration/from-expo-router),
[react-native-navigation](https://navigation.apexnative.dev/docs/migration/from-react-native-navigation).

## Principles

1. **The platform leads.** Native stacks, tabs, sheets and gestures, not a web-style router. A URL is one entry point
   among push notifications, widgets and shortcuts.
2. **Native is the source of truth.** Navigation state, screen lifecycle, safe areas, keyboard and theme come from the
   OS.
3. **App lifecycle is first-class.** Cold start, splash, session restore, background and process death are explicit
   steps.
4. **New OS features, fast.** Support for new iOS and Android releases lands with the betas.
5. **Customization without losing native behavior.** Options of system components first, your own native components
   inside native containers next, custom JS UI last.
6. **Performance is a requirement.** Transitions and gestures run natively; JavaScript stays off the critical path.
7. **Rendering and logic apart.** React renders screens. The logic of the app (a socket, a session, services) lives
   outside the render tree: `navigation` works outside React, and `willMount` prepares the app before the first
   screen.

When two of them pull apart, the order decides.

## Contributing

The [Showcase app](https://github.com/apexnative/navigation/tree/main/example) runs every feature as a scenario.
See [CONTRIBUTING.md](https://github.com/apexnative/navigation/blob/main/CONTRIBUTING.md) and the
[code of conduct](https://github.com/apexnative/navigation/blob/main/CODE_OF_CONDUCT.md).

## License

[MIT](https://github.com/apexnative/navigation/blob/main/LICENSE)
