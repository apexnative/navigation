// @ts-check
'use strict';

const readline = require('node:readline/promises');
const { hunks, diffLines } = require('./diff');

// The colors of the brand, with the nearest 256-color codes for terminals without true color. Plain text keeps the
// terminal's own color, so the output reads on dark and light terminals alike.
const COLORS = {
  coral: { rgb: [232, 112, 79], code: 209 },
  yellow: { rgb: [242, 179, 61], code: 214 },
  cream: { rgb: [243, 235, 221], code: 230 },
  navy: { rgb: [42, 51, 82], code: 237 },
  muted: { rgb: [138, 143, 156], code: 245 },
  faint: { rgb: [91, 98, 122], code: 60 },
};

/** @typedef {keyof typeof COLORS} Color */
/** @typedef {'truecolor' | '256' | 'none'} ColorLevel */
/** @typedef {{ fg?: Color; bg?: Color; bold?: boolean }} Style */
/** @typedef {'change' | 'create' | 'remove' | 'manual' | 'done'} RowKind */
/** @typedef {{ kind: RowKind; file: string; platform: string; note: string }} Row */
/** @typedef {{ text: string; ok?: boolean }} Fact */

// The banner needs this many columns; a narrower terminal gets one line.
const BANNER_COLUMNS = 60;

/**
 * How the terminal shows color: none when it isn't a terminal (a pipe, CI) or `NO_COLOR` is set.
 * @param {{ isTTY?: boolean }} stream
 * @param {NodeJS.ProcessEnv} env
 * @returns {ColorLevel}
 */
function colorLevel(stream, env) {
  if ('NO_COLOR' in env || env.TERM === 'dumb' || !stream.isTTY) return 'none';
  return env.COLORTERM === 'truecolor' || env.COLORTERM === '24bit' ? 'truecolor' : '256';
}

/**
 * @param {{ out?: NodeJS.WriteStream | { write(text: string): unknown; isTTY?: boolean; columns?: number };
 *   input?: NodeJS.ReadableStream; env?: NodeJS.ProcessEnv; level?: ColorLevel }} [options]
 */
function createUI(options = {}) {
  const out = options.out ?? process.stdout;
  const input = options.input ?? process.stdin;
  const level = options.level ?? colorLevel(out, options.env ?? process.env);
  const columns = out.columns ?? 80;
  const write = (/** @type {string} */ text = '') => out.write(text + '\n');

  /**
   * @param {string} text
   * @param {Style} style
   */
  function paint(text, style) {
    if (level === 'none' || text === '') return text;
    const codes = [];
    if (style.bold) codes.push('1');
    if (style.fg) codes.push(colorCode(style.fg, 38));
    if (style.bg) codes.push(colorCode(style.bg, 48));
    return codes.length === 0 ? text : `\x1b[${codes.join(';')}m${text}\x1b[0m`;
  }

  /**
   * @param {Color} color
   * @param {38 | 48} layer
   */
  function colorCode(color, layer) {
    const { rgb, code } = COLORS[color];
    return level === 'truecolor' ? `${layer};2;${rgb.join(';')}` : `${layer};5;${code}`;
  }

  const fg = (/** @type {Color} */ color) => (/** @type {string} */ text) =>
    paint(text, { fg: color });
  const coral = fg('coral');
  const yellow = fg('yellow');
  const muted = fg('muted');
  const faint = fg('faint');

  /** @param {Fact[]} facts */
  const factsLine = (facts) =>
    facts.map(({ text, ok }) => muted(text) + (ok ? ' ' + yellow('✓') : '')).join(muted('   '));

  return {
    level,

    /**
     * The header: a porthole with the rocket in color, one line without color or on a narrow terminal.
     * @param {{ command: string; facts: Fact[] }} header
     */
    banner({ command, facts }) {
      if (level === 'none' || columns < BANNER_COLUMNS) {
        write(`${paint('apex-navigation', { bold: true })} ${command}   ${factsLine(facts)}`);
        write();
        return;
      }
      const ring = coral;
      const glass = (/** @type {string} */ text, /** @type {Color} */ color = 'cream') =>
        paint(text, { fg: color, bg: 'navy' });
      write();
      write('   ' + yellow('✦') + faint('        ·              ·          ') + yellow('✦'));
      write('   ' + ring('▄██████▄') + '      ' + coral('@APEXNATIVE/NAVIGATION'));
      write(
        '  ' +
          ring('██') +
          glass('  ▲   ') +
          ring('██') +
          '     ' +
          paint('navigation', { bold: true }) +
          muted('  ' + command)
      );
      write(
        '  ' +
          ring('██') +
          glass(' ◢█◣  ') +
          ring('██') +
          '     ' +
          muted('Native navigation for React Native')
      );
      write('  ' + ring('██') + glass('  ') + glass('▀', 'yellow') + glass('   ') + ring('██'));
      write('   ' + ring('▀██████▀') + '      ' + factsLine(facts));
      write();
    },

    /**
     * The files and what happens to each.
     * @param {Row[]} rows
     */
    files(rows) {
      const fileWidth = Math.max(...rows.map((row) => row.file.length));
      const platformWidth = Math.max(...rows.map((row) => row.platform.length));
      write(muted('  Files'));
      for (const { kind, file, platform, note } of rows) {
        const name = file.padEnd(fileWidth);
        const where = platform.padEnd(platformWidth);
        if (kind === 'done') write(faint(`  ○ ${name}   ${where}   ${note}`));
        else if (kind === 'manual')
          write(`  ${yellow('▲')} ${name}   ${muted(where)}   ${yellow(note)}`);
        else {
          const mark = { create: '+', remove: '−', change: '●' }[kind];
          write(`  ${coral(mark)} ${name}   ${muted(where)}   ${note}`);
        }
      }
      write();
    },

    /**
     * A file before and after, the changed lines with a few lines around them.
     * @param {string} title
     * @param {string} before
     * @param {string} after
     */
    diff(title, before, after) {
      write(faint(`  ── ${title} `) + faint('─'.repeat(Math.max(4, 44 - title.length))));
      for (const line of hunks(diffLines(before, after))) {
        if (line === null) write(faint('  ⋯'));
        else if (line.type === '-') write(coral(`  - ${line.text}`));
        else if (line.type === '+') write(yellow(`  + ${line.text}`));
        else write(muted(`    ${line.text}`));
      }
      write();
    },

    /** @param {string} text */
    line(text) {
      write(text);
    },

    /** @param {string} text */
    muted(text) {
      write(muted(text));
    },

    /** @param {string} text */
    warn(text) {
      write(`  ${yellow('▲')} ${text}`);
    },

    /** @param {string} text */
    error(text) {
      write(`  ${coral('✕')} ${text}`);
    },

    /** @param {string} text */
    done(text) {
      write(`  ${yellow('✦')} ${text}`);
    },

    /**
     * What to run next, one command a line.
     * @param {string[]} commands
     */
    next(commands) {
      commands.forEach((command, index) =>
        write(`  ${muted(index === 0 ? 'Next' : '    ')}   ${command}`)
      );
      write();
    },

    rule() {
      write(faint('  ' + '─'.repeat(46)));
    },

    /**
     * The findings of doctor by group: a mark, the title and what was found, and how to fix it.
     * @param {{ title: string; checks: import('./doctor/types').Check[] }[]} groups
     */
    checks(groups) {
      const width = Math.max(
        ...groups.flatMap((group) => group.checks.map((check) => check.title.length))
      );
      const marks = { ok: yellow('✓'), warn: yellow('▲'), error: coral('✕'), info: muted('·') };
      const details = { ok: faint, info: faint, warn: yellow, error: coral };
      for (const group of groups) {
        write(muted(`  ${group.title}`));
        for (const { status, title, detail, fix } of group.checks) {
          write(
            `  ${marks[status]} ${title.padEnd(width)}   ${detail ? details[status](detail) : ''}`.trimEnd()
          );
          if (fix && (status === 'error' || status === 'warn'))
            write(muted(`      fix  ${fix.label}`));
        }
      }
      write();
    },

    /**
     * How many checks found a problem or a warning, and how many passed.
     * @param {{ errors: number; warnings: number; passed: number }} counts
     */
    summary({ errors, warnings, passed }) {
      write(faint('  ' + '─'.repeat(46)));
      const passedText = `${passed} ${passed === 1 ? 'check' : 'checks'} passed`;
      if (errors === 0 && warnings === 0) {
        write(`  ${yellow('✦')} Everything looks good ${muted('· ' + passedText)}`);
      } else {
        const parts = [
          errors > 0 && coral(`${errors} ${errors === 1 ? 'problem' : 'problems'}`),
          warnings > 0 && muted(`${warnings} ${warnings === 1 ? 'warning' : 'warnings'}`),
          muted(passedText),
        ].filter(Boolean);
        write(`  ${parts.join(muted(' · '))}`);
      }
      write();
    },

    /**
     * Asks a yes or no question; no when nobody can answer (not a terminal).
     * @param {string} question
     */
    async confirm(question) {
      if (!out.isTTY || !('isTTY' in input) || !input.isTTY) return false;
      const prompt = readline.createInterface({
        input,
        output: /** @type {NodeJS.WritableStream} */ (out),
      });
      try {
        const answer = await prompt.question(`  ${question} ${muted('(y/N)')} `);
        return /^y(es)?$/i.test(answer.trim());
      } finally {
        prompt.close();
      }
    },
  };
}

/** @typedef {ReturnType<typeof createUI>} UI */

module.exports = { createUI, colorLevel };
