#!/usr/bin/env node
// @ts-check
'use strict';

const path = require('node:path');
const { parseArgs } = require('node:util');
const { codegen } = require('./codegen');
const { doctor } = require('./doctor');
const { icons } = require('./icons');
const { info } = require('./info');
const { init } = require('./init');
const { links, linksFiles, linksOpen } = require('./links');
const { mcp, mcpInstall } = require('./mcp');
const { migrate } = require('./migrate');
const { screen } = require('./screen');
const { setup } = require('./setup');
const { upgrade } = require('./upgrade');
const { splash } = require('./splash');

const HELP = `Usage: apex-navigation <command> [options]

Commands:
  init Name    Makes a new React Native app with the navigation set up: Home and Details in a stack, its
               dependencies and pods installed.
  setup        Sets up the app for the navigation: its native files and its entry point, the types of its
               commands, then the pods.
  screen Name  Adds a screen: its file, with its props and options, and its line in screens. A native screen
               (--native, --ios, --android) also gets its Swift and Kotlin files and its generated code.
  codegen      Generates the Swift and Kotlin side of the native screens from their props in TypeScript, with
               the typed commands of native code.
  links        Declares the links of linking on the native side: the schemes in Info.plist and the manifest, the
               web sites in the entitlements and a verified filter of the manifest.
  links open url
               Opens a link as a tap would, on the booted iOS simulator and the connected Android device.
  links files  Writes the files the app's web site serves for its links: apple-app-site-association and
               assetlinks.json.
  icons        Makes the app icon of both platforms from a logo and a color, or from a ready square icon.
               Without options, makes it again from the settings in package.json.
  splash       Makes the native splash from a logo and colors, light and dark: a storyboard on iOS, a splash
               theme on Android. Without options, makes it again from the settings in package.json.
  migrate      Moves the app from react-native-navigation or React Navigation: the package, the native files and
               what it can of the code; the rest is a report, place by place, with the step of the guide.
  upgrade [version]
               Upgrades the navigation (latest by default): the package, then its setup, codegen and pods, and
               what changed since the version the app has.
  doctor       Checks what the navigation needs in the app and on this machine.
  info         Prints what the app is made with and what builds it, to paste into an issue.
  mcp          Runs the MCP server on stdio, for AI coding agents: the commands, the map of the app, the docs.
  mcp install client
               Adds the server to the config of claude, cursor or vscode in the project, or prints the line for
               codex.

Options:
  --yes        Applies the changes without asking.
  --dry-run    Shows the changes and writes nothing.
  --dir path   screen: the folder of the app's screens, from its root (src/screens by default); kept in
               package.json for the next screens.
  --native     screen: a native screen on both platforms, SwiftUI and Jetpack Compose.
  --ios kind   screen: a native screen on iOS, swiftui or uikit (alone: iOS only).
  --android k  screen: a native screen on Android, compose or views (alone: Android only).
  --version v  init: the React Native of the app, a version or a tag (the one the navigation was checked
               with by default).
  --template name
               init: the starter, stack (Home and Details, the default), tabs (Home, Profile, Settings) or auth
               (Login, then the tabs).
  --pm name    init: npm, yarn or bun (the one the command was run with by default).
  --directory  init: the folder of the app (its name by default).
  --package-name id
               init: the Android package and iOS bundle id, such as com.example.app.
  --title text init: the name the app shows.
  --skip-install
               init: makes the app without installing its dependencies and pods.
  --skip-pods  init, setup, migrate: leaves the pods for later.
  --root path  mcp: the app it serves (the folder the client starts it in by default).
  --from name  migrate: react-native-navigation or react-navigation (the one in package.json by default).
  --skip-git-init
               init: no git repository.
  --image path icons: a ready square icon, at least 1024 px, instead of --logo.
  --logo-size n
               icons: the logo's width in percent of the icon (60 by default).
  --logo path  splash, icons: the logo, PNG, JPEG, WebP or SVG.
  --background color
               splash, icons: the background, such as #FFFFFF (the default).
  --dark-logo path, --dark-background color
               splash: the same in dark mode (the light ones without them); icons: --dark-logo only.
  --logo-width n
               splash: the logo's width in points and dp (100 by default; within 192 on Android 12+).
  --fix        doctor: makes the fixes it can (setup, links, icons, codegen, pod install), asking
               before each.
  --platform ios|android
               links open, links files: that platform only.
  --cold       links open: quits the app first, so the link launches it.
  --team-id id links files: the team of the Apple Developer account (the Xcode project's by default).
  --fingerprint sha256
               links files: the SHA-256 of a signing key for assetlinks.json, such as the release key of Google
               Play signing; repeat it for several. The debug key's is added by itself.
  --out path   links files: the folder of the files (.well-known by default).
  --json       Prints the result as one JSON document, for CI, tools and AI agents. A command that changes files
               writes them only with --yes.
  --help       Shows this help.

Exit codes: 0 done (or nothing to do), 1 a problem, or files left to change by hand.`;

async function main() {
  const { values, positionals } = parseArgs({
    allowPositionals: true,
    options: {
      yes: { type: 'boolean' },
      'dry-run': { type: 'boolean' },
      dir: { type: 'string' },
      native: { type: 'boolean' },
      ios: { type: 'string' },
      android: { type: 'string' },
      fix: { type: 'boolean' },
      version: { type: 'string' },
      pm: { type: 'string' },
      directory: { type: 'string' },
      'package-name': { type: 'string' },
      title: { type: 'string' },
      'skip-install': { type: 'boolean' },
      'skip-pods': { type: 'boolean' },
      'skip-git-init': { type: 'boolean' },
      template: { type: 'string' },
      platform: { type: 'string' },
      cold: { type: 'boolean' },
      'team-id': { type: 'string' },
      fingerprint: { type: 'string', multiple: true },
      out: { type: 'string' },
      from: { type: 'string' },
      root: { type: 'string' },
      image: { type: 'string' },
      'logo-size': { type: 'string' },
      logo: { type: 'string' },
      background: { type: 'string' },
      'dark-logo': { type: 'string' },
      'dark-background': { type: 'string' },
      'logo-width': { type: 'string' },
      json: { type: 'boolean' },
      help: { type: 'boolean' },
    },
  });
  const [command] = positionals;
  if (values.help || command === undefined || command === 'help') {
    console.log(HELP);
    return 0;
  }
  if (command === 'init') {
    return init({
      cwd: process.cwd(),
      name: positionals[1],
      version: values.version,
      pm: values.pm,
      directory: values.directory,
      packageName: values['package-name'],
      title: values.title,
      template: values.template,
      skipGitInit: Boolean(values['skip-git-init']),
      skipInstall: Boolean(values['skip-install']),
      skipPods: Boolean(values['skip-pods']),
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'setup') {
    return setup({
      root: process.cwd(),
      skipPods: Boolean(values['skip-pods']),
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'screen') {
    return screen({
      root: process.cwd(),
      name: positionals[1],
      dir: values.dir,
      native: Boolean(values.native),
      ios: values.ios,
      android: values.android,
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'codegen') {
    return codegen({
      root: process.cwd(),
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'splash') {
    return splash({
      root: process.cwd(),
      options: {
        logo: values.logo,
        background: values.background,
        darkLogo: values['dark-logo'],
        darkBackground: values['dark-background'],
        logoWidth: values['logo-width'],
      },
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'icons') {
    return icons({
      root: process.cwd(),
      options: {
        logo: values.logo,
        image: values.image,
        background: values.background,
        darkLogo: values['dark-logo'],
        logoSize: values['logo-size'],
      },
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'upgrade') {
    return upgrade({
      root: process.cwd(),
      version: positionals[1],
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'migrate') {
    return migrate({
      root: process.cwd(),
      from: values.from,
      skipPods: Boolean(values['skip-pods']),
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'mcp' && positionals[1] === 'install') {
    return mcpInstall({
      root: process.cwd(),
      client: positionals[2],
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'mcp') {
    return mcp({ root: values.root ? path.resolve(values.root) : process.cwd() });
  }
  if (command === 'info') {
    return info({ root: process.cwd(), json: Boolean(values.json) });
  }
  if (command === 'links' && positionals[1] === 'open') {
    return linksOpen({
      root: process.cwd(),
      url: positionals[2],
      platform: values.platform,
      cold: Boolean(values.cold),
      json: Boolean(values.json),
    });
  }
  if (command === 'links' && positionals[1] === 'files') {
    return linksFiles({
      root: process.cwd(),
      teamId: values['team-id'],
      fingerprints: values.fingerprint,
      out: values.out,
      platform: values.platform,
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'links' && positionals[1] === undefined) {
    return links({
      root: process.cwd(),
      yes: Boolean(values.yes),
      dryRun: Boolean(values['dry-run']),
      json: Boolean(values.json),
    });
  }
  if (command === 'links') {
    console.error(`Unknown command "links ${positionals[1]}": links, links open or links files.`);
    return 1;
  }
  if (command === 'doctor') {
    return doctor({ root: process.cwd(), fix: Boolean(values.fix), json: Boolean(values.json) });
  }
  console.error(`Unknown command "${command}".\n\n${HELP}`);
  return 1;
}

main().then(
  (code) => {
    process.exitCode = code;
  },
  (error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
);
