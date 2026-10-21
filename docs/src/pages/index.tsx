import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import clsx from 'clsx';
import React, { useEffect, useMemo, useState } from 'react';
import styles from './index.module.css';

// The docs home of the brand canvas (page Docs, board DocsHome), element for element.

const RULES: [string, string, string][] = [
  [
    'The platform leads',
    'Native stacks, tabs, sheets and gestures rather than a web-style router. A URL is one entry point among many.',
    '#E76F51',
  ],
  [
    'Native is the source of truth',
    'Navigation state, screen lifecycle, safe areas, keyboard and theme come from the OS.',
    '#E9B949',
  ],
  [
    'App lifecycle is first-class',
    'Cold start, splash, session restore, background and process death are explicit steps.',
    '#2A9D8F',
  ],
  [
    'New OS features, fast',
    'Support for new iOS and Android releases lands with the betas.',
    '#D1495B',
  ],
  [
    'Customization, still native',
    'Options on system components first, your own native components inside native containers next, custom JS UI last.',
    '#5FA8D3',
  ],
  [
    'Performance is a requirement',
    'Transitions and gestures run natively; JS stays off the critical path, and CI measures it.',
    '#9C89B8',
  ],
  [
    'React context still works',
    'Every screen is a native container, and your providers still reach it: createNavigation takes providers for the whole app or for every screen.',
    '#F4A261',
  ],
];

// The same field of stars on every load: a seeded generator, as on the board.
function makeStars() {
  let seed = 17;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed - 1) / 2147483646;
  };
  return Array.from({ length: 60 }, () => ({
    x: (rnd() * 100).toFixed(2),
    y: (rnd() * 92).toFixed(2),
    r: (1 + rnd() * 2).toFixed(1),
    d: (rnd() * 5).toFixed(2),
  }));
}

// A launch every 14 seconds: the count from T-10, the arm swings away at T-02, lift-off at zero.
function useLaunch() {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined;
    const timer = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);
  const phase = tick % 14;
  const launched = phase >= 10;
  return {
    label: launched ? 'LIFT-OFF' : `T-${String(Math.max(0, 10 - phase)).padStart(2, '0')}`,
    launched,
    armOff: phase >= 8,
  };
}

const STROKE = { stroke: '#101828', strokeWidth: 3, strokeLinejoin: 'round' as const };

function LaunchPad() {
  const { label, launched, armOff } = useLaunch();
  return (
    <div className={styles.pad} aria-hidden="true">
      <div className={styles.countdown}>
        <span className={styles.countdownLabel}>COUNTDOWN</span>
        <span className={styles.countdownValue}>{label}</span>
      </div>

      <svg viewBox="0 0 150 440" className={styles.tower}>
        <path
          d="M40 20 V440 M110 20 V440"
          style={{ fill: 'none', stroke: '#2A9D8F', strokeWidth: 6 }}
        />
        <path
          d="M40 20 L110 62 M110 20 L40 62 M40 62 L110 104 M110 62 L40 104 M40 104 L110 146 M110 104 L40 146 M40 146 L110 188 M110 146 L40 188 M40 188 L110 230 M110 188 L40 230 M40 230 L110 272 M110 230 L40 272 M40 272 L110 314 M110 272 L40 314 M40 314 L110 356 M110 314 L40 356 M40 356 L110 398 M110 356 L40 398 M40 398 L110 440 M110 398 L40 440"
          style={{ fill: 'none', stroke: '#2A9D8F', strokeWidth: 2.5, strokeOpacity: 0.8 }}
        />
        <path
          d="M34 20 H116"
          style={{ fill: 'none', stroke: '#2A9D8F', strokeWidth: 6, strokeLinecap: 'round' }}
        />
        <path d="M75 20 V4" style={{ fill: 'none', stroke: '#2A9D8F', strokeWidth: 3 }} />
        <circle cx="75" cy="2" r="5" fill="#D1495B" className={styles.blink} />
        <g className={clsx(styles.arm, armOff && styles.armOff)}>
          <rect x="-14" y="166" width="56" height="12" rx="3" fill="#2A9D8F" />
          <rect x="-20" y="160" width="10" height="24" rx="3" fill="#E9B949" />
        </g>
        <rect x="-14" y="300" width="56" height="10" rx="3" fill="#2A9D8F" />
      </svg>

      <div className={clsx(styles.rocket, launched && styles.rocketUp)}>
        <svg viewBox="0 0 120 260" className={styles.rocketSvg}>
          <g className={clsx(styles.flame, launched && styles.flameBig)}>
            <path d="M47 204 Q60 252 73 204 Z" fill="#E9B949" />
            <path d="M53 204 Q60 232 67 204 Z" fill="#F4A261" />
          </g>
          <path d="M38 150 L13 208 L37 199 Z" fill="#E76F51" style={STROKE} />
          <path d="M82 150 L107 208 L83 199 Z" fill="#E76F51" style={STROKE} />
          <path d="M44 190 H76 L72 205 H48 Z" fill="#8C836F" style={STROKE} />
          <path
            d="M60 10 C86 40 92 110 84 190 H36 C28 110 34 40 60 10 Z"
            fill="#F3E9D2"
            style={STROKE}
          />
          <path
            d="M60 10 C70 20 77 33 81 48 H39 C43 33 50 20 60 10 Z"
            fill="#E76F51"
            style={STROKE}
          />
          <rect x="33" y="140" width="54" height="12" fill="#E76F51" />
          <circle
            cx="60"
            cy="92"
            r="16"
            fill="#1E2A44"
            style={{ stroke: '#E76F51', strokeWidth: 5 }}
          />
          <path
            d="M51 86 A10 10 0 0 1 60 80"
            style={{
              fill: 'none',
              stroke: '#F3E9D2',
              strokeWidth: 3,
              strokeLinecap: 'round',
              strokeOpacity: 0.7,
            }}
          />
          <path d="M56 168 H64 V216 H56 Z" fill="#E76F51" style={STROKE} />
        </svg>
      </div>

      <svg viewBox="0 0 360 110" className={styles.smokeSvg}>
        <g className={clsx(styles.smoke, launched && styles.smokeOn)}>
          <circle cx="110" cy="80" r="34" fill="#E9DFC7" />
          <circle cx="160" cy="70" r="42" fill="#F3E9D2" />
          <circle cx="215" cy="74" r="38" fill="#E9DFC7" />
          <circle cx="262" cy="84" r="28" fill="#F3E9D2" />
          <circle cx="72" cy="92" r="22" fill="#F3E9D2" />
        </g>
      </svg>

      <div className={styles.platform} />
    </div>
  );
}

const DRAW = { fill: 'none', strokeWidth: 2.5 };
const LINE = { fill: 'none', strokeWidth: 2.5, strokeLinecap: 'round' as const };

const TILES: { title: string; text: string; to: string; art: React.ReactNode }[] = [
  {
    title: 'Stack',
    text: 'Push, pop and swipe back on a real native stack.',
    to: '/docs/containers/stack',
    art: (
      <>
        <rect
          x="52"
          y="8"
          width="40"
          height="76"
          rx="8"
          stroke="currentColor"
          opacity="0.35"
          style={DRAW}
        />
        <rect
          x="44"
          y="12"
          width="40"
          height="76"
          rx="8"
          stroke="currentColor"
          opacity="0.6"
          style={DRAW}
        />
        <rect
          x="36"
          y="16"
          width="40"
          height="76"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <path
          d="M46 28 L42 32 L46 36"
          style={{
            fill: 'none',
            stroke: '#E76F51',
            strokeWidth: 3,
            strokeLinecap: 'round',
            strokeLinejoin: 'round',
          }}
        />
        <path
          d="M44 46 H68 M44 54 H62 M44 62 H66"
          stroke="currentColor"
          opacity="0.35"
          style={LINE}
        />
      </>
    ),
  },
  {
    title: 'Tabs',
    text: 'The system tab bar; every tab keeps its own stack.',
    to: '/docs/containers/tabs',
    art: (
      <>
        <rect
          x="40"
          y="8"
          width="40"
          height="84"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <path d="M40 74 H80" stroke="currentColor" style={{ fill: 'none', strokeWidth: 2 }} />
        <circle cx="48" cy="83" r="2.6" fill="#E76F51" />
        <circle cx="56" cy="83" r="2.6" fill="currentColor" opacity="0.45" />
        <circle cx="64" cy="83" r="2.6" fill="currentColor" opacity="0.45" />
        <circle cx="72" cy="83" r="2.6" fill="currentColor" opacity="0.45" />
        <path
          d="M48 24 H72 M48 32 H66 M48 40 H70"
          stroke="currentColor"
          opacity="0.35"
          style={LINE}
        />
      </>
    ),
  },
  {
    title: 'Modals',
    text: 'Sheets and full-screen modals from the platform.',
    to: '/docs/containers/modals',
    art: (
      <>
        <rect
          x="40"
          y="8"
          width="40"
          height="84"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <rect x="40" y="38" width="40" height="54" rx="8" fill="#E76F51" opacity="0.35" />
        <path d="M54 44 H66" stroke="currentColor" style={LINE} />
        <path d="M48 56 H72 M48 64 H66" stroke="currentColor" opacity="0.4" style={LINE} />
      </>
    ),
  },
  {
    title: 'Side menu',
    text: 'A drawer next to the center screen.',
    to: '/docs/containers/side-menu',
    art: (
      <>
        <rect
          x="40"
          y="8"
          width="40"
          height="84"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <rect x="40" y="8" width="24" height="84" rx="8" fill="#E9B949" opacity="0.5" />
        <path d="M45 24 H58 M45 32 H55 M45 40 H58" stroke="currentColor" style={LINE} />
      </>
    ),
  },
  {
    title: 'Split view',
    text: 'Columns for the large iPad screen.',
    to: '/docs/containers/split-view',
    art: (
      <>
        <rect
          x="14"
          y="16"
          width="92"
          height="68"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <rect x="14" y="16" width="26" height="68" rx="8" fill="#2A9D8F" opacity="0.35" />
        <path
          d="M40 16 V84 M72 16 V84"
          stroke="currentColor"
          style={{ fill: 'none', strokeWidth: 2 }}
        />
        <path
          d="M46 28 H66 M46 36 H62 M78 28 H98"
          stroke="currentColor"
          opacity="0.4"
          style={LINE}
        />
      </>
    ),
  },
  {
    title: 'Overlays',
    text: 'Toasts, banners and dialogs above every screen.',
    to: '/docs/containers/overlays',
    art: (
      <>
        <rect
          x="40"
          y="8"
          width="40"
          height="84"
          rx="8"
          stroke="currentColor"
          style={{ fill: 'var(--an-surface2)', strokeWidth: 2.5 }}
        />
        <rect x="44" y="16" width="32" height="11" rx="5.5" fill="#5FA8D3" opacity="0.75" />
        <path
          d="M48 42 H72 M48 50 H66 M48 58 H70"
          stroke="currentColor"
          opacity="0.35"
          style={LINE}
        />
      </>
    ),
  },
];

function Mono({ children }: { children: React.ReactNode }) {
  return <span className={styles.inlineMono}>{children}</span>;
}

export default function Home(): React.JSX.Element {
  const stars = useMemo(makeStars, []);
  return (
    <Layout
      title="Native navigation for React Native"
      description="Stacks, tabs, modals, side menus and overlays are real platform containers, owned by the native side and driven from JS."
    >
      <div className={clsx('home-page', styles.page)}>
        <section className={styles.hero}>
          <div className={styles.stars} aria-hidden="true">
            {stars.map((s, i) => (
              <span
                key={i}
                className={styles.star}
                style={{
                  left: `${s.x}%`,
                  top: `${s.y}%`,
                  width: `${s.r}px`,
                  height: `${s.r}px`,
                  animationDelay: `${s.d}s`,
                }}
              />
            ))}
          </div>
          <div className={styles.heroInner}>
            <div className={styles.heroText}>
              <div className={styles.eyebrow} style={{ color: '#E9B949' }}>
                @apexnative/navigation
              </div>
              <h1 className={styles.heroTitle}>Native navigation for React Native</h1>
              <p className={styles.heroLead}>
                Stacks, tabs, modals, side menus and overlays are real platform containers, owned by
                the native side and driven from JS.
              </p>
              <div className={styles.buttons}>
                <Link className={clsx(styles.btn, styles.btnPrimary)} to="/docs/start/quick-start">
                  Get started
                </Link>
                <Link
                  className={clsx(styles.btn, styles.btnGhost)}
                  to="/api/starting-the-app/createNavigation"
                >
                  API reference
                </Link>
              </div>
              <div className={styles.pills}>
                <span className={styles.pill} style={{ background: '#E9B949', color: '#101828' }}>
                  Early development
                </span>
                <span className={clsx(styles.pill, styles.pillDim)}>Not on npm yet</span>
                <span className={clsx(styles.pill, styles.pillDim)}>iOS · iPadOS · Android</span>
              </div>
            </div>
            <LaunchPad />
          </div>
          <svg
            aria-hidden="true"
            viewBox="0 0 1440 60"
            preserveAspectRatio="none"
            className={styles.horizon}
          >
            <path d="M0 60 V34 Q720 0 1440 34 V60 Z" />
          </svg>
        </section>

        <section id="guides" className={styles.rulesSection}>
          <div className={styles.eyebrow} style={{ color: 'var(--an-link)' }}>
            Flight rules
          </div>
          <h2 className={styles.h2}>How it is built</h2>
          <div className={styles.rules}>
            {RULES.map(([title, text, color], i) => (
              <article
                key={title}
                className={styles.rule}
                style={{ boxShadow: `0 5px 0 ${color}` }}
              >
                <span className={styles.ruleNumber} style={{ background: color }}>
                  {String(i + 1).padStart(2, '0')}
                </span>
                <h3 className={styles.ruleTitle}>{title}</h3>
                <p className={styles.ruleText}>{text}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.boardSection}>
          <div className={styles.eyebrow} style={{ color: 'var(--an-link)' }}>
            On board
          </div>
          <h2 className={styles.h2}>Real platform containers</h2>
          <p className={styles.boardLead}>
            UINavigationController, UITabBarController and Material components do the work. JS
            describes the layout.
          </p>
          <div className={styles.tiles}>
            {TILES.map((tile) => (
              <Link key={tile.title} className={styles.tile} to={tile.to}>
                <svg aria-hidden="true" viewBox="0 0 120 100" className={styles.tileArt}>
                  {tile.art}
                </svg>
                <span className={styles.tileTitle}>{tile.title}</span>
                <span className={styles.tileText}>{tile.text}</span>
              </Link>
            ))}
          </div>
        </section>

        <section id="api" className={styles.band}>
          <div className={styles.bandInner}>
            <div className={styles.bandText}>
              <div className={styles.eyebrow} style={{ color: 'var(--an-link)' }}>
                Countdown
              </div>
              <h2 className={styles.h2}>From an empty app to a native stack</h2>
              <ol className={styles.steps}>
                <li>
                  <span className={styles.stepNumber}>3</span>
                  <span className={styles.stepText}>
                    Define your screens with <Mono>defineScreen</Mono>.
                  </span>
                </li>
                <li>
                  <span className={styles.stepNumber}>2</span>
                  <span className={styles.stepText}>
                    Describe the roots with builders like <Mono>Stack</Mono> and <Mono>Tabs</Mono>.
                  </span>
                </li>
                <li>
                  <span className={clsx(styles.stepNumber, styles.stepGo)}>1</span>
                  <span className={styles.stepText}>
                    Hand both to <Mono>createNavigation</Mono> and launch.
                  </span>
                </li>
              </ol>
              <div className={styles.bandButton}>
                <Link className={clsx(styles.btn, styles.btnInk)} to="/docs/start/quick-start">
                  Read the quick start
                </Link>
              </div>
            </div>
            <div className={styles.bandCode}>
              <div className={styles.window}>
                <div className={styles.windowBar}>
                  <span className={styles.windowDot} style={{ background: '#E76F51' }} />
                  <span className={styles.windowDot} style={{ background: '#E9B949' }} />
                  <span className={styles.windowDot} style={{ background: '#2A9D8F' }} />
                  <span className={styles.windowFile}>src/App.ts</span>
                </div>
                <pre className={styles.code}>
                  <span className={styles.tk}>import</span> {'{ createNavigation, Stack } '}
                  <span className={styles.tk}>from</span>{' '}
                  <span className={styles.ts}>'@apexnative/navigation'</span>;{'\n'}
                  <span className={styles.tk}>import</span> {'{ DetailsScreen } '}
                  <span className={styles.tk}>from</span>{' '}
                  <span className={styles.ts}>'./DetailsScreen'</span>;{'\n'}
                  <span className={styles.tk}>import</span> {'{ HomeScreen } '}
                  <span className={styles.tk}>from</span>{' '}
                  <span className={styles.ts}>'./HomeScreen'</span>;{'\n\n'}
                  <span className={styles.tk}>export const</span> App ={' '}
                  <span className={styles.tf}>createNavigation</span>
                  {'({\n'}
                  {'  screens: { Home: HomeScreen, Details: DetailsScreen },\n'}
                  {'  roots: { main: '}
                  <span className={styles.tf}>Stack</span>(<span className={styles.ts}>'Home'</span>
                  ){' },\n'}
                  {'  initialRoot: () => '}
                  <span className={styles.ts}>'main'</span>,{'\n'}
                  {'});'}
                </pre>
              </div>
            </div>
          </div>
        </section>
      </div>
    </Layout>
  );
}
