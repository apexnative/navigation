// @ts-check
'use strict';

const { codegen } = require('../codegen');
const { doctor } = require('../doctor');
const { exec } = require('../exec');
const { icons } = require('../icons');
const { info } = require('../info');
const { links } = require('../links');
const { migrate } = require('../migrate');
const { screen } = require('../screen');
const { setup } = require('../setup');
const { splash } = require('../splash');
const { createUI } = require('../ui');
const { appMap } = require('./appMap');
const { loadDocs, readDoc, searchDocs } = require('./docs');

/** @typedef {import('./protocol').Tool} Tool */

// An install that sharp, a dependency or the pods may need takes minutes; nothing may print to stdout meanwhile.
const LONG = 20 * 60_000;

const APPLY = {
  apply: {
    type: 'boolean',
    description:
      'Write the changes. Without it the tool returns the plan: every file with its diff.',
  },
};

/**
 * The tools of the server, on the app in `root`: the commands of the CLI as JSON, the map of the app, the docs.
 * @param {string} root
 * @param {import('../exec').Exec} [run]
 * @returns {Tool[]}
 */
function createTools(root, run = exec) {
  const install = (/** @type {string} */ manager, /** @type {string[]} */ args) =>
    run(manager, args, { cwd: root, timeout: LONG }).ok;
  /**
   * A command that changes files: a plan, or written with `apply`.
   * @param {Record<string, unknown>} args
   */
  const changes = (args) => ({
    root,
    yes: args.apply === true,
    dryRun: args.apply !== true,
    json: true,
    ui: silentUI(),
    run,
  });
  return [
    {
      name: 'app_map',
      title: 'The map of the app',
      description:
        "The app's navigation from its code: each screen of screens with its props as TypeScript types them and whether it is React or native, the root or roots, the links of linking, and whether all screens render in one React tree. Read it before changing the navigation.",
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: { readOnlyHint: true, openWorldHint: false },
      run: async () => {
        const map = appMap(root, run);
        return { result: map, isError: 'error' in map };
      },
    },
    {
      name: 'docs_search',
      title: 'Search the docs',
      description:
        'Searches the docs of the installed navigation: guides, the API, the options. Gives sections with a line each; read one with docs_read.',
      inputSchema: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'Words to look for, such as "modal sheet detents".',
          },
          limit: { type: 'integer', minimum: 1, maximum: 20, description: '8 by default.' },
        },
        required: ['query'],
        additionalProperties: false,
      },
      annotations: { readOnlyHint: true, openWorldHint: false },
      run: async (args) => ({
        result: {
          results: searchDocs(loadDocs(), String(args.query ?? ''), Number(args.limit) || 8),
        },
      }),
    },
    {
      name: 'docs_read',
      title: 'Read the docs',
      description:
        'A page of the docs of the installed navigation as Markdown, or one of its sections: the page as docs_search gives it (such as "fundamentals/screens-and-props"), and the anchor of a section.',
      inputSchema: {
        type: 'object',
        properties: {
          page: { type: 'string', description: 'The page, such as "guides/deep-links".' },
          section: { type: 'string', description: 'The anchor of a section, such as "routes".' },
        },
        required: ['page'],
        additionalProperties: false,
      },
      annotations: { readOnlyHint: true, openWorldHint: false },
      run: async (args) => {
        const page = readDoc(
          loadDocs(),
          String(args.page ?? ''),
          args.section === undefined ? undefined : String(args.section)
        );
        return page
          ? { result: page }
          : {
              result: { error: `No page or section ${args.page} ${args.section ?? ''}`.trim() },
              isError: true,
            };
      },
    },
    {
      name: 'doctor',
      title: 'Check the app',
      description:
        'Checks what the navigation needs in the app and on this machine: React Native, the native files, the typed commands, the screens, the links, the icons, Xcode, CocoaPods, the JDK, the Android SDK, the devices. Each problem comes with its fix.',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: { readOnlyHint: true, openWorldHint: false },
      run: () =>
        output((write) => doctor({ root, fix: false, json: true, ui: silentUI(), run, write })),
    },
    {
      name: 'info',
      title: 'What the app is made with',
      description:
        'The versions the app is made and built with, for an issue: the system, Node, React Native, the navigation and how it renders, Xcode, CocoaPods, the JDK, the Android SDK.',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: { readOnlyHint: true, openWorldHint: false },
      run: () => output((write) => info({ root, json: true, run, write })),
    },
    {
      name: 'setup',
      title: 'Set up the app',
      description:
        'Sets the app up for the navigation: its native entry points, index.js and Navigation.ts, the typed commands, then the pods on a Mac.',
      inputSchema: {
        type: 'object',
        properties: {
          ...APPLY,
          skipPods: { type: 'boolean', description: 'Leave the pods for later.' },
        },
        additionalProperties: false,
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) => output(() => setup({ ...changes(args), skipPods: args.skipPods === true })),
    },
    {
      name: 'screen',
      title: 'Add a screen',
      description:
        'Adds a screen: its file and its entry in screens. A native screen (native, or ios / android) also gets its Swift and Kotlin files and its generated code.',
      inputSchema: {
        type: 'object',
        properties: {
          name: { type: 'string', description: 'PascalCase, such as Profile.' },
          dir: {
            type: 'string',
            description: 'The folder of the screens, from the root of the app.',
          },
          native: {
            type: 'boolean',
            description: 'A native screen on both platforms, SwiftUI and Compose.',
          },
          ios: { type: 'string', enum: ['swiftui', 'uikit'] },
          android: { type: 'string', enum: ['compose', 'views'] },
          ...APPLY,
        },
        required: ['name'],
        additionalProperties: false,
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) =>
        output(() =>
          screen({
            ...changes(args),
            name: String(args.name ?? ''),
            dir: optionalString(args.dir),
            native: args.native === true,
            ios: optionalString(args.ios),
            android: optionalString(args.android),
          })
        ),
    },
    {
      name: 'codegen',
      title: 'Generate the native code',
      description:
        'Generates the Swift and Kotlin of the native screens from their props in TypeScript, with the typed commands of native code.',
      inputSchema: { type: 'object', properties: { ...APPLY }, additionalProperties: false },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) => output(() => codegen({ ...changes(args) })),
    },
    {
      name: 'links',
      title: 'Declare the links',
      description:
        'Declares the prefixes of linking on the native side: the schemes in Info.plist and the manifest, the web sites in the entitlements and a verified filter of the manifest.',
      inputSchema: { type: 'object', properties: { ...APPLY }, additionalProperties: false },
      annotations: {
        readOnlyHint: false,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) => output(() => links({ ...changes(args) })),
    },
    {
      name: 'icons',
      title: 'Make the app icon',
      description:
        'Makes the app icon of both platforms from a logo and a color, or from a ready square icon; without options, again from the settings in package.json. Replaces the icon files.',
      inputSchema: {
        type: 'object',
        properties: {
          logo: {
            type: 'string',
            description: 'A PNG, JPEG, WebP or SVG on a transparent background.',
          },
          image: {
            type: 'string',
            description: 'A ready square icon, at least 1024 px, instead of logo.',
          },
          background: { type: 'string', description: 'A color such as #FFFFFF.' },
          darkLogo: { type: 'string' },
          logoSize: {
            type: 'number',
            description: 'The logo in percent of the icon, 60 by default.',
          },
          ...APPLY,
        },
        additionalProperties: false,
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: true,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) =>
        output(() =>
          icons({
            ...changes(args),
            install,
            options: {
              logo: optionalString(args.logo),
              image: optionalString(args.image),
              background: optionalString(args.background),
              darkLogo: optionalString(args.darkLogo),
              logoSize: args.logoSize === undefined ? undefined : String(args.logoSize),
            },
          })
        ),
    },
    {
      name: 'splash',
      title: 'Make the splash screen',
      description:
        'Makes the native splash screen of both platforms from a logo and colors; without options, again from the settings in package.json. Replaces the splash files.',
      inputSchema: {
        type: 'object',
        properties: {
          logo: { type: 'string' },
          background: { type: 'string' },
          darkLogo: { type: 'string' },
          darkBackground: { type: 'string' },
          logoWidth: { type: 'number', description: 'In points and dp, 100 by default.' },
          ...APPLY,
        },
        additionalProperties: false,
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: true,
        idempotentHint: true,
        openWorldHint: false,
      },
      run: (args) =>
        output(() =>
          splash({
            ...changes(args),
            install,
            options: {
              logo: optionalString(args.logo),
              background: optionalString(args.background),
              darkLogo: optionalString(args.darkLogo),
              darkBackground: optionalString(args.darkBackground),
              logoWidth: args.logoWidth === undefined ? undefined : String(args.logoWidth),
            },
          })
        ),
    },
    {
      name: 'migrate',
      title: 'Move from another navigation',
      description:
        'Moves the app from react-native-navigation or React Navigation: the package, the native files and what the CLI can move of the code; the rest is a report, place by place, with the step of the migration guide.',
      inputSchema: {
        type: 'object',
        properties: {
          from: { type: 'string', enum: ['react-native-navigation', 'react-navigation'] },
          ...APPLY,
        },
        additionalProperties: false,
      },
      annotations: {
        readOnlyHint: false,
        destructiveHint: true,
        idempotentHint: false,
        openWorldHint: false,
      },
      run: (args) => output(() => migrate({ ...changes(args), from: optionalString(args.from) })),
    },
  ];
}

/**
 * What a command printed as its JSON, as the result of a tool. The commands print their one document to stdout, which
 * is the channel of the protocol here: it is caught while they run (the server runs one request at a time, and writes
 * its own messages through the stdout it took before). A command that failed is an error the agent can act on.
 * @param {(write: (text: string) => void) => Promise<number>} command
 * @returns {Promise<{ result: unknown; isError: boolean }>}
 */
async function output(command) {
  let text = '';
  const write = (/** @type {string} */ chunk) => {
    text += chunk;
  };
  const original = process.stdout.write;
  process.stdout.write = /** @type {typeof process.stdout.write} */ (
    /** @type {unknown} */ (
      (/** @type {string | Uint8Array} */ chunk) => {
        write(String(chunk));
        return true;
      }
    )
  );
  try {
    await command(write);
  } finally {
    process.stdout.write = original;
  }
  /** @type {any} */
  let result;
  try {
    result = JSON.parse(text);
  } catch {
    return { result: { error: text.trim() || 'The command printed nothing.' }, isError: true };
  }
  return { result, isError: Array.isArray(result?.errors) && result.errors.length > 0 };
}

/** A terminal that says nothing: under a tool the commands print JSON only, and never ask. */
function silentUI() {
  return createUI({
    level: 'none',
    out: { isTTY: false, columns: 100, write: () => true },
  });
}

/**
 * @param {unknown} value
 * @returns {string | undefined}
 */
function optionalString(value) {
  return typeof value === 'string' && value !== '' ? value : undefined;
}

module.exports = { createTools, output };
