import { defineArrangement, nativeScreen, type ScreenOptions } from '@apexnative/navigation';
import { type NativeProfileProps, useProfileFunctions } from '../demo/nativeProfiles';
import { usePropsDemo } from '../demo/usePropsDemo';
import { BarBackground } from '../demo/BarBackground';
import { BarButtonView } from '../demo/BarButtonView';
import { ChatTitle } from '../demo/ChatTitle';
import { SearchResults } from '../demo/SearchResults';
import { DetailsScreen } from '../demo/DetailsScreen';
import { DialogOverlay } from '../demo/DialogOverlay';
import { SideMenuCenterScreen } from '../demo/SideMenuCenterScreen';
import { SideMenuMenuScreen } from '../demo/SideMenuMenuScreen';
import { SplitDetailScreen } from '../demo/SplitDetailScreen';
import { SplitInspectorScreen } from '../demo/SplitInspectorScreen';
import { SplitItemsScreen } from '../demo/SplitItemsScreen';
import { SplitSidebarScreen } from '../demo/SplitSidebarScreen';
import { PairDetailsScreen } from '../demo/PairDetailsScreen';
import { PairProfileScreen } from '../demo/PairProfileScreen';
import { HardwareRegionsScreen } from '../demo/HardwareRegionsScreen';
import { HingeDemoScreen } from '../demo/HingeDemoScreen';
import { StackStepScreen } from '../demo/StackStepScreen';
import { ChatScreen } from '../demo/ChatScreen';
import { DraftScreen } from '../demo/DraftScreen';
import { ButtonHooksScreen } from '../demo/ButtonHooksScreen';
import { HooksDemoScreen } from '../demo/HooksDemoScreen';
import { TabPressDemoScreen } from '../demo/TabPressDemoScreen';
import { BrokenScreen } from '../demo/BrokenScreen';
import { SlowScreen } from '../demo/SlowScreen';
import { LateContentScreen } from '../demo/LateContentScreen';
import { ContextDemoScreen } from '../demo/ContextDemoScreen';
import { PlainDetailsScreen } from '../demo/PlainDetailsScreen';
import { PlainHomeScreen } from '../demo/PlainHomeScreen';
import { ThemeDemoScreen } from '../demo/ThemeDemoScreen';
import { SharedStateScreen } from '../demo/SharedStateScreen';
import { GalleryItemScreen } from '../demo/GalleryItemScreen';
import { GalleryListScreen } from '../demo/GalleryListScreen';
import { GalleryScreen } from '../demo/GalleryScreen';
import { CardScreen } from '../demo/CardScreen';
import { CardsScreen } from '../demo/CardsScreen';
import { PeopleScreen } from '../demo/PeopleScreen';
import { PersonScreen } from '../demo/PersonScreen';
import { RootDemoScreen } from '../demo/RootDemoScreen';
import { WaitForRenderDemoScreen } from '../demo/WaitForRenderDemoScreen';
import { HeavyScreen } from '../demo/HeavyScreen';
import { LoginDemoScreen } from '../demo/LoginDemoScreen';
import { WelcomeDemoScreen } from '../demo/WelcomeDemoScreen';
import { GroupFirstScreen } from '../demo/GroupFirstScreen';
import { GroupSecondScreen } from '../demo/GroupSecondScreen';
import { TabsAccessory } from '../demo/TabsAccessory';
import { TabsDemoScreen } from '../demo/TabsDemoScreen';
import { TabItemCell } from '../demo/TabItemCell';
import { FormOverlay } from '../demo/FormOverlay';
import { MiniPlayerOverlay } from '../demo/MiniPlayerOverlay';
import { PlainListScreen } from '../demo/PlainListScreen';
import { TitleView } from '../demo/TitleView';
import { ToastOverlay } from '../demo/ToastOverlay';
import { ShapeScreen } from '../demo/ShapeScreen';
import { ShapesScreen } from '../demo/ShapesScreen';
import { BarAvatar } from '../examples/BarAvatar';
import { BarLogo } from '../examples/BarLogo';
import { ClubsScreen } from '../examples/ClubsScreen';
import { EventScreen } from '../examples/EventScreen';
import { ExamplesScreen } from '../examples/ExamplesScreen';
import { SafeAreaScreen } from '../examples/SafeAreaScreen';
import { HomeScreen } from '../examples/HomeScreen';
import { PostHeader } from '../examples/PostHeader';
import { PostScreen } from '../examples/PostScreen';
import { JournalScreen } from '../showcase/JournalScreen';
import { SearchScreen } from '../showcase/SearchScreen';
import { SectionScreen } from '../showcase/SectionScreen';
import { SectionsScreen } from '../showcase/SectionsScreen';
import { SettingsScreen } from '../showcase/SettingsScreen';

/** Every screen of the app by name: the commands and the layouts name them. */
export const screens = {
  // The Showcase.
  Sections: SectionsScreen,
  Section: SectionScreen,
  Search: SearchScreen,
  Journal: JournalScreen,
  Settings: SettingsScreen,
  // The screens the scenarios show.
  Details: DetailsScreen,
  PlainList: PlainListScreen,
  // The screens of 6.11, an app's first push as it is written: no backgrounds, no theme.
  PlainHome: PlainHomeScreen,
  PlainDetails: PlainDetailsScreen,
  // The overlays of section 8.
  ToastOverlay,
  DialogOverlay,
  MiniPlayerOverlay,
  FormOverlay,
  // The demo of tabs of section 9.
  TabsDemo: TabsDemoScreen,
  TabsAccessory,
  TabItemCell,
  // The side menu demo of section 10.
  SideMenuCenter: SideMenuCenterScreen,
  SideMenuMenu: SideMenuMenuScreen,
  // The split view demo of section 11.
  SplitSidebar: SplitSidebarScreen,
  SplitItems: SplitItemsScreen,
  SplitDetail: SplitDetailScreen,
  SplitInspector: SplitInspectorScreen,
  // The pairs of 11.7: Profile comes with Details in a large window (`arrangement`); the entries pair them under names of
  // their own with every setting.
  PairProfile: PairProfileScreen,
  PairDetails: PairDetailsScreen,
  PairOverlay: defineArrangement<{ id: string }>({
    primary: 'PairProfile',
    secondary: 'PairDetails',
    style: 'overlay',
    compact: 'pair',
  }),
  PairEverywhere: defineArrangement<{ id: string }>({
    primary: 'PairProfile',
    secondary: 'PairDetails',
    compact: 'pair',
  }),
  PairThird: defineArrangement<{ id: string }>({
    primary: 'PairProfile',
    secondary: 'PairDetails',
    fraction: 1 / 3,
    compact: 'pair',
  }),
  // The screens of 6.9 and 6.10.
  HardwareRegions: HardwareRegionsScreen,
  HingeDemo: HingeDemoScreen,
  // The screens of section 12.
  StackStep: StackStepScreen,
  Chat: ChatScreen,
  // The screen of 3.4, 3.6 and 7.3.
  Draft: DraftScreen,
  // The screens of section 13.
  HooksDemo: HooksDemoScreen,
  ButtonHooks: ButtonHooksScreen,
  TabPressDemo: TabPressDemoScreen,
  // The screens of section 14.
  Broken: BrokenScreen,
  Slow: SlowScreen,
  Later: { load: () => import('../demo/LaterScreen') },
  // The screens of section 15.
  ThemeDemo: ThemeDemoScreen,
  ContextDemo: ContextDemoScreen,
  // The screen of section 16.
  SharedState: SharedStateScreen,
  // The screens of section 17.
  LateContent: LateContentScreen,
  Gallery: GalleryScreen,
  GalleryItem: GalleryItemScreen,
  GalleryList: GalleryListScreen,
  People: PeopleScreen,
  Person: PersonScreen,
  Cards: CardsScreen,
  Card: CardScreen,
  Shapes: ShapesScreen,
  Shape: ShapeScreen,
  RootDemo: RootDemoScreen,
  WaitForRenderDemo: WaitForRenderDemoScreen,
  Heavy: HeavyScreen,
  // The native screens of section 18, registered natively in AppDelegate.swift and MainApplication.kt.
  NativeProfile: nativeScreen<NativeProfileProps>('NativeProfileScreen', {
    options: (s, props) => s.title(props.name).hideBottomTabs(),
    useProps: useProfileFunctions,
  }),
  UsePropsDemo: usePropsDemo,
  // The roots of section 19.
  LoginDemo: LoginDemoScreen,
  WelcomeDemo: WelcomeDemoScreen,
  SwiftUIProfile: nativeScreen<NativeProfileProps>('SwiftUIProfileScreen', {
    options: (s, props) => s.title(props.name).largeTitle().hideBottomTabs(),
    useProps: useProfileFunctions,
  }),
  // Components in the bar.
  TitleView,
  BarBackground,
  BarButtonView,
  ChatTitle,
  SearchResults,
  // The examples: screens of a made-up app and the components of their bars.
  Examples: ExamplesScreen,
  ExampleSafeArea: SafeAreaScreen,
  ExampleHome: HomeScreen,
  ExampleClubs: ClubsScreen,
  ExamplePost: PostScreen,
  ExampleEvent: EventScreen,
  ExampleAvatar: BarAvatar,
  ExampleLogo: BarLogo,
  ExamplePostHeader: PostHeader,
};

/** Screens that share options: the group of section 19. */
export const groups = {
  'section-19': {
    options: (s: ScreenOptions) => s.subtitle('A screen of the group').largeTitle(),
    screens: { GroupFirst: GroupFirstScreen, GroupSecond: GroupSecondScreen },
  },
};
