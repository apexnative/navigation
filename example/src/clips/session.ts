import { Root, Stack, Tab, Tabs } from '@apexnative/navigation';
import { tabFonts } from '../app/barFonts';
import { md } from '../ui/icons';
import { clipNavigation } from './nav';

/** The app of most clips: three tabs, each with its stack; `more` adds two for the iPad sidebar. */
export function mainTabs(more = false) {
  const extra = more
    ? [
        Tab(Stack('Contacts'))
          .id('contacts')
          .label('Contacts')
          .icon({ ios: 'person.2', android: md('mail') })
          .options(tabFonts),
        Tab(Stack('Gallery'))
          .id('photos')
          .label('Photos')
          .icon({ ios: 'photo.on.rectangle', android: md('star') })
          .options(tabFonts),
      ]
    : [];
  return Tabs(
    Tab(Stack('Home'))
      .id('home')
      .label('Home')
      .icon({ ios: 'house', android: md('home') })
      .options(tabFonts),
    Tab(Stack('Inbox'))
      .id('inbox')
      .label('Inbox')
      .icon({ ios: 'tray', android: md('inbox') })
      .options(tabFonts),
    Tab(Stack('Settings'))
      .id('settings')
      .label('Settings')
      .icon({ ios: 'gearshape', android: md('settings') })
      .options(tabFonts),
    ...extra
  );
}

/** Signs in: the context knows the user, and the app switches to the tabs. */
export function signIn() {
  clipNavigation.setContext({ signedIn: true });
  return clipNavigation.switchTo(Root(mainTabs()).transition('fade'));
}

/** Signs out: back to Login; the modals and the tabs go. */
export function signOut() {
  clipNavigation.setContext({ signedIn: false });
  return clipNavigation.switchTo(Root(Stack('Login')).transition('fade'));
}
