// @ts-check
'use strict';

const { colorLevel, createUI } = require('../ui');

function capture(/** @type {import('../ui').ColorLevel} */ level, columns = 80) {
  let text = '';
  const out = {
    isTTY: level !== 'none',
    columns,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  return { ui: createUI({ out, level }), output: () => text };
}

describe('the output of the CLI', () => {
  it('has no color where nothing shows it', () => {
    expect(colorLevel({ isTTY: false }, {})).toBe('none');
    expect(colorLevel({ isTTY: true }, { NO_COLOR: '' })).toBe('none');
    expect(colorLevel({ isTTY: true }, { COLORTERM: 'truecolor' })).toBe('truecolor');
    expect(colorLevel({ isTTY: true }, {})).toBe('256');
  });

  it('is plain text without color: one header line, then the files', () => {
    const { ui, output } = capture('none');
    ui.banner({
      command: 'setup',
      facts: [{ text: 'React Native 0.87.1', ok: true }, { text: 'iOS · Android' }],
    });
    ui.files([
      { kind: 'change', file: 'MainActivity.kt', platform: 'android', note: 'NavigationActivity' },
      { kind: 'done', file: 'Info.plist', platform: 'ios', note: 'already set up' },
    ]);
    expect(output()).toBe(
      'apex-navigation setup   React Native 0.87.1 ✓   iOS · Android\n' +
        '\n' +
        '  Files\n' +
        '  ● MainActivity.kt   android   NavigationActivity\n' +
        '  ○ Info.plist        ios       already set up\n' +
        '\n'
    );
  });

  it('draws the porthole on a wide terminal with color, one line on a narrow one', () => {
    const wide = capture('truecolor', 100);
    wide.ui.banner({ command: 'setup', facts: [] });
    expect(wide.output()).toContain('▄██████▄');
    const narrow = capture('truecolor', 40);
    narrow.ui.banner({ command: 'setup', facts: [] });
    expect(narrow.output()).not.toContain('▄██████▄');
  });

  it('shows the changed lines of a file with a few lines around them', () => {
    const { ui, output } = capture('none');
    ui.diff('MainActivity.kt', 'a\nb\nc\nd\ne\nf\ng', 'a\nb\nc\nD\ne\nf\ng');
    expect(output()).toBe(
      '  ── MainActivity.kt ─────────────────────────────\n' +
        '    b\n    c\n  - d\n  + D\n    e\n    f\n\n'
    );
  });
});
