// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * A place of the app's code left to change by hand: the file and line, what it is, the line as it is, its new form, and
 * the step of the migration page about it (`docs`: the page and its anchor).
 * @typedef {{ file: string; line: number; step: string; what: string; before: string; after: string; docs: string }}
 *   ReportItem
 */

/** @typedef {{ pattern: RegExp; what: string; after: string; step: string }} Rule */

// The code of react-native-navigation, by the steps of "Moving from react-native-navigation".
/** @type {Rule[]} */
const FROM_RNN = [
  {
    pattern: /\bNavigation\.registerComponent(?:WithRedux)?\(/,
    what: 'a screen registered by name',
    after: 'an entry of screens in createNavigation: Name: Component',
    step: 'screens',
  },
  {
    pattern: /\bNavigation\.setRoot\(/,
    what: 'a root set by a command',
    after: "root of createNavigation (Stack, Tabs), or navigation.switchTo('name') between roots",
    step: 'root',
  },
  {
    pattern: /\bstatic\s+(?:get\s+)?options\b|\b[A-Z][\w$]*\.options\s*=/,
    what: 'options on the component',
    after: 'options of defineScreen({ component, options: (s) => s.title(…) })',
    step: 'options',
  },
  {
    pattern: /\bNavigation\.(?:push|pop|popTo|popToRoot|setStackRoot)\(/,
    what: 'a command by componentId',
    after: "navigation.push('Name', { props }), navigation.setStack(…): no componentId",
    step: 'commands',
  },
  {
    pattern:
      /\bNavigation\.(?:showModal|dismissModal|dismissAllModals|showOverlay|dismissOverlay|dismissAllOverlays)\(/,
    what: 'a modal or an overlay',
    after: "navigation.showModal('Name', { props }), navigation.dismiss()",
    step: 'modals',
  },
  {
    pattern: /\bNavigation\.events\(\)/,
    what: 'an event listener of the old API',
    after:
      'listeners of createNavigation, or the hooks of a screen (useScreenEvent, useNavigationButton)',
    step: 'events',
  },
  {
    pattern: /\bNavigation\.mergeOptions\(/,
    what: 'options of a shown screen',
    after: 'useNavigation().setOptions(…) in the screen, or useScreenOptions',
    step: 'merge-options',
  },
  {
    pattern: /\bNavigation\.setDefaultOptions\(/,
    what: 'default options',
    after: 'options and the theme of createNavigation',
    step: 'defaults',
  },
  {
    pattern: /\bNavigation\.(?:updateProps|constants|getLaunchArgs)\(/,
    what: 'a call with no direct equivalent',
    after: 'see What has no direct equivalent',
    step: 'no-equivalent',
  },
  {
    pattern: /\bpassProps\b/,
    what: 'props of a command',
    after: '{ props } of the command, typed by the screen',
    step: 'commands',
  },
  {
    pattern: /\bcomponentId\b/,
    what: 'the id of a screen in the code',
    after: 'useNavigation() acts from the screen it is in; navigation.getState() tells where',
    step: 'commands',
  },
  {
    pattern: /react-native-navigation\/Mock|\bmockNativeComponents\b/,
    what: 'the test mock of the old library',
    after: "jest.mock('@apexnative/navigation', …) in a setup file",
    step: 'cleanup',
  },
];

// The code of React Navigation, by the steps of "Moving from React Navigation".
/** @type {Rule[]} */
const FROM_REACT_NAVIGATION = [
  {
    pattern: /\bNavigationContainer\b/,
    what: 'the container of the navigators',
    after: 'AppRegistry.registerComponent(name, Navigation) in index.js',
    step: 'register',
  },
  {
    pattern: /\bcreateDrawerNavigator\b/,
    what: 'a drawer',
    after: 'SideMenu(…) in root of createNavigation',
    step: 'drawer',
  },
  {
    pattern: /\bcreate(?:NativeStack|Stack|BottomTab|MaterialTopTab|MaterialBottomTab)Navigator\b/,
    what: 'a navigator',
    after: 'root of createNavigation: Stack(…), Tabs(Tab(…), …)',
    step: 'root',
  },
  {
    pattern: /\bnavigation\.(?:navigate|goBack|push|replace|reset|popToTop|pop)\(/,
    what: 'a move between screens',
    after: "navigation.push('Name', { props }), navigation.pop()",
    step: 'moves',
  },
  {
    pattern: /\buseRoute\(|\broute\.params\b/,
    what: 'the params of a screen',
    after: 'the props of the screen component',
    step: 'screens',
  },
  {
    pattern: /\b(?:useFocusEffect|useIsFocused)\(/,
    what: 'a focus hook',
    after: "useFocusEffect and useIsFocused of '@apexnative/navigation'",
    step: 'hooks',
  },
  {
    pattern: /\bnavigation\.setOptions\(|\bscreenOptions\b/,
    what: 'header options',
    after: 'options of defineScreen, or useNavigation().setOptions(…)',
    step: 'options',
  },
  {
    pattern: /presentation:\s*['"](?:modal|transparentModal|formSheet)['"]/,
    what: 'a modal screen',
    after: "navigation.showModal('Name')",
    step: 'modals',
  },
  {
    pattern: /\blinking=\{/,
    what: 'the deep links',
    after: 'linking of createNavigation',
    step: 'linking',
  },
  {
    pattern: /from\s+['"]@react-navigation\/[^'"]+['"]/,
    what: 'an import of React Navigation',
    after: "from '@apexnative/navigation', then remove the old packages",
    step: 'cleanup',
  },
];

const PAGES = {
  'react-native-navigation': {
    rules: FROM_RNN,
    page: 'from-react-native-navigation',
    from: 'react-native-navigation',
  },
  'react-navigation': {
    rules: FROM_REACT_NAVIGATION,
    page: 'from-react-navigation',
    from: '@react-navigation/',
  },
};

// Folders of an app that hold no code of its own.
const SKIP = new Set([
  'node_modules',
  'ios',
  'android',
  'build',
  'Pods',
  '.git',
  'vendor',
  'coverage',
]);

/**
 * The places of the app's code left to change by hand: the lines of the files that import the old library, a rule
 * each, the first that takes the line. `skip` names lines moved already, as `file:line`.
 * @param {string} root
 * @param {'react-native-navigation' | 'react-navigation'} source
 * @param {Set<string>} [skip]
 * @returns {ReportItem[]}
 */
function reportCode(root, source, skip = new Set()) {
  const { rules, page, from } = PAGES[source];
  /** @type {ReportItem[]} */
  const items = [];
  for (const file of codeFiles(root)) {
    const text = fs.readFileSync(file, 'utf8');
    // A test of the old library names it in a mock: the rest of a file without it isn't the old library's.
    if (!text.includes(from)) continue;
    const relative = path.relative(root, file);
    text.split('\n').forEach((line, index) => {
      if (skip.has(`${relative}:${index + 1}`)) return;
      const rule = rules.find(({ pattern }) => pattern.test(line));
      if (!rule) return;
      items.push({
        file: relative,
        line: index + 1,
        step: rule.step,
        what: rule.what,
        before: line.trim().slice(0, 160),
        after: rule.after,
        docs: `${page}#${rule.step}`,
      });
    });
  }
  return items;
}

/**
 * The files of the app's own code, its configuration of Jest and Babel too.
 * @param {string} dir
 * @returns {string[]}
 */
function codeFiles(dir) {
  /** @type {string[]} */
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.') || SKIP.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...codeFiles(full));
    else if (/\.(tsx?|jsx?|mjs|cjs)$/.test(entry.name) && !entry.name.endsWith('.d.ts'))
      files.push(full);
  }
  return files;
}

module.exports = { reportCode, codeFiles };
