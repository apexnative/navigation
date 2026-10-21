import clsx from 'clsx';
import React, { Children, type ReactNode } from 'react';
import styles from './styles.module.css';

// Blocks of the explaining pages (Introduction first), in the look of the boards: 2px ink borders, rounded cards,
// shadows of the accent color, mono eyebrows, Righteous titles.

/** A key phrase, marked as with a highlighter. */
export function Mark({ children }: { children: ReactNode }) {
  return <mark className={styles.mark}>{children}</mark>;
}

/** A platform named in text: its color chip. */
export function Os({ name }: { name: 'iOS' | 'iPadOS' | 'Android' }) {
  const tone =
    name === 'Android' ? styles.osAndroid : name === 'iPadOS' ? styles.osIpad : styles.osIos;
  return <span className={clsx(styles.os, tone)}>{name}</span>;
}

/** The idea of the page in one line. */
export function Thesis({ eyebrow, children }: { eyebrow?: string; children: ReactNode }) {
  return (
    <div className={styles.thesis}>
      {eyebrow && <span className={clsx('eyebrow', styles.thesisEyebrow)}>{eyebrow}</span>}
      <p className={styles.thesisText}>{children}</p>
    </div>
  );
}

/** Approaches side by side, row by row; the last column is this library. */
export function Models({
  columns,
  rows,
}: {
  columns: { eyebrow: string; title: string }[];
  rows: { label: string; cells: ReactNode[] }[];
}) {
  const last = columns.length - 1;
  const template = { gridTemplateColumns: `120px repeat(${columns.length}, minmax(0, 1fr))` };
  return (
    <div className={styles.models} role="table" aria-label="Approaches to navigation">
      <div className={styles.modelsHead} role="row" style={template}>
        <span className={styles.modelsCorner} role="columnheader" aria-label="Aspect" />
        {columns.map((column, i) => (
          <div
            key={column.title}
            className={i === last ? styles.modelRightHead : styles.modelLeftHead}
            role="columnheader"
          >
            <span className={clsx('eyebrow', i === last && styles.modelRightEyebrow)}>
              {column.eyebrow}
            </span>
            <span className={styles.modelTitle}>{column.title}</span>
          </div>
        ))}
      </div>
      {rows.map((row) => (
        <div key={row.label} className={styles.modelsRow} role="row" style={template}>
          <span className={clsx('eyebrow', styles.modelsLabel)} role="rowheader">
            {row.label}
          </span>
          {row.cells.map((cell, i) => (
            <div key={i} className={i === last ? styles.modelRight : styles.modelLeft} role="cell">
              <span className={styles.modelMobileTag}>{columns[i].title}</span>
              {cell}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

/** Two code blocks side by side: the same task in two ways; the right one is this library. */
export function Pair({
  left,
  right,
  children,
}: {
  left: string;
  right: string;
  children: ReactNode;
}) {
  const [first, second] = Children.toArray(children);
  return (
    <div className={styles.pair}>
      <div className={styles.pairSide}>
        <span className={clsx('eyebrow', styles.pairLabel)}>{left}</span>
        {first}
      </div>
      <div className={clsx(styles.pairSide, styles.pairOurs)}>
        <span className={clsx('eyebrow', styles.pairLabel)}>{right}</span>
        {second}
      </div>
    </div>
  );
}

const COLORS = [
  '#E76F51',
  '#E9B949',
  '#2A9D8F',
  '#D1495B',
  '#5FA8D3',
  '#9C89B8',
  '#F4A261',
  '#8A9A5B',
];

/** Numbered cards, as "How it is built" on the home page. */
export function Principles({ items }: { items: { title: string; text: ReactNode }[] }) {
  return (
    <div className={styles.principles}>
      {items.map((item, i) => (
        <article
          key={item.title}
          className={styles.principle}
          style={{ boxShadow: `0 5px 0 ${COLORS[i % COLORS.length]}` }}
        >
          <span
            className={styles.principleNumber}
            style={{ background: COLORS[i % COLORS.length] }}
          >
            {String(i + 1).padStart(2, '0')}
          </span>
          <h3 className={styles.principleTitle}>{item.title}</h3>
          <div className={styles.principleText}>{item.text}</div>
        </article>
      ))}
    </div>
  );
}

/** The three layers of the library and what crosses between them. */
export function Layers() {
  return (
    <div
      className={styles.layers}
      role="img"
      aria-label="Your app sends commands to the platform; the platform sends events back"
    >
      <div className={clsx(styles.layer, styles.layerApp)}>
        <span className="eyebrow">Your app · JavaScript</span>
        <span className={styles.layerTitle}>Screens and the layout</span>
        <span className={styles.chips}>
          <code>defineScreen</code>
          <code>createNavigation</code>
          <code>useNavigation</code>
          <code>useScreenAppear</code>
        </span>
      </div>
      <div className={styles.flow} aria-hidden="true">
        <span className={styles.flowDown}>commands ↓</span>
        <span className={styles.flowUp}>↑ events</span>
      </div>
      <div className={clsx(styles.layer, styles.layerBridge)}>
        <span className="eyebrow">Commands and events · New Architecture</span>
        <span className={styles.layerTitle}>A layout and a command go down, events come up</span>
        <span className={styles.chips}>
          <code>push</code>
          <code>showModal</code>
          <code>switchTo</code>
          <code>screenDidAppear</code>
          <code>stateChanged</code>
        </span>
      </div>
      <div className={styles.flow} aria-hidden="true">
        <span className={styles.flowDown}>commands ↓</span>
        <span className={styles.flowUp}>↑ events</span>
      </div>
      <div className={styles.platforms}>
        <div className={clsx(styles.layer, styles.layerIos)}>
          <span className="eyebrow">Platform · iOS and iPadOS</span>
          <span className={styles.layerTitle}>UIKit containers</span>
          <span className={styles.chips}>
            <code>UINavigationController</code>
            <code>UITabBarController</code>
            <code>UISplitViewController</code>
            <code>sheets</code>
          </span>
        </div>
        <div className={clsx(styles.layer, styles.layerAndroid)}>
          <span className="eyebrow">Platform · Android</span>
          <span className={styles.layerTitle}>Native stack and Material</span>
          <span className={styles.chips}>
            <code>Activity</code>
            <code>BottomNavigationView</code>
            <code>MaterialToolbar</code>
            <code>back press</code>
          </span>
        </div>
      </div>
    </div>
  );
}

/** Numbered steps on a rail, with the code of each step. */
export function Steps({ items }: { items: { text: ReactNode; code?: string }[] }) {
  return (
    <ol className={styles.steps}>
      {items.map((item, i) => (
        <li key={i} className={styles.step}>
          <span className={clsx(styles.stepNumber, i === items.length - 1 && styles.stepLast)}>
            {i + 1}
          </span>
          <span className={styles.stepBody}>
            <span className={styles.stepText}>{item.text}</span>
            {item.code && <code className={styles.stepCode}>{item.code}</code>}
          </span>
        </li>
      ))}
    </ol>
  );
}

/** Where the library fits and where it does not. */
export function Fit({
  yes,
  no,
  yesLabel = 'Fits',
  noLabel = 'Not a fit',
}: {
  yes: ReactNode[];
  no: ReactNode[];
  yesLabel?: string;
  noLabel?: string;
}) {
  return (
    <div className={styles.fit}>
      <div className={clsx(styles.fitCard, styles.fitYes)}>
        <div className={styles.fitHead}>
          <span aria-hidden="true">✓</span> {yesLabel}
        </div>
        <ul className={styles.fitList}>
          {yes.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </div>
      <div className={clsx(styles.fitCard, styles.fitNo)}>
        <div className={styles.fitHead}>
          <span aria-hidden="true">✕</span> {noLabel}
        </div>
        <ul className={styles.fitList}>
          {no.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** The way a section of the docs goes, from written to closed. */
export function Cycle() {
  const stages = [
    { label: 'Written', note: 'the page and its API reference', color: '#5FA8D3' },
    { label: 'API reviewed', note: 'names, types, defaults', color: '#E9B949' },
    { label: 'Checked', note: 'iOS, iPadOS and Android, in the Showcase', color: '#9C89B8' },
    { label: 'Stable', note: 'the API stays as documented', color: '#2A9D8F' },
  ];
  return (
    <ol className={styles.cycle}>
      {stages.map((stage) => (
        <li key={stage.label} className={styles.cycleStage}>
          <span
            className={styles.cycleDot}
            style={{ background: stage.color }}
            aria-hidden="true"
          />
          <span className={styles.cycleLabel}>{stage.label}</span>
          <span className={styles.cycleNote}>{stage.note}</span>
        </li>
      ))}
    </ol>
  );
}

/** Where the library comes from: one quiet card at the end of the page. */
export function Credits({ children }: { children: ReactNode }) {
  return (
    <aside className={styles.credits} aria-label="Credits">
      <span className={clsx('eyebrow', styles.creditsTitle)}>Credits</span>
      <div className={styles.creditsText}>{children}</div>
    </aside>
  );
}

/** What a setup needs, a card per platform or tool, in the color of each. */
export function Requirements({
  items,
}: {
  items: { title: string; tone: string; lines: ReactNode[] }[];
}) {
  return (
    <div className={styles.reqs}>
      {items.map((item) => (
        <div
          key={item.title}
          className={styles.req}
          style={{ boxShadow: `inset 0 6px 0 ${item.tone}` }}
        >
          <span className={styles.reqTitle}>{item.title}</span>
          <ul className={styles.reqList}>
            {item.lines.map((line, i) => (
              <li key={i}>{line}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

/** Ways to start, side by side: each with its command and where it is explained. */
export function Paths({
  items,
}: {
  items: { eyebrow: string; title: string; text: ReactNode; command: string; to: string }[];
}) {
  return (
    <div className={styles.paths}>
      {items.map((item, i) => (
        <a
          key={item.title}
          href={item.to}
          className={clsx(styles.path, i === 0 && styles.pathFirst)}
        >
          <span className={clsx('eyebrow', styles.pathEyebrow)}>{item.eyebrow}</span>
          <span className={styles.pathTitle}>{item.title}</span>
          <span className={styles.pathText}>{item.text}</span>
          <code className={styles.pathCommand}>{item.command}</code>
        </a>
      ))}
    </div>
  );
}

type StationState = 'run' | 'skip' | 'restore' | 'fail';

const STATIONS = ['Splash screen', 'willMount', 'First screen', 'didMount'];

const LAUNCHES: {
  id: string;
  label: string;
  note: string;
  splashUntil: number;
  /** What covers the window until the first screen: the splash screen, or a blank background on a reload. */
  band?: 'blank';
  stations: { state: StationState; text: string }[];
}[] = [
  {
    id: 'cold',
    label: 'Cold start',
    note: 'The app starts from nothing: everything runs once, in order.',
    splashUntil: 2,
    stations: [
      { state: 'run', text: 'The OS starts the app' },
      { state: 'run', text: 'Runs; the splash screen waits' },
      { state: 'run', text: 'The first root shows: the user sees the app' },
      { state: 'run', text: 'Runs, with the first screen on screen' },
    ],
  },
  {
    id: 'reload',
    label: 'JavaScript reload',
    note: 'A new JavaScript runtime in development: everything runs again, without the splash screen.',
    splashUntil: 2,
    band: 'blank',
    stations: [
      { state: 'skip', text: "Doesn't show: a blank background for a moment" },
      { state: 'run', text: 'Runs again' },
      { state: 'run', text: 'Shows again, from the first root' },
      { state: 'run', text: 'Runs again' },
    ],
  },
  {
    id: 'activity',
    label: 'Android: a new activity',
    note: 'The activity is new, the JavaScript runtime is the same: nothing runs twice.',
    splashUntil: 2,
    stations: [
      { state: 'run', text: 'Shows for a moment' },
      { state: 'skip', text: "Doesn't run" },
      { state: 'restore', text: 'The last root comes back; screens pushed after it are gone' },
      { state: 'skip', text: "Doesn't run" },
    ],
  },
  {
    id: 'fail',
    label: 'The start fails',
    note: 'willMount or the first root throws: errorFallback shows instead, and its retry runs the start again. Without errorFallback the splash screen stays until the next launch.',
    splashUntil: 2,
    stations: [
      { state: 'run', text: 'The OS starts the app' },
      { state: 'fail', text: 'Throws; the error goes to onError' },
      { state: 'restore', text: 'errorFallback shows, with retry' },
      { state: 'skip', text: "Doesn't run until a start succeeds" },
    ],
  },
];

/** The life of every kind of launch: a line of stations each, one under the other. */
export function Lifecycle() {
  return (
    <div className={styles.life}>
      <div className={styles.lifeHead} aria-hidden="true">
        {STATIONS.map((name) => (
          <code key={name} className={styles.lifeHeadName}>
            {name}
          </code>
        ))}
      </div>
      {LAUNCHES.map((launch) => {
        const stuck = launch.splashUntil === STATIONS.length;
        return (
          <section key={launch.id} className={styles.lifeRow} aria-label={launch.label}>
            <div className={styles.lifeRowHead}>
              <span className={styles.lifeRowTitle}>{launch.label}</span>
              <span className={styles.lifeNote}>{launch.note}</span>
            </div>
            <div className={styles.lifeTrack}>
              <div
                className={clsx(
                  styles.lifeSplash,
                  stuck && styles.lifeSplashStuck,
                  launch.band === 'blank' && styles.lifeSplashBlank
                )}
                style={{ gridColumn: `1 / ${launch.splashUntil + 1}` }}
              >
                {launch.band === 'blank'
                  ? 'Blank background'
                  : stuck
                    ? 'Splash screen, until the next launch'
                    : 'Splash screen'}
              </div>
              {STATIONS.map((name, i) => {
                const station = launch.stations[i];
                return (
                  <div
                    key={name}
                    className={clsx(styles.lifeStation, styles[`life_${station.state}`])}
                    style={{ gridColumn: i + 1 }}
                  >
                    <span className={styles.lifeDot} aria-hidden="true">
                      {station.state === 'fail'
                        ? '✕'
                        : station.state === 'restore'
                          ? '↺'
                          : station.state === 'run'
                            ? '✓'
                            : ''}
                    </span>
                    <code className={styles.lifeName}>{name}</code>
                    <span className={styles.lifeText}>{station.text}</span>
                  </div>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
