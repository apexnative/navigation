/**
 * The Showcase on the new API.
 */
// First, so section 18.7 measures from when the bundle started.
import './src/app/launchTiming';
// At the start, not when Settings first reads it: a reload goes back to the system's light or dark at once.
import './src/app/appearance';
import { AppRegistry } from '@apexnative/navigation';
import { name } from './app.json';
import { App } from './src/app/App';

AppRegistry.registerComponent(name, App);
