// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const PACKAGE = '@apexnative/navigation';

// The template's entry point, with its comment on top kept.
const TEMPLATE = new RegExp(
  String.raw`^(\s*\/\*\*[\s\S]*?\*\/\s*)?` +
    String.raw`import\s*\{\s*AppRegistry\s*\}\s*from\s*'react-native';?\s*` +
    String.raw`import\s+App\s+from\s*'\.\/App';?\s*` +
    String.raw`import\s*\{\s*name\s+as\s+appName\s*\}\s*from\s*'\.\/app\.json';?\s*` +
    String.raw`AppRegistry\.registerComponent\(\s*appName\s*,\s*\(\)\s*=>\s*App\s*\);?\s*$`
);

const ENTRY = `import { AppRegistry } from '${PACKAGE}';
import { name } from './app.json';
import { Navigation } from './Navigation';

AppRegistry.registerComponent(name, Navigation);
`;

const NAVIGATION = `import { createNavigation } from '${PACKAGE}';
import App from './App';

export const Navigation = createNavigation({
  screens: { Home: App },
  root: 'Home',
});
`;

const HOW_TO = `Start the app through the navigation: its AppRegistry, with the app made by createNavigation.

  // index.js
  import { AppRegistry } from '${PACKAGE}';
  import { name } from './app.json';
  import { Navigation } from './Navigation';

  AppRegistry.registerComponent(name, Navigation);

  // Navigation.ts
  ${NAVIGATION.trim().split('\n').join('\n  ')}`;

/**
 * index.js: the app starts through the navigation, which shows the template's App as its first screen.
 * @param {string} text
 * @returns {StepResult}
 */
function planEntry(text) {
  if (new RegExp(String.raw`from\s*'${PACKAGE.replace('/', '\\/')}'`).test(text)) {
    return { status: 'done', note: 'already set up' };
  }
  const found = TEMPLATE.exec(text);
  if (!found) return { status: 'manual', note: 'changed by hand, see below', howTo: HOW_TO };
  const comment = found[1] ? found[1].trim() + '\n\n' : '';
  return { status: 'change', next: comment + ENTRY, note: 'entry point → Navigation' };
}

/**
 * The file the entry point imports: the app's screens and its first root.
 * @returns {string}
 */
function navigationFile() {
  return NAVIGATION;
}

module.exports = { planEntry, navigationFile, HOW_TO };
