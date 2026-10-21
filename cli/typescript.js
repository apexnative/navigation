// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {typeof import('typescript')} TS */
/** @typedef {import('typescript').Node} Node */
/** @typedef {import('typescript').SourceFile} SourceFile */
/** @typedef {import('typescript').ObjectLiteralExpression} ObjectLiteralExpression */
/**
 * The object of the app's screens, `screens` of `createNavigation`, in the file it is written in.
 * @typedef {{ file: string; source: SourceFile; object: ObjectLiteralExpression }} ScreensObject
 */
/**
 * An expression of the app's code with the file it is written in.
 * @typedef {{ file: string; source: SourceFile; node: Node }} Value
 */

// A line the CLI writes stays one line up to this width, as Prettier keeps it by default.
const MAX_LINE = 100;
const EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx'];

/**
 * The app's TypeScript: React Native apps have it, and the CLI reads and edits the app's code with it.
 * @param {string} root
 * @returns {TS | undefined}
 */
function loadTypeScript(root) {
  try {
    return require(require.resolve('typescript', { paths: [root] }));
  } catch {
    return undefined;
  }
}

/**
 * @param {TS} ts
 * @param {string} file
 * @returns {SourceFile}
 */
function parse(ts, file) {
  const kind = /\.tsx$/.test(file)
    ? ts.ScriptKind.TSX
    : /\.jsx?$/.test(file)
      ? ts.ScriptKind.JSX
      : ts.ScriptKind.TS;
  return ts.createSourceFile(
    file,
    fs.readFileSync(file, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
    kind
  );
}

/**
 * The file a relative import names, from the importing file; undefined for a package.
 * @param {string} from
 * @param {string} specifier
 * @returns {string | undefined}
 */
function resolveImport(from, specifier) {
  if (!specifier.startsWith('.')) return undefined;
  const base = path.resolve(path.dirname(from), specifier);
  const candidates = [
    base,
    ...EXTENSIONS.map((extension) => base + extension),
    ...EXTENSIONS.map((extension) => path.join(base, 'index' + extension)),
  ];
  return candidates.find((file) => fs.existsSync(file) && fs.statSync(file).isFile());
}

/**
 * Calls `visit` on the node and everything under it.
 * @param {TS} ts
 * @param {Node} node
 * @param {(node: Node) => void} visit
 */
function walk(ts, node, visit) {
  visit(node);
  ts.forEachChild(node, (child) => walk(ts, child, visit));
}

/**
 * The module of the app's navigation and the name it exports the navigation under (`default` for a default export):
 * the export the entry point registers, `AppRegistry.registerComponent(name, Navigation)` or
 * `AppRegistry.registerComponent(name, () => Navigation)`.
 * @param {TS} ts
 * @param {string} entry
 * @returns {{ file: string; exported: string } | undefined}
 */
function navigationImport(ts, entry) {
  const source = parse(ts, entry);
  /** @type {string | undefined} */
  let registered;
  walk(ts, source, (node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      node.expression.name.text === 'registerComponent' &&
      node.arguments.length >= 2
    ) {
      const provider = node.arguments[1];
      if (ts.isIdentifier(provider)) registered = provider.text;
      else if (ts.isArrowFunction(provider) && ts.isIdentifier(provider.body))
        registered = provider.body.text;
    }
  });
  if (!registered) return undefined;
  for (const statement of source.statements) {
    if (!ts.isImportDeclaration(statement) || !statement.importClause) continue;
    const imported = importedNames(ts, statement).find(({ local }) => local === registered);
    if (imported) {
      const file = resolveImport(
        entry,
        /** @type {import('typescript').StringLiteral} */ (statement.moduleSpecifier).text
      );
      return file ? { file, exported: imported.exported } : undefined;
    }
  }
  // Made in the entry point itself.
  return { file: entry, exported: registered };
}

/**
 * The module of the app's navigation: the one whose export the entry point registers.
 * @param {TS} ts
 * @param {string} entry
 * @returns {string | undefined}
 */
function navigationModule(ts, entry) {
  return navigationImport(ts, entry)?.file;
}

/**
 * The value of a key of the `createNavigation({ ... })` call in the file, such as `screens` or `linking`: the
 * expression written there, or the one a const it names holds, in this file or one it imports.
 * @param {TS} ts
 * @param {string} file
 * @param {string} key
 * @returns {Value | undefined}
 */
function findConfig(ts, file, key) {
  const source = parse(ts, file);
  /** @type {Node | undefined} */
  let value;
  walk(ts, source, (node) => {
    if (!ts.isCallExpression(node) || !ts.isIdentifier(node.expression)) return;
    if (node.expression.text !== 'createNavigation') return;
    const config = node.arguments[0];
    if (!config || !ts.isObjectLiteralExpression(config)) return;
    for (const property of config.properties) {
      if (ts.isPropertyAssignment(property) && propertyName(ts, property) === key) {
        value = property.initializer;
      } else if (ts.isShorthandPropertyAssignment(property) && property.name.text === key) {
        value = property.name;
      }
    }
  });
  return value && valueOf(ts, file, source, value, 3);
}

/**
 * The object of the app's screens: `screens` of the `createNavigation({ ... })` call in the file, written there or in
 * a const it names, in this file or one it imports.
 * @param {TS} ts
 * @param {string} file
 * @returns {ScreensObject | undefined}
 */
function findScreens(ts, file) {
  const screens = findConfig(ts, file, 'screens');
  return screens && ts.isObjectLiteralExpression(screens.node)
    ? { file: screens.file, source: screens.source, object: screens.node }
    : undefined;
}

/**
 * The value of a property of an object literal, followed through the names it is written with.
 * @param {TS} ts
 * @param {Value} object an object literal
 * @param {string} key
 * @returns {Value | undefined}
 */
function propertyValue(ts, object, key) {
  if (!ts.isObjectLiteralExpression(object.node)) return undefined;
  for (const property of object.node.properties) {
    if (ts.isPropertyAssignment(property) && propertyName(ts, property) === key) {
      return valueOf(ts, object.file, object.source, property.initializer, 3);
    }
    if (ts.isShorthandPropertyAssignment(property) && property.name.text === key) {
      return valueOf(ts, object.file, object.source, property.name, 3);
    }
  }
  return undefined;
}

/**
 * The strings of an array literal of string literals; undefined when it is something else, or has anything else.
 * @param {TS} ts
 * @param {Value} value
 * @returns {string[] | undefined}
 */
function stringsOf(ts, value) {
  if (!ts.isArrayLiteralExpression(value.node)) return undefined;
  /** @type {string[]} */
  const strings = [];
  for (const element of value.node.elements) {
    const item = unwrap(ts, element);
    if (!ts.isStringLiteral(item) && !ts.isNoSubstitutionTemplateLiteral(item)) return undefined;
    strings.push(item.text);
  }
  return strings;
}

/**
 * The names of the properties of an object literal, as written.
 * @param {TS} ts
 * @param {Value} value
 * @returns {string[]}
 */
function keysOf(ts, value) {
  if (!ts.isObjectLiteralExpression(value.node)) return [];
  return value.node.properties.flatMap((property) => {
    const name = propertyName(ts, property);
    return name === undefined ? [] : [name];
  });
}

/**
 * Whether the file exports a type or an interface of the name, declared there or passed on (`export type { … }`).
 * @param {TS} ts
 * @param {string} file
 * @param {string} name
 */
function exportsType(ts, file, name) {
  const exported = (/** @type {Node} */ statement) =>
    ts.canHaveModifiers(statement) &&
    (ts.getModifiers(statement) ?? []).some(
      (modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword
    );
  return parse(ts, file).statements.some((statement) => {
    if (ts.isTypeAliasDeclaration(statement) || ts.isInterfaceDeclaration(statement)) {
      return statement.name.text === name && exported(statement);
    }
    return (
      ts.isExportDeclaration(statement) &&
      statement.exportClause !== undefined &&
      ts.isNamedExports(statement.exportClause) &&
      statement.exportClause.elements.some((element) => element.name.text === name)
    );
  });
}

/**
 * The expression a node is, or names: followed through a const of the file, or an import, `depth` names at most.
 * @param {TS} ts
 * @param {string} file
 * @param {SourceFile} source
 * @param {Node} node
 * @param {number} depth how many names it still follows
 * @returns {Value | undefined}
 */
function valueOf(ts, file, source, node, depth) {
  const expression = unwrap(ts, node);
  if (!ts.isIdentifier(expression)) return { file, source, node: expression };
  if (depth === 0) return undefined;
  return declarationOf(ts, file, source, expression.text, depth);
}

/**
 * The expression a const of the file holds, or the one an import of the file brings under that name.
 * @param {TS} ts
 * @param {string} file
 * @param {SourceFile} source
 * @param {string} name
 * @param {number} depth
 * @returns {Value | undefined}
 */
function declarationOf(ts, file, source, name, depth) {
  for (const statement of source.statements) {
    if (ts.isVariableStatement(statement)) {
      for (const declaration of statement.declarationList.declarations) {
        if (
          ts.isIdentifier(declaration.name) &&
          declaration.name.text === name &&
          declaration.initializer
        ) {
          return valueOf(ts, file, source, declaration.initializer, depth - 1);
        }
      }
    }
    if (ts.isImportDeclaration(statement) && statement.importClause) {
      const imported = importedNames(ts, statement).find(({ local }) => local === name);
      if (!imported) continue;
      const target = resolveImport(
        file,
        /** @type {import('typescript').StringLiteral} */ (statement.moduleSpecifier).text
      );
      if (!target || imported.exported === 'default') return undefined;
      return declarationOf(ts, target, parse(ts, target), imported.exported, depth - 1);
    }
  }
  return undefined;
}

/**
 * The names an import brings: each under the name the file uses, with the one the module exports.
 * @param {TS} ts
 * @param {import('typescript').ImportDeclaration} statement
 * @returns {{ local: string; exported: string }[]}
 */
function importedNames(ts, statement) {
  const clause = statement.importClause;
  if (!clause) return [];
  const names = clause.name ? [{ local: clause.name.text, exported: 'default' }] : [];
  if (clause.namedBindings && ts.isNamedImports(clause.namedBindings)) {
    for (const element of clause.namedBindings.elements) {
      names.push({
        local: element.name.text,
        exported: (element.propertyName ?? element.name).text,
      });
    }
  }
  return names;
}

/**
 * An expression without what only types it: `as const`, `satisfies`, parentheses.
 * @param {TS} ts
 * @param {Node} node
 * @returns {Node}
 */
function unwrap(ts, node) {
  if (
    ts.isAsExpression(node) ||
    ts.isSatisfiesExpression(node) ||
    ts.isParenthesizedExpression(node)
  ) {
    return unwrap(ts, node.expression);
  }
  return node;
}

/**
 * @param {TS} ts
 * @param {import('typescript').ObjectLiteralElementLike} property
 * @returns {string | undefined}
 */
function propertyName(ts, property) {
  const name = property.name;
  if (!name) return undefined;
  if (ts.isIdentifier(name) || ts.isStringLiteral(name)) return name.text;
  return undefined;
}

/**
 * The text of the screens' file with a screen added: last in `screens`, written as the others are (on one line, or one
 * per line with their indentation; a line too long goes one per line), with its import after the others. `done` when
 * the screens have the name already.
 * @param {TS} ts
 * @param {ScreensObject} screens
 * @param {{ name: string; value: string; importFrom: string }} screen `value` is what the file imports; `importFrom`
 *   is the module's path from the screens' file, such as `./src/screens/Profile`
 * @returns {{ status: 'change'; next: string } | { status: 'done' }}
 */
function addScreen(ts, screens, screen) {
  const { source, object } = screens;
  if (object.properties.some((property) => propertyName(ts, property) === screen.name)) {
    return { status: 'done' };
  }
  const text = source.text;
  const entry = screen.value === screen.name ? screen.name : `${screen.name}: ${screen.value}`;
  const start = object.getStart(source);
  const properties = object.properties;
  const lineIndent = indentationAt(text, start);
  /** @type {string} */
  let next;
  if (!text.slice(start, object.end).includes('\n')) {
    const entries = [...properties.map((property) => property.getText(source)), entry];
    const oneLine = `{ ${entries.join(', ')} }`;
    const lineLength = lineAt(text, start).length - (object.end - start) + oneLine.length;
    const written =
      lineLength <= MAX_LINE
        ? oneLine
        : `{\n${entries.map((item) => `${lineIndent}  ${item},`).join('\n')}\n${lineIndent}}`;
    next = text.slice(0, start) + written + text.slice(object.end);
  } else if (properties.length === 0) {
    next =
      text.slice(0, start) + `{\n${lineIndent}  ${entry},\n${lineIndent}}` + text.slice(object.end);
  } else {
    const last = properties[properties.length - 1];
    const indent = indentationAt(text, last.getStart(source));
    // After the comma that ends the last property, when the object has one.
    const comma = properties.hasTrailingComma ? text.indexOf(',', last.end) + 1 : -1;
    next =
      comma > 0
        ? text.slice(0, comma) + `\n${indent}${entry},` + text.slice(comma)
        : text.slice(0, last.end) + `,\n${indent}${entry}` + text.slice(last.end);
  }
  return {
    status: 'change',
    next: addImport(ts, next, screens.file, screen.value, screen.importFrom),
  };
}

/**
 * The text with `import { name } from 'module'` after its imports, unless it imports the name already.
 * @param {TS} ts
 * @param {string} text
 * @param {string} file
 * @param {string} name
 * @param {string} module
 */
function addImport(ts, text, file, name, module) {
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
  const imports = source.statements.filter(ts.isImportDeclaration);
  if (imports.some((statement) => importedNames(ts, statement).some(({ local }) => local === name)))
    return text;
  const line = `import { ${name} } from '${module}';`;
  if (imports.length === 0) return `${line}\n${text}`;
  const end = imports[imports.length - 1].end;
  return text.slice(0, end) + `\n${line}` + text.slice(end);
}

/**
 * The indentation of the line a position is on.
 * @param {string} text
 * @param {number} position
 */
function indentationAt(text, position) {
  return /^[ \t]*/.exec(lineAt(text, position))?.[0] ?? '';
}

/**
 * The line a position is on.
 * @param {string} text
 * @param {number} position
 */
function lineAt(text, position) {
  const start = text.lastIndexOf('\n', position - 1) + 1;
  const end = text.indexOf('\n', position);
  return text.slice(start, end < 0 ? text.length : end);
}

/**
 * How one file imports another: a relative path without the extension, `./` in front.
 * @param {string} from the importing file
 * @param {string} to the imported file
 */
function importPath(from, to) {
  const relative = path
    .relative(path.dirname(from), to)
    .replace(/\.(tsx?|jsx?)$/, '')
    .split(path.sep)
    .join('/');
  return relative.startsWith('.') ? relative : `./${relative}`;
}

module.exports = {
  loadTypeScript,
  resolveImport,
  navigationImport,
  navigationModule,
  findConfig,
  findScreens,
  propertyValue,
  stringsOf,
  keysOf,
  exportsType,
  addScreen,
  importPath,
};
