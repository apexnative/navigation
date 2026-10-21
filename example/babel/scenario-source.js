/**
 * Puts the source of every run of a scenario next to it, so the Showcase shows the code a button runs as it is in the
 * file. In `defineScenario({ runs: { Title: code } })` it adds `code: { Title: '<source>' }` and
 * `file: '<path from example/>'`.
 */
const path = require('path');

function dedent(source) {
  const lines = source.split('\n');
  const indents = lines
    .slice(1)
    .filter((line) => line.trim() !== '')
    .map((line) => line.match(/^ */)[0].length);
  const cut = indents.length ? Math.min(...indents) : 0;
  return [lines[0], ...lines.slice(1).map((line) => line.slice(Math.min(cut, line.length)))]
    .join('\n')
    .trimEnd();
}

function keyName(t, key) {
  if (t.isIdentifier(key)) return key.name;
  if (t.isStringLiteral(key)) return key.value;
  return null;
}

module.exports = function scenarioSource({ types: t }) {
  return {
    name: 'scenario-source',
    visitor: {
      CallExpression(callPath, state) {
        if (!t.isIdentifier(callPath.node.callee, { name: 'defineScenario' })) return;
        const definition = callPath.get('arguments.0');
        if (!definition || !definition.isObjectExpression()) return;
        const code = [];
        for (const property of definition.get('properties')) {
          if (!property.isObjectProperty()) continue;
          const name = keyName(t, property.node.key);
          if (name !== 'runs') continue;
          const runs = property.get('value');
          if (!runs.isObjectExpression()) continue;
          for (const run of runs.get('properties')) {
            if (!run.isObjectProperty()) continue;
            const title = keyName(t, run.node.key);
            if (title === null) continue;
            code.push(
              t.objectProperty(
                t.stringLiteral(title),
                t.stringLiteral(dedent(run.get('value').getSource()))
              )
            );
          }
        }
        const root = path.resolve(__dirname, '..');
        const file = state.filename ? path.relative(root, state.filename) : '';
        definition.node.properties.push(
          t.objectProperty(t.identifier('code'), t.objectExpression(code)),
          t.objectProperty(t.identifier('file'), t.stringLiteral(file))
        );
      },
    },
  };
};
