// @ts-check
'use strict';

// A Babel plugin, development only: a module that imports `createNavigation` from the navigation accepts its own updates
// (`module.hot.accept()`). Metro then runs it again when it or a module only it imports changes, instead of reloading
// the app, and the navigation gives the new config to the app that runs.

const PACKAGE = '@apexnative/navigation';

/**
 * @param {{ template: { statement: { ast: (strings: TemplateStringsArray) => unknown } } }} babel
 */
function acceptPlugin({ template }) {
  return {
    name: 'apexnative-navigation-accept',
    visitor: {
      Program: {
        /**
         * Read on entering, before the other plugins turn the imports into requires.
         * @param {{ node: { body: ImportNode[] } }} path
         * @param {{ makesApp?: boolean }} state
         */
        enter(path, state) {
          state.makesApp = path.node.body.some(importsCreateNavigation);
        },
        /**
         * @param {{ pushContainer: (key: string, node: unknown) => void }} path
         * @param {{ makesApp?: boolean }} state
         */
        exit(path, state) {
          if (!state.makesApp) return;
          path.pushContainer(
            'body',
            template.statement.ast`if (module.hot) { module.hot.accept(); }`
          );
        },
      },
    },
  };
}

/**
 * @typedef {{ type: string, source?: { value: string },
 *   specifiers?: { type: string, imported?: { name?: string, value?: string } }[] }} ImportNode
 */

/** @param {ImportNode} node */
function importsCreateNavigation(node) {
  if (node.type !== 'ImportDeclaration' || node.source?.value !== PACKAGE) return false;
  return (node.specifiers ?? []).some(
    (specifier) =>
      specifier.type === 'ImportSpecifier' &&
      (specifier.imported?.name ?? specifier.imported?.value) === 'createNavigation'
  );
}

module.exports = acceptPlugin;
