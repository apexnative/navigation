/**
 * The clips of the docs: screens with neutral names that a recording script drives (Metro serves this entry on a port
 * of its own, so the Showcase on the usual port doesn't see it).
 */
import { LogBox } from 'react-native';
import { AppRegistry } from '@apexnative/navigation';
import { name } from './app.json';
import { ClipsApp } from './src/clips/ClipsApp';

// No warnings or error reports over the recordings: the error clip shows the app's own fallback.
LogBox.ignoreAllLogs();
LogBox.uninstall();

AppRegistry.registerComponent(name, ClipsApp);
