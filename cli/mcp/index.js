// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { createRun } = require('../changes');
const { createUI } = require('../ui');
const { serve } = require('./protocol');
const { createTools } = require('./tools');

const NAME = 'apex-navigation';
const ENTRY = { command: 'npx', args: [NAME, 'mcp'] };

const INSTRUCTIONS = `apex-navigation is the CLI of @apexnative/navigation: native navigation for React Native. The platform's own stacks, tabs, modals and bars, owned by the native side and driven from JavaScript; the commands come from the package (navigation.push('Details', { props })), the screens are registered by name in createNavigation.

- app_map first, before changing the navigation: the screens with their props, the roots, the links.
- docs_search and docs_read for the API, the options and the guides: the docs of the version installed here.
- doctor when the app doesn't build or start; info for an issue.
- setup, screen, codegen, links, icons, splash and migrate change files: they return the plan with every diff, and write with apply: true. A screen added in JavaScript needs no native build; a change of native files needs one.
- init (a new app) and upgrade aren't tools: they take minutes and the network. Run npx apex-navigation init or upgrade in a shell.`;

/**
 * The MCP server of the CLI on stdio, for AI coding agents, on the app in `root` (the folder the client starts it in).
 * It takes the stdout the protocol writes to before any tool runs: the commands' output is caught under the tools.
 * @param {{ root: string; input?: NodeJS.ReadableStream; send?: (line: string) => void }} options
 * @returns {Promise<number>} the exit code, when the client closes the input
 */
async function mcp({ root, input, send = process.stdout.write.bind(process.stdout) }) {
  const version = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', '..', 'package.json'), 'utf8')
  ).version;
  await serve({
    info: { name: NAME, version },
    instructions: INSTRUCTIONS,
    tools: createTools(root),
    input,
    send,
  });
  return 0;
}

/**
 * The config of a client with the server in it, in the project: Claude Code `.mcp.json`, Cursor `.cursor/mcp.json`,
 * VS Code `.vscode/mcp.json`; for Codex, whose config is the user's, the line that adds it. Merged into what is there.
 * @param {{
 *   root: string;
 *   client: string | undefined;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   write?: (text: string) => void;
 * }} options
 * @returns {Promise<number>} the exit code
 */
async function mcpInstall({
  root,
  client,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  write = (text) => process.stdout.write(text),
}) {
  const command = createRun({ command: 'mcp install', root, json, ui });
  if (!json) ui.banner({ command: 'mcp install', facts: [{ text: client ?? 'no client' }] });
  if (client === 'codex') {
    const line = `codex mcp add ${NAME} -- ${ENTRY.command} ${ENTRY.args.join(' ')}`;
    if (json) {
      write(JSON.stringify({ command: 'mcp install', ok: true, next: [line] }, null, 2) + '\n');
    } else {
      ui.line('  Codex keeps its servers in ~/.codex/config.toml. Add this one with:');
      ui.next([line]);
    }
    return 0;
  }
  /** @type {Record<string, { file: string; key: string; entry: Record<string, unknown> }>} */
  const clients = {
    claude: { file: '.mcp.json', key: 'mcpServers', entry: ENTRY },
    cursor: { file: path.join('.cursor', 'mcp.json'), key: 'mcpServers', entry: ENTRY },
    vscode: {
      file: path.join('.vscode', 'mcp.json'),
      key: 'servers',
      entry: { type: 'stdio', ...ENTRY },
    },
  };
  const target = client && clients[client];
  if (!target) {
    return command.fail(
      'client-unknown',
      `The client is claude, cursor, vscode or codex: npx ${NAME} mcp install claude`
    );
  }
  const file = path.join(root, target.file);
  const before = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : undefined;
  /** @type {any} */
  let config = {};
  if (before !== undefined) {
    try {
      config = JSON.parse(before);
    } catch {
      return command.apply(
        [
          {
            name: target.file,
            platform: 'js',
            file,
            status: 'manual',
            note: "isn't plain JSON, see below",
            howTo: `Add under "${target.key}":\n\n  "${NAME}": ${JSON.stringify(target.entry)}`,
          },
        ],
        { yes, dryRun }
      );
    }
  }
  const servers = { ...config[target.key] };
  const same = JSON.stringify(servers[NAME]) === JSON.stringify(target.entry);
  servers[NAME] = target.entry;
  const next = JSON.stringify({ ...config, [target.key]: servers }, null, 2) + '\n';
  return command.apply(
    [
      same
        ? { name: target.file, platform: 'js', file, status: 'done', note: 'the server is there' }
        : before === undefined
          ? {
              name: target.file,
              platform: 'js',
              file,
              status: 'create',
              next,
              note: `${NAME} for ${client}`,
            }
          : {
              name: target.file,
              platform: 'js',
              file,
              status: 'change',
              before,
              next,
              note: `${NAME} for ${client}`,
            },
    ],
    {
      yes,
      dryRun,
      nothingToDo: 'The server is there already.',
      next: ['Restart the client, or reload its servers, and allow apex-navigation when it asks.'],
    }
  );
}

module.exports = { mcp, mcpInstall, INSTRUCTIONS };
