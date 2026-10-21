import React, { type ReactNode } from 'react';
import styles from './styles.module.css';

// What the CLI prints in a terminal with color: the porthole banner and the colors of its brand (cli/ui.js).

type Tone = 'coral' | 'yellow' | 'cream' | 'muted' | 'faint';

const t = (tone: Tone, text: string) => <span className={styles[tone]}>{text}</span>;
const glass = (text: string, tone: Tone = 'cream') => (
  <span className={`${styles.glass} ${styles[tone]}`}>{text}</span>
);

/** The banner of the CLI: the porthole with the rocket, the name, the command and what it found first. */
function Banner({ command, facts }: { command: string; facts: { text: string; ok?: boolean }[] }) {
  return (
    <div className={styles.banner}>
      {'   '}
      {t('yellow', '✦')}
      {t('faint', '        ·              ·          ')}
      {t('yellow', '✦')}
      {'\n   '}
      {t('coral', '▄██████▄')}
      {'      '}
      {t('coral', '@APEXNATIVE/NAVIGATION')}
      {'\n  '}
      {t('coral', '██')}
      {glass('  ▲   ')}
      {t('coral', '██')}
      {'     '}
      <span className={styles.bold}>navigation</span>
      {t('muted', `  ${command}`)}
      {'\n  '}
      {t('coral', '██')}
      {glass(' ◢█◣  ')}
      {t('coral', '██')}
      {'     '}
      {t('muted', 'Native navigation for React Native')}
      {'\n  '}
      {t('coral', '██')}
      {glass('  ')}
      {glass('▀', 'yellow')}
      {glass('   ')}
      {t('coral', '██')}
      {'\n   '}
      {t('coral', '▀██████▀')}
      {'      '}
      {facts.map((fact, i) => (
        <React.Fragment key={fact.text}>
          {i > 0 && t('muted', '   ')}
          {t('muted', fact.text)}
          {fact.ok && <> {t('yellow', '✓')}</>}
        </React.Fragment>
      ))}
    </div>
  );
}

type Status = 'ok' | 'warn' | 'error' | 'info';
const MARKS: Record<Status, ReactNode> = {
  ok: t('yellow', '✓'),
  warn: t('yellow', '▲'),
  error: t('coral', '✕'),
  info: t('muted', '·'),
};
const DETAIL: Record<Status, Tone> = { ok: 'faint', info: 'faint', warn: 'yellow', error: 'coral' };

interface Group {
  title: string;
  checks: { status: Status; title: string; detail?: string; fix?: string }[];
}

/** An example run of `apex-navigation doctor`, as a color terminal shows it. */
export function DoctorRun({
  facts,
  groups,
  summary,
}: {
  facts: { text: string; ok?: boolean }[];
  groups: Group[];
  summary: string;
}) {
  const width = Math.max(...groups.flatMap((g) => g.checks.map((c) => c.title.length)));
  return (
    <div className="cb">
      <div className="cb__head">
        <span className="cb__title">Terminal · npx apex-navigation doctor</span>
      </div>
      <pre className={styles.screen} aria-label="An example run of doctor">
        <Banner command="doctor" facts={facts} />
        {groups.map((group) => (
          <React.Fragment key={group.title}>
            {t('muted', `  ${group.title}`)}
            {'\n'}
            {group.checks.map((check) => (
              <React.Fragment key={check.title}>
                {'  '}
                {MARKS[check.status]} {check.title.padEnd(width)}
                {'   '}
                {check.detail && t(DETAIL[check.status], check.detail)}
                {'\n'}
                {check.fix && (check.status === 'warn' || check.status === 'error') && (
                  <>
                    {t('muted', `      fix  ${check.fix}`)}
                    {'\n'}
                  </>
                )}
              </React.Fragment>
            ))}
          </React.Fragment>
        ))}
        {'\n'}
        {t('faint', '  ' + '─'.repeat(46))}
        {'\n  '}
        {t('muted', summary)}
      </pre>
    </div>
  );
}
