// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { PassThrough } = require('node:stream');
const { mcp, mcpInstall } = require('..');
const { loadDocs, readDoc, searchDocs } = require('../docs');
const { toMarkdown } = require('../markdown');
const { createUI } = require('../../ui');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const MODERN = {
  'io.modelcontextprotocol/protocolVersion': '2026-07-28',
  'io.modelcontextprotocol/clientCapabilities': {},
};

/** The app after setup, with React Native and TypeScript where the CLI looks for them. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-mcp-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  fs.mkdirSync(path.join(root, 'node_modules', 'react-native'), { recursive: true });
  fs.writeFileSync(
    path.join(root, 'node_modules', 'react-native', 'package.json'),
    '{ "version": "0.87.1" }'
  );
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  return root;
}

/**
 * The answers of the server to messages, in order.
 * @param {string} root
 * @param {object[]} messages
 */
async function talk(root, messages) {
  const input = new PassThrough();
  /** @type {any[]} */
  const answers = [];
  const done = mcp({ root, input, send: (line) => answers.push(JSON.parse(line)) });
  for (const message of messages)
    input.write(JSON.stringify({ jsonrpc: '2.0', ...message }) + '\n');
  input.end();
  await done;
  return answers;
}

describe('the MCP server', () => {
  it('answers the handshake of 2025 and lists its tools', async () => {
    const [initialize, list] = await talk(app(), [
      { id: 1, method: 'initialize', params: { protocolVersion: '2025-06-18', capabilities: {} } },
      { method: 'notifications/initialized' },
      { id: 2, method: 'tools/list' },
    ]);
    expect(initialize.result).toMatchObject({
      protocolVersion: '2025-06-18',
      serverInfo: { name: 'apex-navigation' },
      capabilities: { tools: { listChanged: false } },
    });
    expect(list.result.tools.map((/** @type {any} */ tool) => tool.name)).toEqual([
      'app_map',
      'docs_search',
      'docs_read',
      'doctor',
      'info',
      'setup',
      'screen',
      'codegen',
      'links',
      'icons',
      'splash',
      'migrate',
    ]);
    expect(list.result.tools[0].annotations).toEqual({ readOnlyHint: true, openWorldHint: false });
  });

  it('answers the discovery of 2026-07-28, with the version on each result', async () => {
    const [discover, map] = await talk(app(), [
      { id: 1, method: 'server/discover', params: { _meta: MODERN } },
      { id: 2, method: 'tools/call', params: { _meta: MODERN, name: 'app_map', arguments: {} } },
    ]);
    expect(discover.result).toMatchObject({
      resultType: 'complete',
      supportedVersions: ['2026-07-28', '2025-11-25', '2025-06-18', '2025-03-26'],
      ttlMs: 0,
      cacheScope: 'private',
      _meta: { 'io.modelcontextprotocol/serverInfo': { name: 'apex-navigation' } },
    });
    expect(map.result).toMatchObject({
      resultType: 'complete',
      isError: false,
      structuredContent: {
        navigation: 'Navigation.ts',
        mode: 'root-per-screen',
        screens: [{ name: 'Home', kind: 'react', props: [] }],
        root: "'Home'",
      },
    });
    expect(JSON.parse(map.result.content[0].text)).toEqual(map.result.structuredContent);
  });

  it('runs a command as a plan unless it applies, its output caught', async () => {
    const root = app();
    const [plan] = await talk(root, [
      { id: 1, method: 'tools/call', params: { name: 'codegen', arguments: {} } },
    ]);
    expect(plan.result.structuredContent).toMatchObject({ command: 'codegen', applied: false });
  });

  it('tells an unknown tool, method and version apart', async () => {
    const answers = await talk(app(), [
      { id: 1, method: 'tools/call', params: { name: 'nope', arguments: {} } },
      { id: 2, method: 'resources/list' },
      {
        id: 3,
        method: 'ping',
        params: { _meta: { 'io.modelcontextprotocol/protocolVersion': '1999-01-01' } },
      },
    ]);
    expect(answers.map((answer) => answer.error.code)).toEqual([-32602, -32601, -32022]);
    expect(answers[2].error.data).toEqual({
      supported: ['2026-07-28', '2025-11-25', '2025-06-18', '2025-03-26'],
      requested: '1999-01-01',
    });
  });
});

describe('the docs for agents', () => {
  it('turn a page of the site into Markdown, its sections by anchor', () => {
    const page = toMarkdown(
      'guides/example',
      [
        '---',
        'title: Example',
        "description: 'A page.'",
        '---',
        '',
        "import { Mark, Os, Steps } from '@site/src/components/Intro';",
        '',
        '## First {#first}',
        '',
        'A <Mark>marked</Mark> word on <Os name="iOS" /> only.',
        '',
        "<Steps items={[{ text: 'One.', code: 'push()' }, { text: 'Two.' }]} />",
        '',
        ':::note',
        'A note.',
        ':::',
        '',
        '```tsx title="App.tsx"',
        '<View />',
        '```',
        '',
      ].join('\n')
    );
    expect(page.markdown).toBe(
      '# Example\n\nA page.\n\n## First\n\nA **marked** word on (iOS) only.\n\n1. One. `push()`\n2. Two.\n\n> **Note**\n>\n> A note.\n\n```tsx\n<View />\n```\n'
    );
    expect(page.sections).toEqual([
      expect.objectContaining({ heading: 'First', anchor: 'first', level: 2 }),
    ]);
  });

  it('are searched and read from the pages of the site', () => {
    const docs = loadDocs();
    expect(docs.length).toBeGreaterThan(40);
    const [first] = searchDocs(docs, 'deep links routes prefixes');
    expect(first.page).toBe('guides/deep-links');
    const section = readDoc(docs, 'deep-links', 'routes');
    expect(section?.markdown).toMatch(/^## 2\. Add the routes/);
    expect(readDoc(docs, 'no-such-page')).toBeUndefined();
  });
});

describe('mcp install', () => {
  /**
   * @param {string} root
   * @param {string} client
   */
  async function install(root, client) {
    let text = '';
    const out = { isTTY: false, columns: 80, write: () => true };
    const original = process.stdout.write;
    process.stdout.write = /** @type {any} */ (
      (/** @type {string} */ chunk) => {
        text += chunk;
        return true;
      }
    );
    try {
      const code = await mcpInstall({
        root,
        client,
        yes: true,
        dryRun: false,
        json: true,
        ui: createUI({ out, level: 'none' }),
        write: (chunk) => (text += chunk),
      });
      return { code, result: JSON.parse(text) };
    } finally {
      process.stdout.write = original;
    }
  }

  it('adds the server to the config of a client, keeping the others', async () => {
    const root = app();
    fs.writeFileSync(
      path.join(root, '.mcp.json'),
      '{ "mcpServers": { "other": { "command": "x" } } }\n'
    );
    await install(root, 'claude');
    expect(JSON.parse(fs.readFileSync(path.join(root, '.mcp.json'), 'utf8'))).toEqual({
      mcpServers: {
        other: { command: 'x' },
        'apex-navigation': { command: 'npx', args: ['apex-navigation', 'mcp'] },
      },
    });
    await install(root, 'vscode');
    expect(JSON.parse(fs.readFileSync(path.join(root, '.vscode', 'mcp.json'), 'utf8'))).toEqual({
      servers: {
        'apex-navigation': { type: 'stdio', command: 'npx', args: ['apex-navigation', 'mcp'] },
      },
    });
  });

  it('prints the line for Codex, and fails for a client it has no config for', async () => {
    const root = app();
    expect((await install(root, 'codex')).result.next).toEqual([
      'codex mcp add apex-navigation -- npx apex-navigation mcp',
    ]);
    expect((await install(root, 'other')).code).toBe(1);
  });
});
