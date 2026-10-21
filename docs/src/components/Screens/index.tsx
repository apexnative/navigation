import Link from '@docusaurus/Link';
import clsx from 'clsx';
import React, { type ReactNode } from 'react';
import { Os } from '../Intro';
import styles from './styles.module.css';

const PARTS: { tone: string; title: string; text: ReactNode }[] = [
  {
    tone: 'native',
    title: 'The native screen',
    text: 'A screen of the platform: a view controller on iOS, a screen view on Android. The platform slides it in, keeps it in its stack and takes it back with the back gesture.',
  },
  {
    tone: 'bar',
    title: 'The top bar',
    text: "The platform draws it: the title, the buttons and the back button come from the screen's options, not from your component.",
  },
  {
    tone: 'react',
    title: 'Your component',
    text: 'The content: a React component in a React root of its own, rendered with the props of the command that showed it.',
  },
  {
    tone: 'safe',
    title: 'The safe area',
    text: 'The navigation keeps the content out from under the bars, the notch and the home indicator.',
  },
];

/** What a screen is made of: a phone with the parts numbered, and what each part is. */
export function ScreenAnatomy() {
  return (
    <div className={styles.anatomy}>
      <div
        className={styles.phone}
        role="img"
        aria-label="A native screen with the top bar of the platform and your component inside"
      >
        <div className={clsx(styles.phoneScreen, styles.tone_native)}>
          <span className={clsx(styles.badge, styles.badgeNative)}>1</span>
          <div className={styles.phoneBar}>
            <span className={styles.phoneBack}>‹ Home</span>
            <span className={styles.phoneTitle}>Profile</span>
            <span className={clsx(styles.badge, styles.badgeBar)}>2</span>
          </div>
          <div className={styles.phoneContent}>
            <code className={styles.phoneCode}>{'<Profile userId="7" />'}</code>
            <span className={clsx(styles.badge, styles.badgeReact)}>3</span>
          </div>
          <div className={styles.phoneSafe}>
            <span className={styles.phoneHome} />
            <span className={clsx(styles.badge, styles.badgeSafe)}>4</span>
          </div>
        </div>
      </div>
      <div className={styles.parts}>
        {PARTS.map((part, i) => (
          <div key={part.title} className={styles.part}>
            <span className={clsx(styles.partNumber, styles[`tone_${part.tone}`])}>{i + 1}</span>
            <span className={styles.partBody}>
              <span className={styles.partTitle}>{part.title}</span>
              <span className={styles.partText}>{part.text}</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

const STATIONS: { name: string; state: string; text: string; hooks: string[] }[] = [
  {
    name: 'Mounts',
    state: 'mounted',
    text: 'A command shows the screen. React renders the component with its props; the options are on the native screen already.',
    hooks: ['useEffect'],
  },
  {
    name: 'Appears',
    state: 'on screen',
    text: 'The screen is in place: the user sees it.',
    hooks: ['useScreenAppear', 'useFocusEffect'],
  },
  {
    name: 'Covered',
    state: 'covered, still mounted',
    text: 'Another screen goes over it: a push, a full-screen modal, another tab. It stays mounted, with its state and scroll.',
    hooks: ['useScreenDisappear', 'useFocusEffect:cleanup'],
  },
  {
    name: 'Back on top',
    state: 'on screen',
    text: 'What covered it goes away: the screen appears again, as it was.',
    hooks: ['useScreenAppear', 'useFocusEffect'],
  },
  {
    name: 'Leaves',
    state: 'unmounted',
    text: 'Back from it, its modal dismissed, a new root: the screen disappears, and React unmounts it.',
    hooks: ['useScreenDisappear', 'useEffect:cleanup'],
  },
];

// Each station takes two columns of the grid, so a band can start and end at the middle of a station.
const at = (from: number, to: number) => ({ gridColumn: `${from * 2} / ${to * 2}` });

/** The life of one screen: when it is mounted, when it is on screen, and the hooks at each station. */
export function ScreenLife() {
  return (
    <div className={styles.life}>
      <div className={styles.lifeTrack}>
        <div className={clsx(styles.band, styles.bandMounted)} style={{ ...at(1, 5), gridRow: 1 }}>
          Mounted: the component and its state live
        </div>
        <div className={clsx(styles.band, styles.bandShown)} style={{ ...at(2, 3), gridRow: 2 }}>
          On screen
        </div>
        <div className={clsx(styles.band, styles.bandHidden)} style={{ ...at(3, 4), gridRow: 2 }}>
          Covered
        </div>
        <div className={clsx(styles.band, styles.bandShown)} style={{ ...at(4, 5), gridRow: 2 }}>
          On screen
        </div>
        {STATIONS.map((station, i) => (
          <div
            key={station.name}
            className={styles.station}
            style={{ gridColumn: `${i * 2 + 1} / ${i * 2 + 3}` }}
          >
            <span
              className={clsx(styles.dot, i === STATIONS.length - 1 && styles.dotLast)}
              aria-hidden="true"
            >
              {i + 1}
            </span>
            <span className={styles.stationBody}>
              <span className={styles.stationName}>{station.name}</span>
              <span className={styles.stationState}>{station.state}</span>
              <span className={styles.stationText}>{station.text}</span>
              <span className={styles.hooks}>
                {station.hooks.map((hook) => {
                  // 'name:cleanup' is the cleanup of the hook, not the hook itself.
                  const [name, cleanup] = hook.split(':');
                  const code = <code className={styles.hook}>{name}</code>;
                  return cleanup ? (
                    <span key={hook} className={styles.hookCleanup}>
                      {code}
                      <span className={styles.hookWord}>cleanup</span>
                    </span>
                  ) : (
                    <React.Fragment key={hook}>{code}</React.Fragment>
                  );
                })}
              </span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

const MOVES: {
  kind: string;
  container: string;
  title: string;
  command: string;
  back: ReactNode;
  to: string;
}[] = [
  {
    kind: 'stack',
    container: 'Stack',
    title: 'Push a screen',
    command: "push('Details')",
    back: (
      <>
        the back button, the swipe, <code>pop()</code>
      </>
    ),
    to: '#push',
  },
  {
    kind: 'modal',
    container: 'Modals',
    title: 'Show a modal',
    command: "showModal('Compose')",
    back: (
      <>
        a swipe down, <code>dismiss()</code>
      </>
    ),
    to: '#modal',
  },
  {
    kind: 'overlay',
    container: 'Overlays',
    title: 'Show an overlay',
    command: "showOverlay('Toast')",
    back: (
      <>
        <code>dismissOverlay()</code>
      </>
    ),
    to: '#overlay',
  },
  {
    kind: 'tabs',
    container: 'Tabs',
    title: 'Select a tab',
    command: "selectTab('inbox')",
    back: 'the user picks another tab',
    to: '#tab',
  },
  {
    kind: 'columns',
    container: 'Split view',
    title: 'Show beside a list',
    command: "showInColumn('Details')",
    back: 'iPad: another item; iPhone and Android: a push',
    to: '#column',
  },
  {
    kind: 'root',
    container: 'Switching roots',
    title: 'Replace everything',
    command: "switchTo('main')",
    back: 'no way back: a new root',
    to: '#root',
  },
];

/** The kinds of moves between screens, each with a small drawing, its command and the way back. */
export function Moves() {
  return (
    <div className={styles.moves}>
      {MOVES.map((move) => (
        <Link
          key={move.kind}
          to={move.to}
          className={clsx(styles.move, styles[`move_${move.kind}`])}
        >
          <span className={styles.moveArt} aria-hidden="true">
            <span className={styles.artStage}>
              <span className={styles.artBase} />
              <span className={styles.artMoving} />
              {move.kind === 'tabs' && (
                <span className={styles.artTabs}>
                  <i />
                  <i />
                  <i />
                </span>
              )}
            </span>
          </span>
          <span className="eyebrow">{move.container}</span>
          <span className={styles.moveTitle}>{move.title}</span>
          <code className={styles.moveCommand}>{move.command}</code>
          <span className={styles.moveBack}>Back: {move.back}</span>
        </Link>
      ))}
    </div>
  );
}

type DemoKind = 'push' | 'modal' | 'overlay' | 'tab' | 'column' | 'root' | 'menu';

const DEMOS: Record<DemoKind, { caption?: string; rows: { label: ReactNode; text: ReactNode }[] }> =
  {
    push: {
      caption: 'As on iOS',
      rows: [
        {
          label: <Os name="iOS" />,
          text: 'Slides in from the right edge, and the screen under it moves aside. The top bar takes the new title and a back button.',
        },
        {
          label: <Os name="Android" />,
          text: 'The transition of the system, as the device sets it. The top bar shows the back arrow.',
        },
        {
          label: 'Back',
          text: (
            <>
              The back button, the swipe from the left edge on iOS, the system back on Android,{' '}
              <code>pop()</code>.
            </>
          ),
        },
      ],
    },
    modal: {
      caption: 'As on iOS',
      rows: [
        {
          label: <Os name="iOS" />,
          text: 'A sheet by default: it rises from the bottom, and the screen under it steps back. Full screen, detents or a popover on iPad with its settings.',
        },
        { label: <Os name="Android" />, text: 'Rises from the bottom and covers the screen.' },
        {
          label: 'Back',
          text: (
            <>
              A swipe down on an iOS sheet, the system back on Android, the close button,{' '}
              <code>dismiss()</code>.
            </>
          ),
        },
      ],
    },
    overlay: {
      rows: [
        {
          label: 'Both',
          text: 'Over everything, the modals too. The screens under it stay as they are, and keep getting the touches around it.',
        },
        {
          label: 'Stays',
          text: 'When the root changes: a call in progress stays on screen while the user signs out.',
        },
        {
          label: 'Back',
          text: (
            <>
              None of the platform: <code>dismissOverlay()</code>, or a button of the overlay
              itself.
            </>
          ),
        },
      ],
    },
    tab: {
      rows: [
        {
          label: <Os name="iOS" />,
          text: 'The tab bar of the system; on iPad also the bar at the top and the sidebar.',
        },
        { label: <Os name="Android" />, text: 'The bottom navigation of Material 3.' },
        {
          label: 'Each tab',
          text: 'Keeps its own stack: the user comes back to where they were in it.',
        },
      ],
    },
    column: {
      caption: 'iPad',
      rows: [
        {
          label: <Os name="iPadOS" />,
          text: 'The screen goes into the column beside the list, in place of what it showed.',
        },
        {
          label: 'One column',
          text: 'iPhone, a narrow window, Android: the screen is pushed over the list, and back returns to it. The same call works everywhere.',
        },
      ],
    },
    menu: {
      caption: 'Over the content',
      rows: [
        {
          label: <Os name="iOS" />,
          text: 'Slides in over the content, which dims; or moves the content aside, with the menu under it.',
        },
        {
          label: <Os name="Android" />,
          text: 'The drawer of the platform: it slides in over the content, which dims.',
        },
        {
          label: 'Back',
          text: (
            <>
              A swipe to its edge, a tap on the content, the system back on Android,{' '}
              <code>closeMenu()</code>.
            </>
          ),
        },
      ],
    },
    root: {
      rows: [
        {
          label: 'Both',
          text: 'The new root takes the window. The old screens unmount, the modals close, the overlays stay.',
        },
        { label: 'Back', text: 'None: the old root is gone. To come back, switch to it again.' },
      ],
    },
  };

function MockScreen({
  title,
  back,
  tint,
  className,
  children,
}: {
  title: string;
  back?: boolean;
  tint?: boolean;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <span className={clsx(styles.mScreen, tint && styles.mTint, className)}>
      <span className={styles.mBar}>
        {back && <span className={styles.mBack}>‹</span>}
        {title}
      </span>
      {children ?? (
        <span className={styles.mRows}>
          <i />
          <i />
          <i />
          <i />
        </span>
      )}
    </span>
  );
}

function TabBar({ moving }: { moving?: boolean }) {
  return (
    <span className={styles.mTabbar}>
      <span className={clsx(styles.mTabDot, moving && styles.mTabDotMoving)} />
      <i />
      <i />
      <i />
    </span>
  );
}

function Drawing({ kind }: { kind: DemoKind }) {
  switch (kind) {
    case 'push':
      return (
        <>
          <MockScreen title="Home" className={styles.pushBase} />
          <MockScreen title="Details" back tint className={styles.pushNext} />
        </>
      );
    case 'modal':
      return (
        <>
          <MockScreen title="Home" className={styles.modalBase} />
          <span className={styles.modalSheet}>
            <span className={styles.mGrabber} />
            <MockScreen title="Compose" tint className={styles.mInSheet} />
          </span>
        </>
      );
    case 'overlay':
      return (
        <>
          <MockScreen title="Home" />
          <span className={styles.overlayToast}>Saved</span>
        </>
      );
    case 'tab':
      return (
        <>
          <MockScreen title="Home" />
          <MockScreen title="Inbox" tint className={styles.tabNext} />
          <TabBar moving />
        </>
      );
    case 'column':
      return (
        <span className={styles.mSplit}>
          <span className={styles.mSide}>
            <span className={styles.mSideTitle}>Settings</span>
            <span className={styles.mSideHighlight} />
            <i />
            <i />
            <i />
            <i />
          </span>
          <span className={styles.mDetail}>
            <MockScreen title="Profile" />
            <MockScreen title="Privacy" tint className={styles.columnNext} />
          </span>
        </span>
      );
    case 'menu':
      return (
        <>
          <MockScreen title="Home" />
          <span className={styles.menuDim} />
          <span className={styles.menuDrawer}>
            <span className={styles.menuTitle}>Menu</span>
            <i />
            <i />
            <i />
            <i />
          </span>
        </>
      );
    case 'root':
      return (
        <>
          <MockScreen title="Sign in">
            <span className={styles.mForm}>
              <i />
              <i />
              <b />
            </span>
          </MockScreen>
          <span className={styles.rootNext}>
            <MockScreen title="Home" tint />
            <TabBar />
          </span>
        </>
      );
  }
}

/** One kind of move: a device that plays it in a loop, and what the platforms do and what takes the user back. */
export function MoveDemo({ kind }: { kind: DemoKind }) {
  const demo = DEMOS[kind];
  const wide = kind === 'column';
  return (
    <div
      className={clsx(
        styles.demo,
        styles[
          `move_${kind === 'push' ? 'stack' : kind === 'tab' ? 'tabs' : kind === 'column' ? 'columns' : kind}`
        ]
      )}
    >
      <div className={styles.demoDevice} aria-hidden="true">
        <span className={clsx(styles.device, wide && styles.deviceWide)}>
          <span className={styles.deviceScreen}>
            <Drawing kind={kind} />
          </span>
        </span>
        {demo.caption && <span className={styles.demoCaption}>{demo.caption}</span>}
      </div>
      <div className={styles.demoRows}>
        {demo.rows.map((row, i) => (
          <div key={i} className={styles.demoRow}>
            <span className={styles.demoLabel}>{row.label}</span>
            <span className={styles.demoText}>{row.text}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

type LayerValue = { text: string; swatch?: string } | null;

const LAYERS: { name: string; where: string; values: [LayerValue, LayerValue, LayerValue] }[] = [
  { name: 'Theme', where: 'theme', values: [null, null, { text: 'paper', swatch: '#efe4cc' }] },
  { name: 'App defaults', where: 'options', values: [null, { text: 'off' }, null] },
  { name: 'Group', where: 'groups.settings.options', values: [null, { text: 'on' }, null] },
  {
    name: 'The screen',
    where: 'defineScreen({ options })',
    values: [{ text: "'Profile'" }, null, null],
  },
  {
    name: 'The command',
    where: "push('Profile', { options })",
    values: [null, null, { text: 'navy', swatch: '#1e2a44' }],
  },
  {
    name: 'Its state',
    where: 'setOptions · useScreenOptions',
    values: [{ text: 'user.name' }, null, null],
  },
];

const LAYER_KEYS = ['title', 'largeTitle', 'bar color'];

/** Where the options of a screen come from: each layer over the ones above it, and what shows in the end. */
export function OptionLayers() {
  // The value that shows in each column: the one of the last layer that sets it.
  const winners = LAYER_KEYS.map((_, column) => {
    let last = -1;
    LAYERS.forEach((layer, row) => {
      if (layer.values[column]) last = row;
    });
    return last;
  });
  const cell = (value: LayerValue, won: boolean) =>
    value ? (
      <span className={clsx(styles.layerValue, won ? styles.layerWon : styles.layerLost)}>
        {value.swatch && (
          <span className={styles.layerSwatch} style={{ background: value.swatch }} />
        )}
        {value.text}
      </span>
    ) : (
      <span className={styles.layerEmpty}>·</span>
    );
  return (
    <div className={styles.layers}>
      <div className={clsx(styles.layerRow, styles.layerHead)}>
        <span>Layer</span>
        {LAYER_KEYS.map((key) => (
          <code key={key}>{key}</code>
        ))}
      </div>
      {LAYERS.map((layer, row) => (
        <div key={layer.name} className={styles.layerRow}>
          <span className={styles.layerName}>
            <span className={styles.layerStep}>{row + 1}</span>
            <span>
              <span className={styles.layerTitle}>{layer.name}</span>
              <code className={styles.layerWhere}>{layer.where}</code>
            </span>
          </span>
          {layer.values.map((value, column) => (
            <span key={column}>{cell(value, winners[column] === row)}</span>
          ))}
        </div>
      ))}
      <div className={clsx(styles.layerRow, styles.layerResult)}>
        <span className={styles.layerTitle}>On screen</span>
        {winners.map((row, column) => (
          <span key={column}>{cell(LAYERS[row].values[column], true)}</span>
        ))}
      </div>
    </div>
  );
}

const ROOT_KINDS: { kind: string; code: string; title: string; text: string }[] = [
  {
    kind: 'single',
    code: "'Home'",
    title: 'A screen',
    text: 'Becomes a stack with it, so it can push.',
  },
  {
    kind: 'stack',
    code: "Stack('Home', 'Details')",
    title: 'A stack',
    text: 'Starts with these screens; back goes down them.',
  },
  {
    kind: 'tabs',
    code: 'Tabs(Tab(…), Tab(…))',
    title: 'Tabs',
    text: 'Sections side by side, a stack each.',
  },
  {
    kind: 'menu',
    code: 'SideMenu({ left, center })',
    title: 'A side menu',
    text: 'A drawer beside the content.',
  },
  {
    kind: 'columns',
    code: 'SplitView({ primary, … })',
    title: 'A split view',
    text: 'A list and its item, on iPad.',
  },
];

/** What a root can be: the containers of the navigation, each with a small drawing and how it is written. */
export function RootKinds() {
  return (
    <div className={clsx(styles.moves, styles.rootKinds)}>
      {ROOT_KINDS.map((root) => (
        <div
          key={root.kind}
          className={clsx(styles.move, styles.rootKind, styles[`move_${root.kind}`])}
        >
          <span className={styles.moveArt} aria-hidden="true">
            <span className={styles.artStage}>
              <span className={styles.artBase} />
              <span className={styles.artMoving} />
              {root.kind === 'tabs' && (
                <span className={styles.artTabs}>
                  <i />
                  <i />
                  <i />
                </span>
              )}
            </span>
          </span>
          <span className={styles.moveTitle}>{root.title}</span>
          <code className={styles.moveCommand}>{root.code}</code>
          <span className={styles.moveBack}>{root.text}</span>
        </div>
      ))}
    </div>
  );
}

const SWITCH_LAYERS: {
  kind: 'overlay' | 'modal' | 'root';
  before: string;
  after: string;
  fate: string;
}[] = [
  { kind: 'overlay', before: 'Overlay · a call', after: 'Overlay · a call', fate: 'Stays' },
  { kind: 'modal', before: 'Modal · Compose', after: 'Closed', fate: 'Closes' },
  {
    kind: 'root',
    before: "Root · 'login'",
    after: "Root · 'main'",
    fate: 'Replaced: the old screens unmount',
  },
];

/** What a switch of the root does to each layer of the app: before, after, and what happened. */
export function RootSwitch() {
  return (
    <div className={styles.switch}>
      <div className={styles.switchSide}>
        <span className="eyebrow">Before</span>
        {SWITCH_LAYERS.map((layer) => (
          <span
            key={layer.kind}
            className={clsx(styles.switchLayer, styles[`switch_${layer.kind}`])}
          >
            {layer.before}
          </span>
        ))}
      </div>
      <div className={styles.switchArrow} aria-hidden="true">
        <code>{"switchTo('main')"}</code>
        <span>→</span>
      </div>
      <div className={styles.switchSide}>
        <span className="eyebrow">After</span>
        {SWITCH_LAYERS.map((layer) => (
          <span
            key={layer.kind}
            className={clsx(
              styles.switchLayer,
              styles[`switch_${layer.kind}`],
              layer.kind === 'modal' && styles.switchGone,
              layer.kind === 'root' && styles.switchNew
            )}
          >
            {layer.after}
          </span>
        ))}
      </div>
      <div className={styles.switchFates}>
        <span className="eyebrow">What happens</span>
        {SWITCH_LAYERS.map((layer) => (
          <span key={layer.kind} className={styles.switchFate}>
            {layer.fate}
          </span>
        ))}
      </div>
    </div>
  );
}

const ROOT_TRANSITIONS = ['platform', 'none', 'fade', 'slideUp', 'scale', 'slideFromEnd'];

/** The presets a new root comes in with, each played in a loop on a small device. */
export function RootTransitions() {
  return (
    <div className={styles.rootTransitions}>
      {ROOT_TRANSITIONS.map((preset) => (
        <div key={preset} className={styles.rootTransition}>
          <span
            className={clsx(styles.device, styles.deviceSmall, styles.move_root)}
            aria-hidden="true"
          >
            <span className={styles.deviceScreen}>
              <MockScreen title="Sign in">
                <span className={styles.mForm}>
                  <i />
                  <i />
                  <b />
                </span>
              </MockScreen>
              <span className={clsx(styles.rootIn, styles[`rootIn_${preset}`])}>
                <MockScreen title="Home" tint />
                <TabBar />
              </span>
            </span>
          </span>
          <code>{`'${preset}'`}</code>
          {preset === 'platform' && <span className={styles.rootDefault}>the default</span>}
        </div>
      ))}
    </div>
  );
}

const TIMELINE_COLUMNS = ['The tap', 'The move', 'In place', 'Right after'];

const TIMELINE_LANES: { name: string; kind: 'screen' | 'app'; marks: (string | null)[] }[] = [
  { name: 'Home', kind: 'screen', marks: [null, null, 'screenDidDisappear', null] },
  { name: 'Details', kind: 'screen', marks: ['screenWillAppear', null, 'screenDidAppear', null] },
  {
    name: 'The app',
    kind: 'app',
    marks: ["push('Details')", null, 'stateChanged', 'push() resolves'],
  },
];

/** The events of one push in time: what each screen and the app get, from the tap to the new state. */
export function EventTimeline() {
  return (
    <div className={styles.timeline}>
      <div className={clsx(styles.timelineRow, styles.timelineHead)}>
        <span />
        {TIMELINE_COLUMNS.map((column) => (
          <span key={column}>{column}</span>
        ))}
      </div>
      <div className={styles.timelineBody}>
        <span className={styles.timelineBand} aria-hidden="true">
          the push animation
        </span>
        {TIMELINE_LANES.map((lane) => (
          <div key={lane.name} className={styles.timelineRow}>
            <span className={styles.timelineLane}>{lane.name}</span>
            {lane.marks.map((mark, i) => (
              <span key={i} className={styles.timelineCell}>
                {mark && (
                  <code
                    className={clsx(
                      styles.timelineMark,
                      lane.kind === 'app' ? styles.markApp : styles.markScreen
                    )}
                  >
                    {mark}
                  </code>
                )}
              </span>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

const SCOPES: { code: string; title: string; text: string }[] = [
  {
    code: 'listeners',
    title: 'For the whole run',
    text: 'createNavigation: from the start, for as long as the app runs.',
  },
  {
    code: 'navigation.on',
    title: 'From anywhere',
    text: 'A store, a service: from the call until the function it returns is called.',
  },
  {
    code: 'useNavigationEvent',
    title: 'In a component',
    text: 'Any component: while it is mounted.',
  },
  {
    code: 'useScreenEvent',
    title: 'In a screen',
    text: 'The events of this screen only, while it is mounted.',
  },
];

/** Where to listen to the events: four scopes, each inside the one before. */
export function EventScopes() {
  const ring = (i: number): ReactNode => {
    const scope = SCOPES[i];
    if (!scope) return null;
    return (
      <div className={clsx(styles.scope, styles[`scope_${i}`])}>
        <span className={styles.scopeHead}>
          <code>{scope.code}</code>
          <span className={styles.scopeTitle}>{scope.title}</span>
        </span>
        <span className={styles.scopeText}>{scope.text}</span>
        {ring(i + 1)}
      </div>
    );
  };
  return <div className={styles.scopes}>{ring(0)}</div>;
}

const STACK_OPS: {
  code: string;
  text: string;
  before: string[];
  after: string[];
  gone: string[];
  fresh?: string[];
}[] = [
  {
    code: "push('Chat')",
    text: 'A screen over the top one.',
    before: ['Home', 'Inbox'],
    after: ['Home', 'Inbox', 'Chat'],
    gone: [],
    fresh: ['Chat'],
  },
  {
    code: 'pop()',
    text: 'Back one screen.',
    before: ['Home', 'Inbox', 'Chat'],
    after: ['Home', 'Inbox'],
    gone: ['Chat'],
  },
  {
    code: 'popTo(inbox)',
    text: 'Back to a screen, by its id.',
    before: ['Home', 'Inbox', 'Chat', 'Profile'],
    after: ['Home', 'Inbox'],
    gone: ['Chat', 'Profile'],
  },
  {
    code: 'popToRoot()',
    text: 'Back to the first screen.',
    before: ['Home', 'Inbox', 'Chat'],
    after: ['Home'],
    gone: ['Inbox', 'Chat'],
  },
  {
    code: "replace('Profile')",
    text: 'The history goes: Profile is alone in the stack, there is no back.',
    before: ['Home', 'Inbox', 'Chat'],
    after: ['Profile'],
    gone: ['Home', 'Inbox', 'Chat'],
    fresh: ['Profile'],
  },
  {
    code: "setStack('Inbox', 'Chat')",
    text: 'New screens for the whole stack: back goes down them.',
    before: ['Home', 'Profile'],
    after: ['Inbox', 'Chat'],
    gone: ['Home', 'Profile'],
    fresh: ['Inbox', 'Chat'],
  },
];

function Deck({
  screens,
  fresh = [],
  gone = [],
}: {
  screens: string[];
  fresh?: string[];
  gone?: string[];
}) {
  return (
    <span className={styles.deck}>
      {screens.map((screen, i) => (
        <span
          key={`${screen}-${i}`}
          className={clsx(
            styles.deckCard,
            fresh.includes(screen) && styles.deckFresh,
            gone.includes(screen) && styles.deckGone,
            i === screens.length - 1 && styles.deckTop
          )}
          style={{ zIndex: i }}
        >
          {screen}
        </span>
      ))}
    </span>
  );
}

/** The commands of a stack: the stack before each one and after it; the screen on top is the one the user sees. */
export function StackOps() {
  return (
    <div className={styles.ops}>
      {STACK_OPS.map((op) => (
        <div key={op.code} className={styles.op}>
          <span className={styles.opHead}>
            <code>{op.code}</code>
            <span className={styles.opText}>{op.text}</span>
          </span>
          <Deck screens={op.before} gone={op.gone} />
          <span className={styles.opArrow} aria-hidden="true">
            →
          </span>
          <Deck screens={op.after} fresh={op.fresh} />
        </div>
      ))}
    </div>
  );
}

const TAB_STACKS: { tab: string; screens: string[] }[] = [
  { tab: 'Home', screens: ['Home'] },
  { tab: 'Inbox', screens: ['Inbox', 'Chat', 'Profile'] },
  { tab: 'Search', screens: ['Search', 'Results'] },
];

/** Tabs with a stack each: the selected tab shows the top of its stack, the others keep theirs. */
export function TabStacks() {
  const selected = 1;
  return (
    <div className={styles.tabStacks}>
      <div className={styles.tabStacksDecks}>
        {TAB_STACKS.map((tab, i) => (
          <div
            key={tab.tab}
            className={clsx(styles.tabStack, i === selected && styles.tabStackSelected)}
          >
            <Deck
              screens={tab.screens}
              fresh={i === selected ? [tab.screens[tab.screens.length - 1]] : []}
            />
            <span className={styles.tabStackNote}>
              {i === selected
                ? 'Shows Profile, the top of its stack'
                : `Keeps ${tab.screens[tab.screens.length - 1]}`}
            </span>
          </div>
        ))}
      </div>
      <div className={styles.tabStacksBar}>
        {TAB_STACKS.map((tab, i) => (
          <span
            key={tab.tab}
            className={clsx(styles.tabStacksItem, i === selected && styles.tabStacksItemSelected)}
          >
            <i />
            {tab.tab}
          </span>
        ))}
      </div>
    </div>
  );
}

const MODAL_STYLES: {
  style: string;
  title: string;
  text: string;
  os: 'iOS' | 'iPadOS' | 'Android' | 'both';
  wide?: boolean;
}[] = [
  {
    style: 'pageSheet',
    title: 'A sheet',
    text: 'The default on iOS. The screen under it steps back and still shows.',
    os: 'iOS',
  },
  {
    style: 'fullScreen',
    title: 'Full screen',
    text: 'Covers everything. The default on Android.',
    os: 'both',
  },
  {
    style: 'overFullScreen',
    title: 'Over the screen',
    text: 'Full screen on a transparent background: the screen under it is seen.',
    os: 'both',
  },
  {
    style: 'formSheet',
    title: 'A form',
    text: 'A card in the middle, sized by contentSize. On iPhone, a sheet.',
    os: 'iPadOS',
    wide: true,
  },
  {
    style: 'popover',
    title: 'A popover',
    text: 'A bubble pointing at a button or a view. On iPhone, a sheet.',
    os: 'iPadOS',
    wide: true,
  },
];

/** How a modal can show: a small drawing of each presentation style, where it works, and what it is for. */
export function ModalStyles() {
  return (
    <div className={styles.modalStyles}>
      {MODAL_STYLES.map((item) => (
        <div key={item.style} className={clsx(styles.modalStyle, styles.move_modal)}>
          <span
            className={clsx(styles.device, styles.deviceSmall, item.wide && styles.deviceTablet)}
            aria-hidden="true"
          >
            <span className={styles.deviceScreen}>
              <MockScreen
                title="Home"
                className={clsx(item.style === 'pageSheet' && styles.msBaseBack)}
              />
              <span className={clsx(styles.msModal, styles[`ms_${item.style}`])}>
                {item.style === 'popover' && <span className={styles.msArrow} />}
                <MockScreen title="Compose" tint />
              </span>
            </span>
          </span>
          <code>{`'${item.style}'`}</code>
          <span className={styles.modalStyleTitle}>
            {item.title}
            {item.os === 'both' ? null : <Os name={item.os} />}
          </span>
          <span className={styles.modalStyleText}>{item.text}</span>
        </div>
      ))}
    </div>
  );
}

const DETENTS: { name: string; top: number }[] = [
  { name: "'large'", top: 12 },
  { name: "'medium'", top: 50 },
  { name: '200', top: 74 },
];

/** The stops of a sheet: the device with a sheet at medium, and the lines of the other stops. */
export function SheetDetents() {
  return (
    <div className={styles.detents}>
      <span className={clsx(styles.device, styles.move_modal)} aria-hidden="true">
        <span className={styles.deviceScreen}>
          <MockScreen title="Map" />
          <span className={styles.detentSheet}>
            <span className={styles.mGrabber} />
            <MockScreen title="Places" tint className={styles.mInSheet} />
          </span>
          {DETENTS.map((detent) => (
            <span key={detent.name} className={styles.detentLine} style={{ top: `${detent.top}%` }}>
              <span className={styles.detentTag}>{detent.name.replace(/'/g, '')}</span>
            </span>
          ))}
        </span>
      </span>
      <div className={styles.detentList}>
        {DETENTS.map((detent, i) => (
          <div key={detent.name} className={styles.detentRow}>
            <code>{detent.name}</code>
            <span>
              {i === 0
                ? 'The full height, as a page sheet.'
                : i === 1
                  ? 'About half the screen. Here the sheet rests at it.'
                  : 'A height in points; the content height with ’content’.'}
            </span>
          </div>
        ))}
        <div className={styles.detentRow}>
          <code>largestUndimmedDetent</code>
          <span>
            Up to this stop the screen under the sheet stays bright and takes touches, as the map
            under the sheet of a maps app.
          </span>
        </div>
      </div>
    </div>
  );
}

const OVERLAY_KINDS: { kind: string; title: string; text: string }[] = [
  {
    kind: 'toast',
    title: 'A toast',
    text: 'A strip that goes by itself. The app under it keeps taking touches.',
  },
  {
    kind: 'dialog',
    title: 'A dialog',
    text: 'Its dimmed backdrop takes every touch until the user answers.',
  },
  {
    kind: 'player',
    title: 'A mini player',
    text: 'Stays on top while the user pushes, switches tabs, opens modals.',
  },
  {
    kind: 'modal',
    title: 'Over a modal',
    text: 'Overlays are over the modals too, and stay when the root changes.',
  },
];

/** What overlays are for: a small device for each kind. */
export function OverlayKinds() {
  return (
    <div className={clsx(styles.modalStyles, styles.overlayKinds)}>
      {OVERLAY_KINDS.map((item) => (
        <div key={item.kind} className={clsx(styles.modalStyle, styles.move_overlay)}>
          <span className={clsx(styles.device, styles.deviceSmall)} aria-hidden="true">
            <span className={styles.deviceScreen}>
              <MockScreen title="Home" />
              {(item.kind === 'player' || item.kind === 'toast') && <TabBar />}
              {item.kind === 'modal' && (
                <span className={clsx(styles.msModal, styles.ms_pageSheet, styles.move_modal)}>
                  <MockScreen title="Compose" tint />
                </span>
              )}
              <span className={clsx(styles.ov, styles[`ov_${item.kind}`])}>
                {item.kind === 'dialog' && (
                  <span className={styles.ovCard}>
                    <i />
                    <i />
                    <b />
                  </span>
                )}
              </span>
            </span>
          </span>
          <span className={styles.modalStyleTitle}>{item.title}</span>
          <span className={styles.modalStyleText}>{item.text}</span>
        </div>
      ))}
    </div>
  );
}

const SPLIT_COLUMNS: { key: string; title: string; text: string; flex: number }[] = [
  { key: 'primary', title: 'primary', text: 'The sidebar: sections, folders.', flex: 1 },
  {
    key: 'supplementary',
    title: 'supplementary',
    text: 'The middle: the items of a section.',
    flex: 1.1,
  },
  { key: 'secondary', title: 'secondary', text: 'The item itself.', flex: 2 },
  { key: 'inspector', title: 'inspector', text: 'iPadOS 26: facts about the item.', flex: 0.9 },
];

const DISPLAY_MODES: { mode: string; text: string }[] = [
  { mode: 'oneBesideSecondary', text: 'Side by side' },
  { mode: 'oneOverSecondary', text: 'The sidebar over the item' },
  { mode: 'secondaryOnly', text: 'The item alone' },
];

/** The columns of a split view on iPad, the one stack of a phone, and how the sidebar can show. */
export function SplitColumns() {
  return (
    <div className={styles.split}>
      <div className={styles.splitMain}>
        <span
          className={clsx(
            styles.device,
            styles.deviceWide,
            styles.splitDevice,
            styles.move_columns
          )}
          aria-hidden="true"
        >
          <span className={styles.deviceScreen}>
            <span className={styles.splitCols}>
              {SPLIT_COLUMNS.map((column) => (
                <span
                  key={column.key}
                  className={clsx(styles.splitCol, styles[`splitCol_${column.key}`])}
                  style={{ flex: column.flex }}
                >
                  <span className={styles.splitColName}>{column.title}</span>
                  <i />
                  <i />
                  <i />
                </span>
              ))}
            </span>
          </span>
        </span>
        <div className={styles.splitLegend}>
          {SPLIT_COLUMNS.map((column) => (
            <div key={column.key} className={styles.splitLegendRow}>
              <code>{column.title}</code>
              <span>{column.text}</span>
            </div>
          ))}
          <div className={styles.splitLegendRow}>
            <code>compact</code>
            <span>iPhone and a narrow window: one stack instead of the columns.</span>
          </div>
        </div>
      </div>
      <div className={styles.splitModes}>
        {DISPLAY_MODES.map((item) => (
          <div key={item.mode} className={styles.splitMode}>
            <span
              className={clsx(styles.splitMini, styles[`splitMini_${item.mode}`])}
              aria-hidden="true"
            >
              <span className={styles.splitMiniSide} />
              <span className={styles.splitMiniDetail} />
            </span>
            <code>{`'${item.mode}'`}</code>
            <span className={styles.splitModeText}>{item.text}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

const BAR_PARTS: { title: string; text: string; to: string; os?: 'iOS' }[] = [
  {
    title: 'The back button',
    text: 'Shows by itself on every screen but the first.',
    to: '/docs/screen-ui/back-and-gestures',
  },
  { title: 'The title and subtitle', text: 'Text, or a component of your own.', to: '#title' },
  {
    title: 'The buttons',
    text: 'At the leading and trailing edges, with menus.',
    to: '/docs/screen-ui/bar-buttons',
  },
  {
    title: 'The large title',
    text: 'Big at the top, small in the bar on scroll.',
    to: '#large-title',
    os: 'iOS',
  },
  { title: 'The search field', text: 'In the bar, or under it.', to: '/docs/screen-ui/search' },
  {
    title: 'The background and the border',
    text: 'Glass, a color, or a component.',
    to: '#background',
  },
];

/** The top bar with its parts numbered, and where each part is described. */
export function BarAnatomy() {
  return (
    <div className={styles.bar}>
      <div className={styles.barMock} aria-hidden="true">
        <span className={styles.barRow}>
          <span className={styles.barBack}>
            ‹ Inbox<span className={clsx(styles.badge, styles.barBadge)}>1</span>
          </span>
          <span className={styles.barTitle}>
            <b>Chat</b>
            <small>online</small>
            <span className={clsx(styles.badge, styles.barBadge)}>2</span>
          </span>
          <span className={styles.barButtons}>
            <i />
            <i />
            <span className={clsx(styles.badge, styles.barBadge)}>3</span>
          </span>
        </span>
        <span className={styles.barLarge}>
          Messages<span className={clsx(styles.badge, styles.barBadge)}>4</span>
        </span>
        <span className={styles.barSearch}>
          <Icon name="search" size={13} /> Search
          <span className={clsx(styles.badge, styles.barBadge)}>5</span>
        </span>
        <span className={styles.barBorder}>
          <span className={clsx(styles.badge, styles.barBadge)}>6</span>
        </span>
      </div>
      <div className={styles.barParts}>
        {BAR_PARTS.map((part, i) => (
          <Link key={part.title} to={part.to} className={styles.barPart}>
            <span className={styles.barPartNumber}>{i + 1}</span>
            <span className={styles.barPartBody}>
              <span className={styles.barPartTitle}>
                {part.title}
                {part.os && <Os name={part.os} />}
              </span>
              <span className={styles.barPartText}>{part.text}</span>
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}

/** A large title that collapses into the bar as the rows scroll, and comes back at the top. */
export function LargeTitleDemo() {
  return (
    <div className={clsx(styles.demo, styles.move_stack, styles.largeDemo)}>
      <div className={styles.demoDevice} aria-hidden="true">
        <span className={styles.device}>
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.ltBar}>
                <span className={styles.ltSmall}>Inbox</span>
              </span>
              <span className={styles.ltScroll}>
                <span className={styles.ltLarge}>Inbox</span>
                <span className={styles.mRows}>
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                  <i />
                </span>
              </span>
            </span>
          </span>
        </span>
        <span className={styles.demoCaption}>iOS</span>
      </div>
      <div className={styles.demoRows}>
        <div className={styles.demoRow}>
          <span className={styles.demoLabel}>At the top</span>
          <span className={styles.demoText}>The title is big, under the row of the bar.</span>
        </div>
        <div className={styles.demoRow}>
          <span className={styles.demoLabel}>On scroll</span>
          <span className={styles.demoText}>
            It goes into the bar as a small title, and comes back when the content returns to the
            top.
          </span>
        </div>
        <div className={styles.demoRow}>
          <span className={styles.demoLabel}>Stays big</span>
          <span className={styles.demoText}>
            With <code>collapseOnScroll: false</code> it doesn't collapse.
          </span>
        </div>
      </div>
    </div>
  );
}

type IconName =
  | 'star'
  | 'share'
  | 'mail'
  | 'heart'
  | 'chevron'
  | 'search'
  | 'back'
  | 'plus'
  | 'compose'
  | 'filter';

/** Line icons in one style, as the system symbols are drawn: 24 × 24, a 2 px stroke, round ends. */
function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  };
  switch (name) {
    case 'star':
      return (
        <svg {...common}>
          <path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z" />
        </svg>
      );
    case 'share':
      return (
        <svg {...common}>
          <path d="M12 14V3.5M8 7.5l4-4 4 4" />
          <path d="M8 10.5H6.5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2H16" />
        </svg>
      );
    case 'mail':
      return (
        <svg {...common}>
          <rect x="3.5" y="5.5" width="17" height="13" rx="2.5" />
          <path d="M4 7.5l8 5.5 8-5.5" />
        </svg>
      );
    case 'heart':
      return (
        <svg {...common} fill="currentColor">
          <path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z" />
        </svg>
      );
    case 'chevron':
      return (
        <svg {...common} width={size * 0.7} height={size * 0.7}>
          <path d="M6 9.5l6 6 6-6" />
        </svg>
      );
    case 'back':
      return (
        <svg {...common} strokeWidth={2.4}>
          <path d="M15 4.5L7.5 12l7.5 7.5" />
        </svg>
      );
    case 'plus':
      return (
        <svg {...common}>
          <path d="M12 5v14M5 12h14" />
        </svg>
      );
    case 'compose':
      return (
        <svg {...common}>
          <path d="M11 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V13" />
          <path d="M17.5 3.5l3 3-8 8-3.5.5.5-3.5z" />
        </svg>
      );
    case 'filter':
      return (
        <svg {...common}>
          <path d="M4 7h16M7 12h10M10 17h4" />
        </svg>
      );
    case 'search':
      return (
        <svg {...common}>
          <circle cx="11" cy="11" r="6.5" />
          <path d="M20 20l-4.4-4.4" />
        </svg>
      );
  }
}

const BUTTON_KINDS: { kind: string; label: ReactNode; code: string }[] = [
  { kind: 'text', label: 'Edit', code: "text: 'Edit'" },
  { kind: 'icon', label: <Icon name="star" />, code: 'icon' },
  {
    kind: 'group',
    label: (
      <>
        <Icon name="star" />
        <Icon name="share" />
      </>
    ),
    code: "group: 'share'",
  },
  { kind: 'prominent', label: 'Save', code: "style: 'prominent'" },
  { kind: 'badge', label: <Icon name="mail" />, code: 'badge: 3' },
  { kind: 'toggle', label: <Icon name="heart" />, code: 'toggle' },
  {
    kind: 'menu',
    label: (
      <>
        Sort <Icon name="chevron" />
      </>
    ),
    code: 'menu',
  },
];

/** The kinds of bar buttons, as iOS 26 draws them: a capsule each, with what makes it. */
export function ButtonKinds() {
  return (
    <div className={styles.buttonKinds}>
      {BUTTON_KINDS.map((item) => (
        <div key={item.kind} className={styles.buttonKind}>
          <span className={clsx(styles.capsule, styles[`capsule_${item.kind}`])} aria-hidden="true">
            {item.label}
            {item.kind === 'badge' && <span className={styles.capsuleBadge}>3</span>}
          </span>
          <code>{item.code}</code>
        </div>
      ))}
    </div>
  );
}

const BACK_MODES: { mode: string; label: string; text: string }[] = [
  {
    mode: "'default'",
    label: 'Inbox',
    text: 'The title of the screen under it; shorter when the bar is full.',
  },
  { mode: "'generic'", label: 'Back', text: '“Back”, in the language of the user.' },
  { mode: "'minimal'", label: '', text: 'The arrow alone. On iOS 26, the default.' },
];

/** The back button of iOS in its display modes: the arrow with the title under it, “Back”, or the arrow alone. */
export function BackButtonModes() {
  return (
    <div className={styles.backModes}>
      {BACK_MODES.map((item) => (
        <div key={item.mode} className={styles.backMode}>
          <span className={styles.backBar} aria-hidden="true">
            <span className={styles.backButton}>
              <Icon name="back" size={16} />
              {item.label}
            </span>
            <span className={styles.backTitle}>Chat</span>
          </span>
          <code>{item.mode}</code>
          <span className={styles.backText}>{item.text}</span>
        </div>
      ))}
    </div>
  );
}

const SEARCH_PLACEMENTS: { kind: string; code: string; text: ReactNode }[] = [
  { kind: 'stacked', code: "'stacked'", text: 'A field under the title. Both platforms.' },
  {
    kind: 'bottom',
    code: "'automatic'",
    text: <>On iPhone with iOS 26: the field at the bottom, in reach of the thumb.</>,
  },
  {
    kind: 'button',
    code: "'integrated'",
    text: <>Android: a magnifier in the bar, and the field opens in the bar.</>,
  },
  {
    kind: 'toolbar',
    code: "systemItem: 'searchField'",
    text: <>iOS 26: the field in the toolbar, between its buttons.</>,
  },
];

function SearchField({ small }: { small?: boolean }) {
  return (
    <span className={clsx(styles.sField, small && styles.sFieldSmall)}>
      <Icon name="search" size={small ? 9 : 11} />
      {!small && 'Search'}
    </span>
  );
}

/** Where the search field can sit: a small device for each placement. */
export function SearchPlacements() {
  return (
    <div className={clsx(styles.modalStyles, styles.searchPlacements)}>
      {SEARCH_PLACEMENTS.map((item) => (
        <div key={item.kind} className={clsx(styles.modalStyle, styles.move_stack)}>
          <span className={clsx(styles.device, styles.deviceSmall)} aria-hidden="true">
            <span className={styles.deviceScreen}>
              <span className={styles.mScreen}>
                <span className={styles.mBar}>
                  Inbox
                  {item.kind === 'button' && (
                    <span className={styles.sMagnifier}>
                      <Icon name="search" size={11} />
                    </span>
                  )}
                </span>
                {item.kind === 'stacked' && (
                  <span className={styles.sUnder}>
                    <SearchField />
                  </span>
                )}
                <span className={styles.mRows}>
                  <i />
                  <i />
                  <i />
                  <i />
                </span>
                {item.kind === 'bottom' && (
                  <span className={styles.sBottom}>
                    <SearchField />
                  </span>
                )}
                {item.kind === 'toolbar' && (
                  <span className={styles.sToolbar}>
                    <span className={styles.sToolButton} />
                    <SearchField />
                    <span className={styles.sToolButton} />
                  </span>
                )}
              </span>
            </span>
          </span>
          <code>{item.code}</code>
          <span className={styles.modalStyleText}>{item.text}</span>
        </div>
      ))}
    </div>
  );
}

/** The actions at the bottom of a screen, each platform its own way: the toolbar of iOS, the FAB of Android. */
export function ToolbarAndFab() {
  return (
    <div className={styles.tf}>
      <div className={styles.tfSide}>
        <span className={clsx(styles.device, styles.move_stack)} aria-hidden="true">
          <span className={styles.deviceScreen}>
            <MockScreen title="Mail" />
            <span className={styles.tfToolbar}>
              <span className={styles.tfButton}>
                <Icon name="filter" size={12} />
              </span>
              <span className={styles.tfSearch}>
                <Icon name="search" size={10} /> Search
              </span>
              <span className={styles.tfButton}>
                <Icon name="compose" size={12} />
              </span>
            </span>
          </span>
        </span>
        <span className={styles.tfLabel}>
          <Os name="iOS" /> The toolbar
        </span>
        <span className={styles.tfText}>
          A row of buttons at the bottom of the stack, on glass capsules on iOS 26.
        </span>
      </div>
      <div className={styles.tfSide}>
        <span className={clsx(styles.device, styles.move_stack)} aria-hidden="true">
          <span className={styles.deviceScreen}>
            <MockScreen title="Mail" />
            <span className={styles.tfActions}>
              <span className={styles.tfMini}>
                <Icon name="compose" size={11} />
              </span>
              <span className={styles.tfMini}>
                <Icon name="mail" size={11} />
              </span>
            </span>
            <span className={styles.tfFab}>
              <Icon name="plus" size={18} />
            </span>
          </span>
        </span>
        <span className={styles.tfLabel}>
          <Os name="Android" /> The floating action button
        </span>
        <span className={styles.tfText}>
          The main action of the screen, over the content; with actions, a press opens them above
          it.
        </span>
      </div>
    </div>
  );
}

const SYSTEM_PARTS: { title: string; text: string; to: string }[] = [
  {
    title: 'The status bar',
    text: 'The time and the battery. Light or dark text, hidden.',
    to: '#status-bar',
  },
  {
    title: 'The cutout',
    text: 'The camera notch or island; the content keeps out of it.',
    to: '#safe-area',
  },
  {
    title: 'The safe area',
    text: 'Where the content is not covered by the bars and the system.',
    to: '#safe-area',
  },
  {
    title: 'The home indicator and the navigation bar',
    text: 'The bottom of the system: a handle on iOS, a handle or buttons on Android.',
    to: '#bottom',
  },
];

/** The parts of the system around a screen, numbered, and where each one is described. */
export function SystemAnatomy() {
  return (
    <div className={styles.bar}>
      <div className={styles.sysPhone} aria-hidden="true">
        <span className={clsx(styles.deviceScreen, styles.sysScreen)}>
          <span className={styles.sysStatus}>
            <span>9:41</span>
            <span className={styles.sysIsland} />
            <span className={styles.sysBattery} />
            <span className={clsx(styles.badge, styles.sysBadge, styles.sysBadge1)}>1</span>
            <span className={clsx(styles.badge, styles.sysBadge, styles.sysBadge2)}>2</span>
          </span>
          <span className={styles.sysSafe}>
            <span className={styles.mRows}>
              <i />
              <i />
              <i />
              <i />
            </span>
            <span className={clsx(styles.badge, styles.sysBadge, styles.sysBadge3)}>3</span>
          </span>
          <span className={styles.sysHome}>
            <span />
            <span className={clsx(styles.badge, styles.sysBadge, styles.sysBadge4)}>4</span>
          </span>
        </span>
      </div>
      <div className={styles.barParts}>
        {SYSTEM_PARTS.map((part, i) => (
          <Link key={part.title} to={part.to} className={styles.barPart}>
            <span className={styles.barPartNumber}>{i + 1}</span>
            <span className={styles.barPartBody}>
              <span className={styles.barPartTitle}>{part.title}</span>
              <span className={styles.barPartText}>{part.text}</span>
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}

const THEME_COLORS: { key: string; color: string; text: string }[] = [
  {
    key: 'primary',
    color: '#E76F51',
    text: 'The buttons, the back button, the selected tab, the cursor of the search.',
  },
  {
    key: 'bar',
    color: '#1E2A44',
    text: 'The top bar and the tab bar. Left out: the glass of iOS 26.',
  },
  { key: 'text', color: '#F3E9D2', text: 'The titles.' },
  { key: 'background', color: '#F3E9D2', text: 'Behind every screen.' },
  { key: 'border', color: '#E9B949', text: 'The lines under the top bar and over the tab bar.' },
  { key: 'badge', color: '#D1495B', text: 'The badges of the tabs.' },
];

/** What each color of the theme paints: a screen in the theme's colors, and the colors with where they go. */
export function ThemeMap() {
  return (
    <div className={styles.bar}>
      <span className={styles.themePhone} aria-hidden="true">
        <span className={styles.themeScreen}>
          <span className={styles.themeTop}>
            <span className={styles.themeBack}>
              <Icon name="back" size={12} /> Inbox
            </span>
            <span className={styles.themeTitle}>Chat</span>
            <span className={styles.themeBtn}>
              <Icon name="compose" size={12} />
            </span>
          </span>
          <span className={styles.themeBody}>
            <i />
            <i />
            <i />
          </span>
          <span className={styles.themeTabs}>
            <span className={styles.themeTabOn}>
              <Icon name="mail" size={13} />
            </span>
            <span className={styles.themeTab}>
              <Icon name="star" size={13} />
              <b>3</b>
            </span>
            <span className={styles.themeTab}>
              <Icon name="search" size={13} />
            </span>
          </span>
        </span>
      </span>
      <div className={styles.barParts}>
        {THEME_COLORS.map((item) => (
          <div key={item.key} className={styles.barPart}>
            <span className={styles.themeSwatch} style={{ background: item.color }} />
            <span className={styles.barPartBody}>
              <code className={styles.themeKey}>{item.key}</code>
              <span className={styles.barPartText}>{item.text}</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** The two ways screens render: a React root each, or one tree with every screen in it. */
export function TreeModes() {
  const screens = ['Home', 'Chat', 'Profile'];
  return (
    <div className={styles.trees}>
      <div className={styles.treeSide}>
        <span className="eyebrow">A root per screen · the default</span>
        <div className={styles.treeRoots}>
          {screens.map((name) => (
            <span key={name} className={styles.treeRoot}>
              <span className={styles.treeProvider}>screenProvider</span>
              <span className={styles.treeScreen}>{name}</span>
            </span>
          ))}
        </div>
        <span className={styles.treeText}>
          Each screen is a React tree of its own, with its own copy of a provider.
        </span>
      </div>
      <div className={styles.treeSide}>
        <span className="eyebrow">One tree · with appProvider</span>
        <div className={styles.treeOne}>
          <span className={styles.treeProvider}>appProvider</span>
          <span className={styles.treeRoots}>
            {screens.map((name) => (
              <span key={name} className={clsx(styles.treeScreen, styles.treeSlot)}>
                {name}
              </span>
            ))}
          </span>
        </div>
        <span className={styles.treeText}>
          Every screen renders inside one provider: what it holds is shared, and lives as long as
          the app.
        </span>
      </div>
    </div>
  );
}

/** What a screen shows when its render fails, and while it loads: the bar stays, the content gives way. */
export function FallbackStates() {
  return (
    <div className={styles.tf}>
      <div className={styles.tfSide}>
        <span className={clsx(styles.device, styles.move_modal)} aria-hidden="true">
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.mBar}>
                <span className={styles.mBack}>‹</span>
                Details
              </span>
              <span className={styles.fbError}>
                <span className={styles.fbIcon}>!</span>
                <b>This screen failed</b>
                <span className={styles.fbButton}>Try again</span>
              </span>
            </span>
          </span>
        </span>
        <span className={styles.tfLabel}>errorFallback</span>
        <span className={styles.tfText}>
          The bar stays, so back works. The other screens go on.
        </span>
      </div>
      <div className={styles.tfSide}>
        <span className={clsx(styles.device, styles.move_stack)} aria-hidden="true">
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.mBar}>
                <span className={styles.mBack}>‹</span>
                Details
              </span>
              <span className={styles.fbLoading}>
                <span className={styles.fbSpinner} />
              </span>
            </span>
          </span>
        </span>
        <span className={styles.tfLabel}>loadingFallback</span>
        <span className={styles.tfText}>
          The bar is there at once; the content comes when its data does.
        </span>
      </div>
    </div>
  );
}

const PUSH_PRESETS: { preset: string; css: string; note?: string }[] = [
  { preset: 'platform', css: 'slideFromEnd', note: 'the default' },
  { preset: 'fade', css: 'fade' },
  { preset: 'slideUp', css: 'slideUp' },
  { preset: 'scale', css: 'scale' },
  { preset: 'none', css: 'none' },
];

/** The presets of a push, each played in a loop on a small device. */
export function PushPresets() {
  return (
    <div className={styles.rootTransitions}>
      {PUSH_PRESETS.map((item) => (
        <div key={item.preset} className={styles.rootTransition}>
          <span
            className={clsx(styles.device, styles.deviceSmall, styles.move_stack)}
            aria-hidden="true"
          >
            <span className={styles.deviceScreen}>
              <MockScreen title="Inbox" />
              <span className={clsx(styles.rootIn, styles[`rootIn_${item.css}`])}>
                <MockScreen title="Chat" back tint />
              </span>
            </span>
          </span>
          <code>{`'${item.preset}'`}</code>
          {item.note && <span className={styles.rootDefault}>{item.note}</span>}
        </div>
      ))}
    </div>
  );
}

/** Transitions out of a view: shared elements, the zoom of iOS 18 and the preview, each on a device in a loop. */
export function ViewTransitions() {
  return (
    <div className={clsx(styles.modalStyles, styles.viewTransitions)}>
      <div className={clsx(styles.modalStyle, styles.move_stack)}>
        <span
          className={clsx(styles.device, styles.deviceSmall, styles.vtDevice)}
          aria-hidden="true"
        >
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.mBar}>People</span>
              <span className={styles.shList}>
                <i />
                <i />
                <i />
              </span>
              <span className={styles.shProfile}>
                <i />
                <i />
              </span>
            </span>
            <span className={styles.shAvatar} />
            <span className={styles.shName}>Name</span>
          </span>
        </span>
        <span className={styles.modalStyleTitle}>Shared elements</span>
        <span className={styles.modalStyleText}>
          The avatar and the name move from the row into the profile.
        </span>
      </div>
      <div className={clsx(styles.modalStyle, styles.move_stack)}>
        <span
          className={clsx(styles.device, styles.deviceSmall, styles.vtDevice)}
          aria-hidden="true"
        >
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.mBar}>Photos</span>
              <span className={styles.zGrid}>
                <i />
                <i />
                <i />
                <i />
              </span>
            </span>
            <span className={styles.zTile} />
          </span>
        </span>
        <span className={styles.modalStyleTitle}>
          Zoom <Os name="iOS" />
        </span>
        <span className={styles.modalStyleText}>
          iOS 18+: the photo opens out of its tile, and shrinks back into it.
        </span>
      </div>
      <div className={clsx(styles.modalStyle, styles.move_stack)}>
        <span
          className={clsx(styles.device, styles.deviceSmall, styles.vtDevice)}
          aria-hidden="true"
        >
          <span className={styles.deviceScreen}>
            <span className={styles.mScreen}>
              <span className={styles.mBar}>Photos</span>
              <span className={styles.zGrid}>
                <i />
                <i />
                <i />
                <i />
              </span>
            </span>
            <span className={styles.pvBlur} />
            <span className={styles.pvCard} />
            <span className={styles.pvMenu}>
              <span>Share</span>
              <span>Delete</span>
            </span>
          </span>
        </span>
        <span className={styles.modalStyleTitle}>
          Preview <Os name="iOS" />
        </span>
        <span className={styles.modalStyleText}>
          A long press shows the screen with a menu; a tap opens it.
        </span>
      </div>
    </div>
  );
}

/** How a native screen talks to the app: props go to it, function calls and commands come back. */
export function NativeBridge() {
  return (
    <div className={styles.nb}>
      <div className={clsx(styles.nbSide, styles.nbJs)}>
        <span className="eyebrow">JavaScript · the app</span>
        <span className={styles.nbItem}>
          <code>{"nativeScreen('Settings')"}</code>
          <span>
            Its name, its props and options, in <code>screens</code>.
          </span>
        </span>
        <span className={styles.nbItem}>
          <code>useProps</code>
          <span>A hook that gives it data of the app and functions, while it is open.</span>
        </span>
        <span className={styles.nbItem}>
          <code>{"push('Settings')"}</code>
          <span>Commands open it like any screen.</span>
        </span>
      </div>
      <div className={styles.nbArrows} aria-hidden="true">
        <span className={styles.nbArrowRight}>props →</span>
        <span className={styles.nbArrowLeft}>← function calls</span>
        <span className={styles.nbArrowLeft}>← commands</span>
      </div>
      <div className={clsx(styles.nbSide, styles.nbNative)}>
        <span className="eyebrow">Native · SwiftUI, Compose</span>
        <span className={styles.nbItem}>
          <code>screen.props</code>
          <span>Typed, and drawn again when they change.</span>
        </span>
        <span className={styles.nbItem}>
          <code>screen.props.onChange(on)</code>
          <span>Calls a function of the app, and awaits its result.</span>
        </span>
        <span className={styles.nbItem}>
          <code>screen.navigation.pop()</code>
          <span>
            Moves the app, as <code>useNavigation()</code> does.
          </span>
        </span>
      </div>
    </div>
  );
}
