import { createNavigation } from '@apexnative/navigation';
import App from './App';

export const Navigation = createNavigation({
  screens: { Home: App },
  root: 'Home',
});
