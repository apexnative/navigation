import {
  createNavigation,
  defineArrangement,
  nativeScreen,
  Screen,
  Stack,
} from '@apexnative/navigation';
import { ErrorFallback } from '../app/ErrorFallback';
import { barFonts } from '../app/barFonts';
import { options } from '../app/options';
import { showcaseTheme } from '../app/theme';
import { type NativeProfileProps, useProfileFunctions } from '../demo/nativeProfiles';
import { clipNavigation } from './nav';
import { mainTabs } from './session';
import { BoardScreen } from './screens/BoardScreen';
import { CarouselScreen } from './screens/CarouselScreen';
import { ChatScreen } from './screens/ChatScreen';
import { ComposeScreen } from './screens/ComposeScreen';
import { ContactsScreen } from './screens/ContactsScreen';
import { DetailsScreen } from './screens/DetailsScreen';
import { EditNoteScreen } from './screens/EditNoteScreen';
import { EditProfileScreen } from './screens/EditProfileScreen';
import { FeedScreen } from './screens/FeedScreen';
import { GalleryScreen } from './screens/GalleryScreen';
import { HomeScreen } from './screens/HomeScreen';
import { InboxScreen } from './screens/InboxScreen';
import { LidScreen } from './screens/LidScreen';
import { LoginScreen } from './screens/LoginScreen';
import { MapScreen } from './screens/MapScreen';
import { MenuScreen } from './screens/MenuScreen';
import { MiniPlayerScreen } from './screens/MiniPlayerScreen';
import { NoteScreen } from './screens/NoteScreen';
import { OnboardingScreen } from './screens/OnboardingScreen';
import { PhotoScreen } from './screens/PhotoScreen';
import { PickerScreen } from './screens/PickerScreen';
import { PlacesScreen } from './screens/PlacesScreen';
import { ProfileScreen } from './screens/ProfileScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { SidebarScreen } from './screens/SidebarScreen';
import { SpreadScreen } from './screens/SpreadScreen';
import { StartScreen } from './screens/StartScreen';
import { TickScreen } from './screens/TickScreen';
import { ToastScreen } from './screens/ToastScreen';
import './run';

/**
 * The clips of the docs: an app of its own in the Showcase's look, started on the clip simulators by a
 * Metro of its own. `run.ts` puts the clips on `globalThis.__clip` for the recording script.
 */
export const ClipsApp = createNavigation({
  screens: {
    Start: StartScreen,
    Home: HomeScreen,
    Details: DetailsScreen,
    Profile: ProfileScreen,
    Inbox: InboxScreen,
    Settings: SettingsScreen,
    Compose: ComposeScreen,
    EditProfile: EditProfileScreen,
    Login: LoginScreen,
    Picker: PickerScreen,
    Menu: MenuScreen,
    Sidebar: SidebarScreen,
    Contacts: ContactsScreen,
    Gallery: GalleryScreen,
    Photo: PhotoScreen,
    Note: NoteScreen,
    EditNote: EditNoteScreen,
    Toast: ToastScreen,
    Tick: TickScreen,
    MiniPlayer: MiniPlayerScreen,
    Feed: FeedScreen,
    Onboarding: OnboardingScreen,
    Map: MapScreen,
    Places: PlacesScreen,
    Chat: ChatScreen,
    Carousel: CarouselScreen,
    // The clips of iPhone Duo: the vertical bar, the fold, the hinge and the pairs of screens.
    Board: BoardScreen,
    Spread: SpreadScreen,
    Lid: LidScreen,
    ProfileWithDetails: defineArrangement<{ index: number }>({
      primary: 'Profile',
      secondary: 'Details',
    }),
    PhotoWithDetails: defineArrangement<{ index: number }>({
      primary: 'Photo',
      secondary: 'Details',
      style: 'overlay',
    }),
    // The Showcase's native screens, registered natively in AppDelegate.swift and MainApplication.kt.
    NativeProfile: nativeScreen<NativeProfileProps>('NativeProfileScreen', {
      options: (s, props) => s.title(props.name),
      useProps: useProfileFunctions,
    }),
    SwiftUIProfile: nativeScreen<NativeProfileProps>('SwiftUIProfileScreen', {
      options: (s, props) => s.title(props.name),
      useProps: useProfileFunctions,
    }),
  },
  // The Showcase's look, so the clips sit well on the site: its theme, its default options and the fonts of its bars.
  theme: showcaseTheme,
  options: { ...options, topBar: { ...options.topBar, ...barFonts } },
  context: { signedIn: false },
  // The splash screen stays a moment at a cold start (the splash screen guide); the clips set their roots later.
  willMount: () => new Promise((resolve) => setTimeout(resolve, 2000)),
  root: mainTabs(),
  errorFallback: ErrorFallback,
  // A link that launches the app opens Profile, with Home under it to go back to.
  linking: {
    prefixes: ['apexnative://'],
    routes: {
      'profile/:id': {
        start: ({ id }) => Stack('Home', Screen('Profile', { index: Number(id) })),
        open: () => undefined,
      },
      // A chat opens only signed in: before, the link waits, and opens once the context says so.
      'chat/:id': {
        when: (context) => (context as { signedIn: boolean }).signedIn,
        open: ({ id }) => clipNavigation.push('Chat', { props: { id } }),
      },
    },
  },
});
