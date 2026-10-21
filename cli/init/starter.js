// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { PACKAGE } = require('../app');

/** @typedef {import('../changes').FileChange} FileChange */

/** Where the starter's screens go: where `screen` puts the next ones too. */
const SCREENS_DIR = 'src/screens';

/** The starters `init --template` makes: Home and Details in a stack, three tabs, or a sign-in before the tabs. */
const TEMPLATES = /** @type {const} */ (['stack', 'tabs', 'auth']);

/** @typedef {typeof TEMPLATES[number]} Template */

// The starter is written as the template's Prettier writes it.

// The text of the system, light or dark as the screen behind it: the template's Text is black on any background.
const TEXT_COLOR = `color:
      Platform.OS === 'ios'
        ? PlatformColor('label')
        : PlatformColor('?android:attr/textColorPrimary'),`;

const STYLES = `const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  title: {
    fontSize: 20,
    fontWeight: '600',
    ${TEXT_COLOR}
  },
});
`;

const HOME = `import React from 'react';
import {
  Button,
  Platform,
  PlatformColor,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { defineScreen, useNavigation } from '${PACKAGE}';

function Home() {
  const navigation = useNavigation();
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Home</Text>
      <Button
        title="Open Details"
        onPress={() =>
          navigation.push('Details', { props: { title: 'Details' } })
        }
      />
    </View>
  );
}

export const HomeScreen = defineScreen({
  component: Home,
  options: s => s.title('Home'),
});

${STYLES}`;

const DETAILS = `import React from 'react';
import { Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '${PACKAGE}';

/** The props a command gives the screen: \`push('Details', { props })\`. */
export interface DetailsProps {
  title?: string;
}

function Details(props: DetailsProps) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>{props.title ?? 'Details'}</Text>
    </View>
  );
}

export const DetailsScreen = defineScreen({
  component: Details,
  options: (s, props) => s.title(props.title ?? 'Details'),
});

${STYLES}`;

/**
 * A screen with its title in the middle.
 * @param {string} name
 */
function titleScreen(name) {
  return `import React from 'react';
import { Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '${PACKAGE}';

function ${name}() {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>${name}</Text>
    </View>
  );
}

export const ${name}Screen = defineScreen({
  component: ${name},
  options: s => s.title('${name}'),
});

${STYLES}`;
}

const STACK_NAVIGATION = `import { createNavigation } from '${PACKAGE}';
import { DetailsScreen } from './${SCREENS_DIR}/Details';
import { HomeScreen } from './${SCREENS_DIR}/Home';

export const Navigation = createNavigation({
  screens: { Home: HomeScreen, Details: DetailsScreen },
  root: 'Home',
});
`;

// Each tab a stack, with an SF Symbol on iOS and a drawable of the app on Android.
const TABS = `Tabs(
  Tab('Home').label('Home').icon({ ios: 'house', android: 'ic_home' }),
  Tab('Profile')
    .label('Profile')
    .icon({ ios: 'person', android: 'ic_person' }),
  Tab('Settings')
    .label('Settings')
    .icon({ ios: 'gearshape', android: 'ic_settings' }),
)`;

const TABS_NAVIGATION = `import { createNavigation, Tab, Tabs } from '${PACKAGE}';
import { DetailsScreen } from './${SCREENS_DIR}/Details';
import { HomeScreen } from './${SCREENS_DIR}/Home';
import { ProfileScreen } from './${SCREENS_DIR}/Profile';
import { SettingsScreen } from './${SCREENS_DIR}/Settings';

export const Navigation = createNavigation({
  screens: {
    Home: HomeScreen,
    Details: DetailsScreen,
    Profile: ProfileScreen,
    Settings: SettingsScreen,
  },
  root: ${TABS.split('\n').join('\n  ')},
});
`;

const AUTH_NAVIGATION = `import {
  createNavigation,
  Root,
  Stack,
  Tab,
  Tabs,
} from '${PACKAGE}';
import { DetailsScreen } from './${SCREENS_DIR}/Details';
import { HomeScreen } from './${SCREENS_DIR}/Home';
import { LoginScreen } from './${SCREENS_DIR}/Login';
import { ProfileScreen } from './${SCREENS_DIR}/Profile';
import { SettingsScreen } from './${SCREENS_DIR}/Settings';

export type User = { name: string };

/** What the whole app reads: the signed-in user, or null. */
export type AppContext = { user: User | null };

const context: AppContext = { user: null };

const main = ${TABS};

export const Navigation = createNavigation({
  screens: {
    Login: LoginScreen,
    Home: HomeScreen,
    Details: DetailsScreen,
    Profile: ProfileScreen,
    Settings: SettingsScreen,
  },
  context,
  roots: {
    signedOut: Stack('Login'),
    signedIn: Root(main).transition('fade'),
  },
  initialRoot: current => (current.user ? 'signedIn' : 'signedOut'),
});
`;

const SESSION = `import { navigation } from '${PACKAGE}';

// Your own sign-in goes here: check the name with your server, keep the
// session in your storage, and read it in willMount of createNavigation.

/** Signs in: the user goes into the context, and the app shows its tabs. */
export async function signIn(name: string) {
  navigation.setContext(context => ({ ...context, user: { name } }));
  await navigation.switchTo('signedIn');
}

/** Signs out: back to Login, then the user leaves the context. */
export async function signOut() {
  await navigation.switchTo('signedOut');
  navigation.setContext(context => ({ ...context, user: null }));
}
`;

const LOGIN = `import React, { useState } from 'react';
import {
  Button,
  Platform,
  PlatformColor,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { defineScreen } from '${PACKAGE}';
import { signIn } from '../session';

function Login() {
  const [name, setName] = useState('');
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Sign in</Text>
      <TextInput
        style={styles.input}
        placeholder="Your name"
        value={name}
        onChangeText={setName}
      />
      <Button title="Sign in" onPress={() => signIn(name || 'Guest')} />
    </View>
  );
}

export const LoginScreen = defineScreen({
  component: Login,
  options: s => s.title('Sign in'),
});

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  title: {
    fontSize: 20,
    fontWeight: '600',
    ${TEXT_COLOR}
  },
  input: {
    width: 240,
    padding: 10,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 8,
    borderColor:
      Platform.OS === 'ios'
        ? PlatformColor('separator')
        : PlatformColor('?android:attr/textColorSecondary'),
    ${TEXT_COLOR}
  },
});
`;

const PROFILE_SIGNED_IN = `import React from 'react';
import { Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useAppContext } from '${PACKAGE}';

function Profile() {
  const user = useAppContext(context => context.user);
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>{user?.name ?? 'Profile'}</Text>
    </View>
  );
}

export const ProfileScreen = defineScreen({
  component: Profile,
  options: s => s.title('Profile'),
});

${STYLES}`;

const SETTINGS_SIGNED_IN = `import React from 'react';
import {
  Button,
  Platform,
  PlatformColor,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { defineScreen } from '${PACKAGE}';
import { signOut } from '../session';

function Settings() {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Settings</Text>
      <Button title="Sign out" onPress={signOut} />
    </View>
  );
}

export const SettingsScreen = defineScreen({
  component: Settings,
  options: s => s.title('Settings'),
});

${STYLES}`;

/**
 * A Material Symbols icon (Outlined, Apache License 2.0) as an Android vector drawable, tinted as the tab bar's
 * other icons.
 * @param {string} pathData the icon's path in its 960 × 960 box
 */
function drawable(pathData) {
  return `<!-- Material Symbols, Apache License 2.0 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="${pathData}"/>
</vector>
`;
}

// The paths of the icons, Outlined at weight 400, as google/material-design-icons has them (the Showcase's too).
const ICONS = {
  ic_home:
    'M240,760L360,760L360,520L600,520L600,760L720,760L720,400L480,220L240,400L240,760ZM160,840L160,360L480,120L800,360L800,840L520,840L520,600L440,600L440,840L160,840ZM480,490L480,490L480,490L480,490L480,490L480,490L480,490L480,490L480,490L480,490Z',
  ic_person:
    'M480,480Q414,480 367,433Q320,386 320,320Q320,254 367,207Q414,160 480,160Q546,160 593,207Q640,254 640,320Q640,386 593,433Q546,480 480,480ZM160,800L160,688Q160,654 177.5,625.5Q195,597 224,582Q286,551 350,535.5Q414,520 480,520Q546,520 610,535.5Q674,551 736,582Q765,597 782.5,625.5Q800,654 800,688L800,800L160,800ZM240,720L720,720L720,688Q720,677 714.5,668Q709,659 700,654Q646,627 591,613.5Q536,600 480,600Q424,600 369,613.5Q314,627 260,654Q251,659 245.5,668Q240,677 240,688L240,720ZM480,400Q513,400 536.5,376.5Q560,353 560,320Q560,287 536.5,263.5Q513,240 480,240Q447,240 423.5,263.5Q400,287 400,320Q400,353 423.5,376.5Q447,400 480,400ZM480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320ZM480,720L480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720Q480,720 480,720L480,720L480,720Z',
  ic_settings:
    'M370,880L354,752Q341,747 329.5,740Q318,733 307,725L188,775L78,585L181,507Q180,500 180,493.5Q180,487 180,480Q180,473 180,466.5Q180,460 181,453L78,375L188,185L307,235Q318,227 330,220Q342,213 354,208L370,80L590,80L606,208Q619,213 630.5,220Q642,227 653,235L772,185L882,375L779,453Q780,460 780,466.5Q780,473 780,480Q780,487 780,493.5Q780,500 778,507L881,585L771,775L653,725Q642,733 630,740Q618,747 606,752L590,880L370,880ZM440,800L519,800L533,694Q564,686 590.5,670.5Q617,655 639,633L738,674L777,606L691,541Q696,527 698,511.5Q700,496 700,480Q700,464 698,448.5Q696,433 691,419L777,354L738,286L639,328Q617,305 590.5,289.5Q564,274 533,266L520,160L441,160L427,266Q396,274 369.5,289.5Q343,305 321,327L222,286L183,354L269,418Q264,433 262,448Q260,463 260,480Q260,496 262,511Q264,526 269,541L183,606L222,674L321,632Q343,655 369.5,670.5Q396,686 427,694L440,800ZM482,620Q540,620 581,579Q622,538 622,480Q622,422 581,381Q540,340 482,340Q423,340 382.5,381Q342,422 342,480Q342,538 382.5,579Q423,620 482,620ZM480,480L480,480Q480,480 480,480Q480,480 480,480L480,480L480,480L480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480L480,480L480,480L480,480Q480,480 480,480Q480,480 480,480L480,480L480,480L480,480Q480,480 480,480Q480,480 480,480L480,480L480,480L480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480L480,480L480,480L480,480Q480,480 480,480Q480,480 480,480L480,480L480,480Z',
};

/**
 * The starter of a new app in place of the template's App and of its test, which renders App by itself:
 * - stack: Home in a stack, with a button that pushes Details;
 * - tabs: Home, Profile and Settings in tabs, each a stack, Home pushing Details; the tab icons for Android;
 * - auth: Login, and after the sign-in the tabs; the user in the app's context, `AppContext`.
 * @param {string} root
 * @param {string | undefined} app the template's App
 * @param {Template} [template]
 * @returns {FileChange[]}
 */
function planStarter(root, app, template = 'stack') {
  const screen = (
    /** @type {string} */ name,
    /** @type {string} */ text,
    /** @type {string} */ note
  ) => newFile(path.join(root, SCREENS_DIR, `${name}.tsx`), text, note);
  /** @type {FileChange[]} */
  const changes = [];
  if (template === 'stack') {
    changes.push(
      newFile(path.join(root, 'Navigation.ts'), STACK_NAVIGATION, 'Home and Details'),
      screen('Home', HOME, 'first screen'),
      screen('Details', DETAILS, 'pushed from Home')
    );
  } else {
    const signIn = template === 'auth';
    changes.push(
      signIn
        ? newFile(path.join(root, 'Navigation.ts'), AUTH_NAVIGATION, 'Login, then the tabs')
        : newFile(path.join(root, 'Navigation.ts'), TABS_NAVIGATION, 'three tabs'),
      ...(signIn
        ? [
            newFile(path.join(root, 'src', 'session.ts'), SESSION, 'sign in and out'),
            screen('Login', LOGIN, 'first screen signed out'),
          ]
        : []),
      screen('Home', HOME, 'first tab'),
      screen('Details', DETAILS, 'pushed from Home'),
      screen('Profile', signIn ? PROFILE_SIGNED_IN : titleScreen('Profile'), 'second tab'),
      screen('Settings', signIn ? SETTINGS_SIGNED_IN : titleScreen('Settings'), 'third tab'),
      ...Object.entries(ICONS).map(([name, pathData]) => {
        const file = path.join(
          root,
          'android',
          'app',
          'src',
          'main',
          'res',
          'drawable',
          `${name}.xml`
        );
        return { ...newFile(file, drawable(pathData), 'tab icon'), platform: 'android' };
      })
    );
  }
  if (app) {
    changes.push(removal(app, 'Home in its place'));
    // The screens render in the navigation, which a test of a component alone doesn't start.
    const test = ['App.test.tsx', 'App.test.js']
      .map((name) => path.join(root, '__tests__', name))
      .find(
        (file) => fs.existsSync(file) && /from\s*'\.\.\/App'/.test(fs.readFileSync(file, 'utf8'))
      );
    if (test) changes.push(removal(test, 'renders App'));
  }
  return changes;
}

/**
 * @param {string} file
 * @param {string} note
 * @returns {FileChange}
 */
function removal(file, note) {
  return {
    name: path.basename(file),
    platform: 'js',
    file,
    status: 'remove',
    before: fs.readFileSync(file, 'utf8'),
    note,
  };
}

/**
 * @param {string} file
 * @param {string} text
 * @param {string} note
 * @returns {FileChange}
 */
function newFile(file, text, note) {
  return { name: path.basename(file), platform: 'js', file, status: 'create', next: text, note };
}

module.exports = { planStarter, SCREENS_DIR, TEMPLATES };
