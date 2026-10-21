// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { PACKAGE } = require('../app');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('typescript').Node} Node */
/** @typedef {import('typescript').Expression} Expression */

const OLD = 'react-native-navigation';

/**
 * The entry point of an app on react-native-navigation, moved: its `registerComponent` calls become the screens of a
 * new Navigation.ts, the `setRoot` of its launched listener the first root, and the entry point registers the
 * navigation. Only when the entry point holds all of them, in forms the CLI reads: otherwise nothing moves, and the
 * report lists them.
 * @param {import('../typescript').TS} ts
 * @param {string} root
 * @param {string} entry the app's index.js
 * @param {string[]} files the app's code, where other registrations could be
 * @returns {{ changes: FileChange[]; moved: Set<string> } | { reason: string }}
 */
function planEntry(ts, root, entry, files) {
  const text = fs.readFileSync(entry, 'utf8');
  const source = ts.createSourceFile(entry, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const oldImport = source.statements.find(
    (statement) =>
      ts.isImportDeclaration(statement) &&
      ts.isStringLiteral(statement.moduleSpecifier) &&
      statement.moduleSpecifier.text === OLD
  );
  const local = oldImport && namedImport(ts, /** @type {any} */ (oldImport), 'Navigation');
  if (!oldImport || !local)
    return { reason: `${path.basename(entry)} doesn't import Navigation of ${OLD}` };
  const elsewhere = files.find(
    (file) =>
      file !== entry &&
      /\.registerComponent(?:WithRedux)?\(/.test(fs.readFileSync(file, 'utf8')) &&
      fs.readFileSync(file, 'utf8').includes(OLD)
  );
  if (elsewhere) {
    return { reason: `screens are registered in ${path.relative(root, elsewhere)} too` };
  }
  const navigationFile = path.join(
    root,
    /\.tsx?$/.test(entry) || fs.existsSync(path.join(root, 'tsconfig.json'))
      ? 'Navigation.ts'
      : 'Navigation.js'
  );
  if (fs.existsSync(navigationFile))
    return { reason: `${path.basename(navigationFile)} exists already` };

  /** @type {{ name: string; value: string; uses: string[] }[]} */
  const screens = [];
  /** @type {string | undefined} */
  let rootLayout;
  /** @type {Set<Node>} */
  const removed = new Set([oldImport]);
  for (const statement of source.statements) {
    if (!ts.isExpressionStatement(statement) || !ts.isCallExpression(statement.expression))
      continue;
    const call = statement.expression;
    if (isCall(ts, call, local, 'registerComponent')) {
      const screen = registration(ts, call, source);
      if (!screen)
        return {
          reason: `a registerComponent the CLI can't read: ${statement.getText(source).slice(0, 80)}`,
        };
      screens.push(screen);
      removed.add(statement);
    } else if (isLaunchedListener(ts, call, local)) {
      const layout = launchedRoot(ts, call, local, source);
      if (!layout)
        return {
          reason: 'the launched listener does more than one setRoot of stacks, tabs and screens',
        };
      rootLayout = layout;
      removed.add(statement);
    }
  }
  if (screens.length === 0 || !rootLayout) {
    return {
      reason: `${path.basename(entry)} has no registerComponent or no setRoot in registerAppLaunchedListener`,
    };
  }

  // The imports of the components move to Navigation.ts whole: an import the rest of index.js needs too, or a
  // component index.js makes itself, keeps everything where it is, for the report.
  const used = new Set(screens.flatMap(({ uses }) => uses));
  const kept = source.statements.filter((statement) => !removed.has(statement));
  const keptCode = kept
    .filter((statement) => !ts.isImportDeclaration(statement))
    .map((statement) => statement.getText(source))
    .join('\n');
  const mentions = (/** @type {string} */ name) => new RegExp(`\\b${name}\\b`).test(keptCode);
  const oldNames = boundNames(ts, /** @type {any} */ (oldImport)).filter(mentions);
  if (oldNames.length > 0) {
    return {
      reason: `index.js uses ${oldNames.join(', ')} of ${OLD} besides the screens and the root`,
    };
  }
  if (/\.registerComponent\w*\(/.test(keptCode)) {
    return { reason: "index.js registers a screen in a way the CLI can't move" };
  }
  /** @type {string[]} */
  const movedImports = [];
  /** @type {Set<string>} */
  const imported = new Set();
  for (const statement of kept) {
    if (!ts.isImportDeclaration(statement) || !statement.importClause) continue;
    const names = boundNames(ts, statement);
    if (!names.some((name) => used.has(name))) continue;
    if (!names.every((name) => used.has(name) && !mentions(name))) {
      return {
        reason: `an import of index.js serves the screens and the rest of it: ${statement.getText(source)}`,
      };
    }
    movedImports.push(statement.getText(source));
    names.forEach((name) => imported.add(name));
    removed.add(statement);
  }
  const missing = [...used].filter((name) => !imported.has(name));
  if (missing.length > 0) {
    return { reason: `the screens use ${missing.join(', ')}, which index.js doesn't import` };
  }

  const builders = ['Stack', 'Tabs', 'Tab'].filter((name) =>
    new RegExp(`\\b${name}\\(`).test(rootLayout ?? '')
  );
  const key = (/** @type {string} */ name) =>
    /^[A-Za-z_$][\w$]*$/.test(name) ? name : `'${name}'`;
  const navigation = [
    `import { ${['createNavigation', ...builders].join(', ')} } from '${PACKAGE}';`,
    ...movedImports,
    '',
    '// The screens of registerComponent, under the names the app gave them, and the root of its setRoot.',
    'export const Navigation = createNavigation({',
    '  screens: {',
    ...screens.map(({ name, value }) => `    ${key(name)}: ${value},`),
    '  },',
    `  root: ${rootLayout.split('\n').join('\n  ')},`,
    '});',
    '',
  ].join('\n');

  // What stays, each statement with the comments above it; the file's own comment on top is kept apart.
  const withComments = (/** @type {import('typescript').Statement} */ statement) =>
    statement === source.statements[0]
      ? statement.getText(source)
      : text.slice(statement.getFullStart(), statement.getEnd()).replace(/^\s*\n/, '');
  const remaining = source.statements.filter((statement) => !removed.has(statement));
  const imports = remaining.filter(ts.isImportDeclaration).map(withComments);
  const rest = remaining
    .filter((statement) => !ts.isImportDeclaration(statement))
    .map(withComments);
  const hasName = /import\s*\{\s*name(?:\s+as\s+\w+)?\s*\}\s*from\s*['"]\.\/app\.json['"]/.test(
    imports.join('\n')
  );
  const nextEntry = [
    ...leadingComment(text, source),
    ...imports,
    `import { AppRegistry } from '${PACKAGE}';`,
    ...(hasName ? [] : ["import { name } from './app.json';"]),
    "import { Navigation } from './Navigation';",
    '',
    ...(rest.length > 0 ? [...rest, ''] : []),
    `AppRegistry.registerComponent(${hasName ? nameOf(imports) : 'name'}, Navigation);`,
    '',
  ].join('\n');

  // Every line of what moved: the report leaves them out, as they are gone from the entry point.
  /** @type {Set<string>} */
  const moved = new Set();
  for (const node of removed) {
    const first = source.getLineAndCharacterOfPosition(node.getStart(source)).line;
    const last = source.getLineAndCharacterOfPosition(node.getEnd()).line;
    for (let line = first; line <= last; line++)
      moved.add(`${path.relative(root, entry)}:${line + 1}`);
  }
  return {
    changes: [
      {
        name: path.basename(entry),
        platform: 'js',
        file: entry,
        status: 'change',
        before: text,
        next: nextEntry,
        note: 'registers the navigation',
      },
      {
        name: path.basename(navigationFile),
        platform: 'js',
        file: navigationFile,
        status: 'create',
        next: navigation,
        note: `${screens.length} ${screens.length === 1 ? 'screen' : 'screens'}, the root`,
      },
    ],
    moved,
  };
}

/**
 * One `registerComponent('Name', () => Component)`: the name and the component, or a wrapper of it such as
 * `() => gestureHandlerRootHOC(Home)`. Undefined for a provider with a body, or the forms with Redux.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').CallExpression} call
 * @param {import('typescript').SourceFile} source
 */
function registration(ts, call, source) {
  const [name, provider, ...rest] = call.arguments;
  if (!name || !ts.isStringLiteralLike(name) || !provider || rest.length > 0) return undefined;
  if (!ts.isArrowFunction(provider) || ts.isBlock(provider.body)) return undefined;
  /** @type {Set<string>} */
  const uses = new Set();
  const visit = (/** @type {Node} */ node) => {
    // The names it reads; not those after a dot, or the keys of an object.
    if (
      ts.isIdentifier(node) &&
      !(ts.isPropertyAccessExpression(node.parent) && node.parent.name === node) &&
      !(ts.isPropertyAssignment(node.parent) && node.parent.name === node)
    ) {
      uses.add(node.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(provider.body);
  return { name: name.text, value: provider.body.getText(source), uses: [...uses] };
}

/**
 * The first root of `Navigation.events().registerAppLaunchedListener(() => Navigation.setRoot({ root }))` as the
 * builders write it: a screen by its name, `Stack(…)` of screens, `Tabs(Tab(…).label(…), …)` of a stack each.
 * Undefined when the listener does more, or the layout has more than these.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').CallExpression} call
 * @param {string} local
 * @param {import('typescript').SourceFile} source
 * @returns {string | undefined}
 */
function launchedRoot(ts, call, local, source) {
  const listener = call.arguments[0];
  if (!listener || !(ts.isArrowFunction(listener) || ts.isFunctionExpression(listener)))
    return undefined;
  const body = listener.body;
  /** @type {Expression | undefined} */
  let setRoot;
  if (ts.isBlock(body)) {
    if (body.statements.length !== 1) return undefined;
    const only = body.statements[0];
    if (ts.isExpressionStatement(only)) setRoot = only.expression;
    else if (ts.isReturnStatement(only)) setRoot = only.expression;
  } else {
    setRoot = body;
  }
  if (!setRoot || !ts.isCallExpression(setRoot) || !isCall(ts, setRoot, local, 'setRoot'))
    return undefined;
  const argument = setRoot.arguments[0];
  const layout =
    argument && ts.isObjectLiteralExpression(argument) && property(ts, argument, 'root');
  return layout ? layoutOf(ts, layout, source, 'root') : undefined;
}

/**
 * A layout of the old API as the builders write it.
 * @param {import('../typescript').TS} ts
 * @param {Expression} node
 * @param {import('typescript').SourceFile} source
 * @param {'root' | 'stack' | 'tab'} place
 * @returns {string | undefined}
 */
function layoutOf(ts, node, source, place) {
  if (!ts.isObjectLiteralExpression(node) || node.properties.length !== 1) return undefined;
  // A layout with props, options or an id of its own isn't moved: none of it may go unseen. Only the label of a tab is
  // read, from the options of its stack.
  const component = property(ts, node, 'component');
  if (component) {
    if (!ts.isObjectLiteralExpression(component) || !onlyKeys(component, ['name']))
      return undefined;
    const name = property(ts, component, 'name');
    return name && ts.isStringLiteralLike(name) ? `'${name.text}'` : undefined;
  }
  const stack = property(ts, node, 'stack');
  if (stack && place !== 'stack') {
    if (!ts.isObjectLiteralExpression(stack)) return undefined;
    const labelOnly =
      place === 'tab' &&
      onlyKeys(stack, ['children', 'options']) &&
      tabLabel(ts, stack) !== undefined;
    if (!onlyKeys(stack, ['children']) && !labelOnly) return undefined;
    const children = property(ts, stack, 'children');
    if (!children || !ts.isArrayLiteralExpression(children) || children.elements.length === 0)
      return undefined;
    const screens = children.elements.map((child) => layoutOf(ts, child, source, 'stack'));
    if (screens.some((screen) => !screen)) return undefined;
    if (place === 'tab') {
      // A tab puts a stack around its screen: a stack of one screen is the tab.
      if (screens.length !== 1) return undefined;
      const label = tabLabel(ts, /** @type {any} */ (stack));
      return `Tab(${screens[0]})${label ? `.label('${label}')` : ''}`;
    }
    return `Stack(${screens.join(', ')})`;
  }
  const tabs = property(ts, node, 'bottomTabs');
  if (tabs && place === 'root') {
    if (!ts.isObjectLiteralExpression(tabs) || !onlyKeys(tabs, ['children'])) return undefined;
    const children = property(ts, tabs, 'children');
    if (!children || !ts.isArrayLiteralExpression(children)) return undefined;
    const items = children.elements.map((child) => {
      const tab = layoutOf(ts, child, source, 'tab');
      return tab && !tab.startsWith('Tab(') ? `Tab(${tab})` : tab;
    });
    if (items.some((item) => !item)) return undefined;
    return `Tabs(\n  ${items.join(',\n  ')},\n)`;
  }
  return undefined;
}

/**
 * Whether an object literal has these keys at most.
 * @param {import('typescript').ObjectLiteralExpression} object
 * @param {string[]} keys
 */
function onlyKeys(object, keys) {
  return object.properties.every(
    (item) => item.name !== undefined && keys.includes(item.name.getText())
  );
}

/**
 * The label of a tab: `options.bottomTab.text` of its stack, when the options have nothing else.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').ObjectLiteralExpression} stack
 * @returns {string | undefined}
 */
function tabLabel(ts, stack) {
  const options = property(ts, stack, 'options');
  if (options && (!ts.isObjectLiteralExpression(options) || !onlyKeys(options, ['bottomTab']))) {
    return undefined;
  }
  const bottomTab =
    options && ts.isObjectLiteralExpression(options) && property(ts, options, 'bottomTab');
  // An icon or a badge of the tab isn't moved either.
  const text =
    bottomTab &&
    ts.isObjectLiteralExpression(bottomTab) &&
    onlyKeys(bottomTab, ['text']) &&
    property(ts, bottomTab, 'text');
  return text && ts.isStringLiteralLike(text) ? text.text.replace(/'/g, "\\'") : undefined;
}

/**
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').ObjectLiteralExpression} object
 * @param {string} key
 * @returns {Expression | undefined}
 */
function property(ts, object, key) {
  for (const item of object.properties) {
    if (ts.isPropertyAssignment(item) && item.name.getText() === key) return item.initializer;
  }
  return undefined;
}

/**
 * Whether a call is `<local>.<method>(…)`.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').CallExpression} call
 * @param {string} local
 * @param {string} method
 */
function isCall(ts, call, local, method) {
  return (
    ts.isPropertyAccessExpression(call.expression) &&
    call.expression.name.text === method &&
    ts.isIdentifier(call.expression.expression) &&
    call.expression.expression.text === local
  );
}

/**
 * Whether a call is `<local>.events().registerAppLaunchedListener(…)`.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').CallExpression} call
 * @param {string} local
 */
function isLaunchedListener(ts, call, local) {
  const callee = call.expression;
  return (
    ts.isPropertyAccessExpression(callee) &&
    callee.name.text === 'registerAppLaunchedListener' &&
    ts.isCallExpression(callee.expression) &&
    isCall(ts, callee.expression, local, 'events')
  );
}

/**
 * The local name of a named import, `import { Navigation as N }` → `N`.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').ImportDeclaration} statement
 * @param {string} exported
 */
function namedImport(ts, statement, exported) {
  const bindings = statement.importClause?.namedBindings;
  if (!bindings || !ts.isNamedImports(bindings)) return undefined;
  return bindings.elements.find(
    (element) => (element.propertyName ?? element.name).text === exported
  )?.name.text;
}

/**
 * The names an import brings into the file.
 * @param {import('../typescript').TS} ts
 * @param {import('typescript').ImportDeclaration} statement
 * @returns {string[]}
 */
function boundNames(ts, statement) {
  const clause = statement.importClause;
  if (!clause) return [];
  const names = clause.name ? [clause.name.text] : [];
  const bindings = clause.namedBindings;
  if (bindings && ts.isNamedImports(bindings))
    names.push(...bindings.elements.map((element) => element.name.text));
  if (bindings && ts.isNamespaceImport(bindings)) names.push(bindings.name.text);
  return names;
}

/**
 * The comment the file starts with (the template's `@format`), kept on top.
 * @param {string} text
 * @param {import('typescript').SourceFile} source
 * @returns {string[]}
 */
function leadingComment(text, source) {
  const first = source.statements[0];
  const head = first ? text.slice(0, first.getStart(source)).trim() : '';
  return head ? [head, ''] : [];
}

/**
 * The local name of `name` from app.json in the imports.
 * @param {string[]} imports
 */
function nameOf(imports) {
  return /import\s*\{\s*name(?:\s+as\s+(\w+))?\s*\}/.exec(imports.join('\n'))?.[1] ?? 'name';
}

module.exports = { planEntry };
