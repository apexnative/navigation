import { Platform } from 'react-native';
import { Root, Screen, SideMenu, SplitView, Stack, Tab, Tabs } from '@apexnative/navigation';
import { tabFonts } from '../app/barFonts';
import { showcaseTheme } from '../app/theme';
import { nativeProfile } from '../demo/nativeProfiles';
import { icons, md } from '../ui/icons';
import { emit, listen } from './bus';
import { lastHinge } from './hinge';
import { clipNavigation as nav } from './nav';
import { mainTabs, signOut } from './session';

/**
 * A step of a clip: a command, a pause, or a gesture the recording script makes on the device (a swipe back, a tap on
 * a native control); the clip waits for it.
 */
type Step = { run: () => unknown } | { wait: number } | { gesture: string };

interface Clip {
  /** The root the clip starts from, set before the recording starts. */
  root: () => unknown;
  steps: Step[];
}

const wait = (ms: number): Step => ({ wait: ms });
const run = (fn: () => unknown): Step => ({ run: fn });
const gesture = (name: string): Step => ({ gesture: name });

let modal = '';
const showModal = (name: string, config?: object) =>
  run(async () => {
    modal = await nav.showModal(name, config);
  });

let overlay = '';
const showOverlay = (name: string, config?: object) =>
  run(async () => {
    overlay = await nav.showOverlay(name, config);
  });

// The iPhone Duo steps: the clip waits until the user opens or folds the device in Device Hub and the hinge stays
// there a moment. A step the hinge already satisfies goes on at once.
type HingeValue = { state: string; angle: number | null } | null;
const POSES: Record<string, (hinge: HingeValue) => boolean> = {
  open: (h) => h?.state === 'fullyOpen',
  flat: (h) => h?.state === 'fullyOpen',
  fold: (h) => h?.state === 'closed',
  half: (h) => h?.state === 'partiallyOpen' && h.angle != null && h.angle <= 130,
};
const pose = (name: string): Step =>
  run(
    () =>
      new Promise<void>((resolve) => {
        clip.state = `gesture:${name}`;
        let timer: ReturnType<typeof setTimeout> | undefined;
        const check = (value: unknown) => {
          clearTimeout(timer);
          if (!POSES[name]!(value as HingeValue)) return;
          timer = setTimeout(() => {
            off();
            clip.state = 'running';
            resolve();
          }, 500);
        };
        const off = listen('hinge', check);
        check(lastHinge());
      })
  );

const push = (name: string, config?: object) => run(() => nav.push(name, config));
const pop = () => run(() => nav.pop());

// The iPad split view of the clips: the lists beside an item.
const splitView = () =>
  SplitView({ primary: Stack('Sidebar'), secondary: Stack(Screen('Details', { index: 0 })) });

// The tabs of the iPhone Duo clip: Board, with its four buttons, in the first tab.
const duoTabs = () =>
  Tabs(
    Tab(Stack('Board'))
      .id('home')
      .label('Notes')
      .icon({ ios: 'note.text', android: md('home') })
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
      .options(tabFonts)
  );

// The two themes the theme clip switches to, over the Showcase's.
const blueTheme = { colors: { ...showcaseTheme.colors, primary: '#3A86FF', bar: '#E4EDFF' } };
const greenTheme = { colors: { ...showcaseTheme.colors, primary: '#2A9D8F', bar: '#DDF1EC' } };

// The kinds of opening of the iOS side menu, one after another.
const menuKinds = (['slide', 'parallax', 'slide-and-scale', 'door'] as const).flatMap((kind) => [
  run(() =>
    nav.switchTo(
      Root(SideMenu({ left: Screen('Menu'), center: Stack('Home') }).animation(kind)).transition({
        enter: { enabled: false },
      })
    )
  ),
  wait(500),
  run(() => nav.openMenu('left')),
  wait(1100),
  run(() => nav.closeMenu()),
  wait(700),
]);

const CLIPS: Record<string, Clip> = {
  'intro-hero': {
    root: mainTabs,
    steps: [
      wait(900),
      run(() => nav.push('Details', { props: { index: 2 } })),
      wait(1300),
      gesture('back'),
      wait(900),
      run(() => nav.selectTab('settings')),
      wait(1200),
      run(() => nav.selectTab('home')),
      wait(700),
      showModal('Compose'),
      wait(1300),
      gesture('dismiss'),
      wait(1000),
    ],
  },
  'quick-start-result': {
    root: () => Stack('Start'),
    steps: [
      wait(900),
      run(() => emit('press')),
      wait(1500),
      run(() => nav.pop()),
      wait(1100),
      run(() => emit('press')),
      wait(1500),
      gesture('back'),
      wait(1000),
    ],
  },
  'moves-push': {
    root: () => Stack('Home'),
    steps: [wait(800), run(() => nav.push('Details', { props: { index: 0 } })), wait(1600)],
  },
  'moves-modal': {
    root: () => Stack('Home'),
    steps: [
      wait(800),
      showModal('Compose'),
      wait(1500),
      run(() => nav.dismissModal(modal)),
      wait(1100),
    ],
  },
  'moves-root': {
    root: () => Stack('Login'),
    steps: [wait(900), run(() => nav.switchTo(Root(mainTabs()).transition('fade'))), wait(1600)],
  },
  'sign-in': {
    root: () => Stack('Login'),
    steps: [
      wait(700),
      run(() => emit('type', 'alex@example.com')),
      wait(1800),
      run(() => emit('press')),
      wait(1600),
      run(() => nav.push('Details', { props: { index: 1 } })),
      wait(1100),
      showModal('Compose'),
      wait(1200),
      run(signOut),
      wait(1500),
    ],
  },
  'stack-push-back': {
    root: () => Stack('Home'),
    steps: [
      wait(800),
      run(() => nav.push('Details', { props: { index: 4 } })),
      wait(1200),
      run(() => nav.push('Profile', { props: { index: 4 } })),
      wait(1300),
      run(() => nav.pop()),
      wait(1100),
      gesture('back'),
      wait(1000),
    ],
  },
  'tabs-stacks': {
    root: mainTabs,
    steps: [
      wait(800),
      run(() => nav.push('Details', { props: { index: 5 } })),
      wait(1200),
      run(() => nav.selectTab('inbox')),
      wait(1300),
      run(() => nav.selectTab('home')),
      wait(1300),
      run(() => nav.pop()),
      wait(1000),
    ],
  },
  'modal-styles': {
    root: () => Stack('Home'),
    steps:
      Platform.OS === 'android'
        ? [
            wait(700),
            showModal('Details', { props: { index: 2 } }),
            wait(1400),
            run(() => nav.dismissModal(modal)),
            wait(900),
            showModal('Picker', { sheet: { detents: ['medium', 'large'] }, stack: false }),
            wait(1400),
            run(() => nav.dismissModal(modal)),
            wait(900),
          ]
        : [
            wait(700),
            showModal('Details', { props: { index: 2 }, style: 'pageSheet' }),
            wait(1400),
            run(() => nav.dismissModal(modal)),
            wait(900),
            showModal('Details', { props: { index: 3 }, style: 'fullScreen', closeButton: true }),
            wait(1400),
            run(() => nav.dismissModal(modal)),
            wait(900),
            showModal('Details', { props: { index: 5 }, style: 'formSheet' }),
            wait(1400),
            run(() => nav.dismissModal(modal)),
            wait(900),
          ],
  },
  'modal-sheet': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      showModal('Picker', { sheet: { detents: ['medium', 'large'], grabber: true }, stack: false }),
      wait(1400),
      run(() => emit('detent', 'large')),
      wait(1300),
      run(() => emit('detent', 'medium')),
      wait(1100),
      run(() => nav.dismissModal(modal)),
      wait(1000),
    ],
  },
  'side-menu': {
    root: () => SideMenu({ left: Screen('Menu'), center: Stack('Home') }),
    steps: [
      wait(800),
      run(() => nav.openMenu('left')),
      wait(1300),
      run(() => emit('pick', 3)),
      wait(1700),
    ],
  },
  'split-view': {
    root: () =>
      SplitView({ primary: Stack('Sidebar'), secondary: Stack(Screen('Details', { index: 0 })) }),
    steps: [
      wait(900),
      run(() => emit('pick', 3)),
      wait(1200),
      run(() => emit('pick', 6)),
      wait(1200),
      run(() => emit('pick', 1)),
      wait(1100),
    ],
  },
  'large-title': {
    root: () => Stack(Screen('Home', { largeTitle: true }).options((s) => s.largeTitle())),
    steps: [wait(900), run(() => emit('scroll', 500)), wait(1300), gesture('pull'), wait(1300)],
  },
  'bar-menus': {
    root: () => Stack('Note'),
    steps: [
      wait(800),
      gesture('tap:sort'),
      wait(900),
      gesture('tap:Name'),
      wait(1000),
      gesture('tap:more'),
      wait(1200),
      gesture('tap:away'),
      wait(800),
    ],
  },
  'prevent-leave': {
    root: () => Stack('Home'),
    steps: [
      wait(600),
      run(() => nav.push('EditProfile')),
      wait(1100),
      run(() => emit('type', 'Sam Rivera')),
      wait(1500),
      run(() => emit('blur')),
      wait(600),
      gesture('back'),
      wait(1200),
      gesture('tap:Keep editing'),
      wait(900),
    ],
  },
  'search-field': {
    root: () => Stack('Contacts'),
    steps: [
      wait(800),
      run(() => emit('scroll', 260)),
      wait(900),
      run(() => emit('scroll', -150)),
      wait(900),
      gesture('search:jo'),
      wait(1300),
      gesture('tap:Cancel'),
      wait(900),
    ],
  },
  'shared-element': {
    root: () => Stack('Gallery'),
    steps: [wait(800), run(() => emit('open', 4)), wait(1700), run(() => nav.pop()), wait(1300)],
  },
  'flow-tabs': {
    root: mainTabs,
    steps: [
      wait(800),
      run(() =>
        nav.push('Details', { props: { index: 2 }, options: { bottomTabs: { visible: false } } })
      ),
      wait(1300),
      run(() => nav.push('Profile', { props: { index: 2 } })),
      wait(1200),
      run(() => nav.popToRoot()),
      wait(1100),
      run(() => nav.setTabBadge(3, 'inbox')),
      wait(1000),
      run(() => nav.selectTab('inbox')),
      wait(1200),
    ],
  },
  'flow-form': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      showModal('Compose'),
      wait(1000),
      run(() => emit('type', 'Buy oat milk')),
      wait(1700),
      run(() => emit('blur')),
      wait(500),
      gesture('dismiss'),
      wait(1100),
      gesture('tap:Keep editing'),
      wait(800),
      run(() => emit('save')),
      wait(1700),
    ],
  },
  'moves-overlay': {
    root: () => Stack('Home'),
    steps: [
      wait(800),
      showOverlay('Toast'),
      wait(500),
      run(() => emit('scroll', 260)),
      wait(1300),
      run(() => nav.dismissOverlay(overlay)),
      wait(900),
    ],
  },
  'moves-tab': {
    root: mainTabs,
    steps: [
      wait(800),
      run(() => nav.selectTab('inbox')),
      wait(1200),
      push('Details', { props: { index: 1 } }),
      wait(1400),
    ],
  },
  'moves-column': {
    root: splitView,
    steps: [
      wait(900),
      run(() => nav.showInColumn('Details', { props: { index: 4 } })),
      wait(1200),
      run(() => nav.showInColumn('Details', { props: { index: 7 } })),
      wait(1100),
    ],
  },
  'screen-life': {
    root: () => Stack('Home'),
    steps: [
      wait(600),
      run(() => emit('scroll', 600)),
      wait(800),
      push('Details', { props: { index: 14 } }),
      wait(1200),
      gesture('back'),
      wait(1200),
    ],
  },
  'options-live': {
    root: () => Stack(Screen('EditProfile', { live: true })),
    steps: [
      wait(700),
      run(() => emit('type', 'Sam Rivera')),
      wait(1600),
      run(() => emit('blur')),
      wait(800),
    ],
  },
  'roots-transitions': {
    root: () => Stack('Login'),
    steps: [
      wait(800),
      run(() => nav.switchTo(Root(mainTabs()))),
      wait(1300),
      run(() => nav.switchTo(Root(Stack('Login')).transition('fade'))),
      wait(1300),
      run(() => nav.switchTo(Root(mainTabs()).transition({ enter: { enabled: false } }))),
      wait(1200),
    ],
  },
  'stack-commands': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Details', { props: { index: 1 } }),
      wait(900),
      push('Profile', { props: { index: 1 } }),
      wait(900),
      push('Details', { props: { index: 2 } }),
      wait(900),
      run(() => nav.popToRoot()),
      wait(1100),
      run(() =>
        nav.setStack('Home', Screen('Details', { index: 3 }), Screen('Profile', { index: 3 }))
      ),
      wait(1300),
    ],
  },
  'tabs-badge': {
    root: mainTabs,
    steps: [
      wait(700),
      run(() => nav.setTabBadge(3, 'inbox')),
      wait(1000),
      run(() => nav.setTabBadge(12, 'inbox')),
      wait(900),
      run(() => emit('dot', true)),
      wait(900),
      run(() => nav.setTabBadge(null, 'inbox')),
      wait(600),
      run(() => emit('dot', false)),
      wait(900),
    ],
  },
  'tabs-platforms': {
    root: () =>
      mainTabs()
        .minimize('onScrollDown')
        .android((tabs) => tabs.options({ hideOnScroll: true })),
    steps: [wait(700), gesture('scroll-down'), wait(1200), gesture('scroll-up'), wait(1000)],
  },
  'tabs-ipad-sidebar': {
    root: () => mainTabs(true).sidebar('sidebar').customizable('clips'),
    steps: [
      wait(900),
      gesture('tap:Inbox'),
      wait(1000),
      gesture('tap:sidebar-toggle'),
      wait(1200),
      gesture('tap:Contacts'),
      wait(1000),
      gesture('tap:sidebar-toggle'),
      wait(1000),
    ],
  },
  'tabs-ios26': {
    root: () => mainTabs().minimize('onScrollDown').accessory('MiniPlayer'),
    steps: [wait(800), gesture('scroll-down'), wait(1400), gesture('scroll-up'), wait(1200)],
  },
  'tab-repress': {
    root: mainTabs,
    steps: [
      wait(700),
      push('Details', { props: { index: 2 } }),
      wait(900),
      push('Profile', { props: { index: 2 } }),
      wait(900),
      gesture('tap:tab-home'),
      wait(1200),
      run(() => emit('scroll', 700)),
      wait(700),
      gesture('tap:tab-home'),
      wait(1100),
    ],
  },
  'modal-close': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      showModal('Details', { props: { index: 2 }, style: 'pageSheet' }),
      wait(1000),
      gesture(Platform.OS === 'android' ? 'back' : 'dismiss'),
      wait(900),
      showModal('Details', { props: { index: 3 }, closeButton: true }),
      wait(1000),
      gesture('tap:close'),
      wait(900),
    ],
  },
  'overlay-kinds': {
    root: mainTabs,
    steps: [
      wait(700),
      showOverlay('Toast'),
      wait(1500),
      run(() => nav.dismissOverlay(overlay)),
      wait(600),
      showOverlay('MiniPlayer', { props: { overlay: true } }),
      wait(900),
      gesture('tap:tab-inbox'),
      wait(1100),
      run(() => nav.dismissOverlay(overlay)),
      wait(700),
    ],
  },
  'split-compact': {
    root: splitView,
    steps: [wait(800), gesture('narrow'), wait(1500), gesture('wide'), wait(1000)],
  },
  'bar-what': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Details', {
        props: { index: 3 },
        options: {
          topBar: {
            rightButtons: [{ id: 'share', systemItem: 'action', icon: { android: md('share') } }],
          },
        },
      }),
      wait(1200),
      gesture(Platform.OS === 'android' ? 'none' : 'back-half'),
      wait(900),
      gesture('back'),
      wait(900),
    ],
  },
  'bar-hide': {
    root: () => Stack(Screen('Home').options((s) => s.topBar({ hideOnScroll: true }))),
    steps: [
      wait(700),
      gesture('scroll-down'),
      wait(1200),
      gesture('scroll-up'),
      wait(1000),
      push('Gallery', { options: { topBar: { visible: false } } }),
      wait(1200),
    ],
  },
  'title-menu': {
    root: () => Stack(Screen('Note', { kind: 'title' })),
    steps: [
      wait(700),
      gesture('tap:title'),
      wait(1000),
      gesture('tap:Rename'),
      wait(600),
      gesture('type:Trip plan'),
      wait(1000),
    ],
  },
  'bar-buttons': {
    root: () => Stack(Screen('Note', { kind: 'buttons' })),
    steps: [
      wait(900),
      gesture('tap:Edit'),
      wait(900),
      run(() => emit('badge', 2)),
      wait(900),
      gesture('tap:Done'),
      wait(800),
    ],
  },
  'back-modes': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Details', { props: { index: 1 } }),
      wait(1000),
      pop(),
      wait(500),
      push('Details', {
        props: { index: 2 },
        options: { topBar: { backButton: { title: 'Notes' } } },
      }),
      wait(1000),
      pop(),
      wait(500),
      push('Details', {
        props: { index: 3 },
        options: { topBar: { backButton: { displayMode: 'generic' } } },
      }),
      wait(1000),
      pop(),
      wait(500),
      push('Details', {
        props: { index: 4 },
        options: { topBar: { backButton: { displayMode: 'minimal' } } },
      }),
      wait(1000),
      pop(),
      wait(700),
    ],
  },
  'swipe-back': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Details', { props: { index: 5 } }),
      wait(900),
      gesture('back'),
      wait(900),
      push('Details', { props: { index: 6 } }),
      wait(900),
      gesture('back-middle'),
      wait(900),
    ],
  },
  'search-while': {
    root: () => Stack(Screen('Contacts', { filters: true })),
    steps: [
      wait(700),
      gesture('search:a'),
      wait(900),
      gesture('tap:Friends'),
      wait(900),
      gesture('search-more:l'),
      wait(900),
      gesture('tap:Cancel'),
      wait(800),
    ],
  },
  'toolbar-fab': {
    root: () =>
      Platform.OS === 'android'
        ? Stack(
            Screen('Home').options((s) =>
              s.fab({
                id: 'fab',
                icon: icons.plus,
                actions: [
                  { id: 'fab-note', icon: icons.star, size: 'mini' },
                  { id: 'fab-share', icon: icons.share, size: 'mini' },
                ],
              })
            )
          )
        : Stack(Screen('Note', { kind: 'toolbar' })),
    steps:
      Platform.OS === 'android'
        ? [wait(800), gesture('tap:fab'), wait(1200), gesture('tap:away'), wait(900)]
        : [wait(900), gesture('scroll-down'), wait(1000), gesture('tap:Photo'), wait(900)],
  },
  'system-status': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Photo', { props: { index: 8, dark: true } }),
      wait(1400),
      gesture('back'),
      wait(1000),
    ],
  },
  'system-rtl': {
    root: mainTabs,
    steps: [
      wait(700),
      push('Details', { props: { index: 1 } }),
      wait(900),
      run(() => nav.setDirection('rtl')),
      wait(1000),
      gesture(Platform.OS === 'android' ? 'back' : 'tap:back'),
      wait(900),
      push('Details', { props: { index: 2 } }),
      wait(1200),
      gesture('back-rtl'),
      wait(800),
      run(() => nav.setDirection('ltr')),
      wait(600),
    ],
  },
  // A popover on iPad; on iPhone `adaptive: false` keeps it one instead of a sheet.
  'modal-popover': {
    root: () => Stack(Screen('Note', { kind: 'popover' })),
    steps: [
      wait(800),
      showModal('Picker', {
        popover: { sourceButtonId: 'filter', arrowDirections: ['up'], adaptive: false },
        contentSize: { width: 320, height: 380 },
        stack: false,
      }),
      wait(1400),
      gesture('tap:away'),
      wait(800),
      showModal('Picker', {
        popover: { sourceNativeID: 'sort', arrowDirections: ['up', 'down'], adaptive: false },
        contentSize: { width: 320, height: 380 },
        stack: false,
      }),
      wait(1400),
      gesture('tap:away'),
      wait(800),
    ],
  },
  'theme-switch': {
    root: mainTabs,
    steps: [
      wait(700),
      run(() => nav.setTheme(blueTheme)),
      wait(1200),
      run(() => nav.selectTab('inbox')),
      wait(900),
      run(() => nav.setTheme(greenTheme)),
      wait(1200),
      run(() => nav.setTheme(showcaseTheme)),
      wait(1000),
    ],
  },
  'errors-retry': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Feed'),
      wait(900),
      run(() => emit('break')),
      wait(1400),
      gesture('tap:Try again'),
      wait(1000),
    ],
  },
  'transitions-presets': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Details', { props: { index: 1 } }),
      wait(900),
      pop(),
      wait(700),
      push('Details', { props: { index: 2 }, transition: 'fade' }),
      wait(900),
      pop(),
      wait(700),
      push('Details', { props: { index: 3 }, transition: 'slideUp' }),
      wait(900),
      pop(),
      wait(900),
    ],
  },
  preview: {
    root: () => Stack('Gallery'),
    steps: [
      wait(800),
      gesture('long-press:tile-3'),
      wait(1200),
      gesture('tap:preview'),
      wait(1200),
    ],
  },
  'native-screen': {
    root: () => Stack('Home'),
    steps:
      Platform.OS === 'android'
        ? [
            wait(700),
            push('NativeProfile', { props: nativeProfile }),
            wait(1300),
            gesture('back'),
            wait(800),
          ]
        : [
            wait(700),
            push('NativeProfile', { props: nativeProfile }),
            wait(1300),
            gesture('back'),
            wait(800),
            push('SwiftUIProfile', { props: nativeProfile }),
            wait(1300),
            gesture('back'),
            wait(800),
          ],
  },
  'flow-pending-link': {
    root: () => {
      nav.setContext({ signedIn: false });
      return Stack('Login');
    },
    steps: [
      wait(700),
      gesture('openurl:apexnative://chat/1'),
      wait(900),
      run(() => emit('type', 'alex@example.com')),
      wait(1700),
      run(async () => {
        await nav.switchTo(Root(mainTabs()).transition('fade'));
        nav.setContext({ signedIn: true });
      }),
      wait(1800),
    ],
  },
  'flow-onboarding': {
    root: () => Stack(Screen('Onboarding', { step: 1 })),
    steps: [
      wait(800),
      run(() => emit('next')),
      wait(1000),
      run(() => emit('next')),
      wait(1000),
      run(() => emit('next')),
      wait(1400),
    ],
  },
  'flow-list-details': {
    root: () => (Platform.OS === 'ios' && Platform.isPad ? splitView() : Stack('Home')),
    steps:
      Platform.OS === 'ios' && Platform.isPad
        ? [
            wait(800),
            run(() => emit('pick', 2)),
            wait(1100),
            run(() => emit('pick', 5)),
            wait(1100),
          ]
        : [
            wait(700),
            push('Details', { props: { index: 2 } }),
            wait(1300),
            gesture('back'),
            wait(900),
          ],
  },
  'flow-sheet': {
    root: () => Stack('Map'),
    steps: [
      wait(500),
      showModal('Places', {
        sheet: { detents: ['medium', 'large'], grabber: true, largestUndimmedDetent: 'medium' },
        stack: false,
      }),
      wait(1100),
      run(() => emit('detent', 'large')),
      wait(1000),
      run(() => emit('scroll', 400)),
      wait(900),
      run(() => emit('detent', 'medium')),
      wait(900),
    ],
  },
  'flow-search': {
    root: () => Stack('Contacts'),
    steps: [
      wait(700),
      gesture('search:zz'),
      wait(900),
      gesture('search:al'),
      wait(900),
      gesture('tap:row-1'),
      wait(1100),
      gesture('back'),
      wait(900),
    ],
  },
  'guide-screen': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('EditNote'),
      wait(1300),
      run(() => emit('type', 'And the map.')),
      wait(1400),
      run(() => emit('blur')),
      wait(400),
      gesture('back'),
      wait(1100),
      gesture('tap:Keep editing'),
      wait(700),
      run(() => emit('save')),
      wait(1100),
    ],
  },
  'gestures-swipe': {
    root: () => Stack('Home'),
    steps: [
      wait(700),
      push('Carousel'),
      wait(900),
      gesture('swipe-left-middle'),
      wait(600),
      gesture('swipe-left-middle'),
      wait(700),
      gesture('back'),
      wait(900),
    ],
  },
  'refetch-on-focus': {
    root: () => Stack('Feed'),
    steps: [wait(700), push('Details', { props: { index: 7 } }), wait(1000), pop(), wait(1600)],
  },
  'sidemenu-kinds': {
    root: () => SideMenu({ left: Screen('Menu'), center: Stack('Home') }),
    steps: [wait(500), ...menuKinds],
  },
  // iPhone Duo, recorded with the Device Hub window so the fold shows: the user opens and folds the device, the poses
  // wait for it. Board, the root, sends the hinge.
  'duo-vertical-bar': {
    root: duoTabs,
    steps: [
      pose('fold'),
      wait(1000),
      push('Details', { props: { index: 2 } }),
      wait(1200),
      pop(),
      wait(800),
      run(() => nav.selectTab('inbox')),
      wait(1000),
      run(() => nav.selectTab('home')),
      wait(700),
      pose('open'),
      wait(1600),
    ],
  },
  'duo-pair': {
    root: () => Stack('Board'),
    steps: [
      pose('fold'),
      wait(900),
      push('ProfileWithDetails', { props: { index: 2 } }),
      wait(1300),
      pose('open'),
      wait(1600),
      pop(),
      wait(1000),
    ],
  },
  'duo-pair-overlay': {
    root: () => Stack('Board'),
    steps: [
      pose('flat'),
      wait(900),
      push('PhotoWithDetails', { props: { index: 4 } }),
      wait(1300),
      pose('half'),
      wait(1600),
      pose('flat'),
      wait(1000),
    ],
  },
  'duo-fold': {
    root: () => Stack('Spread'),
    steps: [pose('flat'), wait(1000), pose('half'), wait(1600), pose('flat'), wait(1000)],
  },
  'duo-hinge': {
    root: () => Stack('Lid'),
    steps: [pose('fold'), wait(900), pose('open'), wait(800), pose('fold'), wait(900)],
  },
};

type State = 'idle' | 'ready' | 'running' | 'done' | `gesture:${string}` | `error:${string}`;

const clip = {
  state: 'idle' as State,
  names: Object.keys(CLIPS),
  resume: undefined as undefined | (() => void),
  /** Sets the clip's root; the recording starts after it. */
  async setup(name: string) {
    clip.state = 'idle';
    errors.splice(0);
    const found = CLIPS[name];
    if (!found) {
      clip.state = `error:no clip ${name}`;
      return;
    }
    await nav.dismissAllModals().catch(() => undefined);
    await nav.dismissAllOverlays().catch(() => undefined);
    // What a clip before may have changed: the theme and the direction.
    nav.setTheme(showcaseTheme);
    nav.setDirection('ltr');
    await nav.switchTo(found.root());
    clip.state = 'ready';
  },
  /** Runs the steps; a gesture step waits for `next()`. */
  async go(name: string) {
    const found = CLIPS[name];
    if (!found) return;
    clip.state = 'running';
    try {
      for (const step of found.steps) {
        if ('wait' in step)
          await new Promise<void>((resolve) => setTimeout(() => resolve(), step.wait));
        else if ('run' in step) await step.run();
        else {
          clip.state = `gesture:${step.gesture}`;
          await new Promise<void>((resolve) => {
            clip.resume = () => resolve();
          });
          clip.state = 'running';
        }
      }
      await nav.showOverlay('Tick');
      await new Promise<void>((resolve) => setTimeout(() => resolve(), 300));
      clip.state = 'done';
    } catch (error) {
      clip.state = `error:${String(error)}`;
    }
  },
  /** The gesture is made: the clip goes on. */
  next() {
    clip.resume?.();
    clip.resume = undefined;
  },
};

// The errors while a clip runs, for the recording script: LogBox doesn't show them over the recording.
const errors: string[] = [];
const consoleError = console.error;
console.error = (...args: unknown[]) => {
  errors.push(args.map(String).join(' ').slice(0, 300));
  consoleError(...args);
};

(globalThis as { __clip?: typeof clip & { errors: string[] } }).__clip = Object.assign(clip, {
  nav,
  errors,
});
