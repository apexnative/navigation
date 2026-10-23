// @ts-check
'use strict';

const IMPORT = /^import\s+(\S+)/;

/**
 * The text with the import added: in alphabetical order among the imports (Kotlin), or after the last one (Swift).
 * Nothing changes when the file has it already.
 * @param {string} text
 * @param {string} statement such as `import dev.apexnative.navigation.NavigationActivity`
 * @param {{ sorted: boolean }} order
 */
function addImport(text, statement, { sorted }) {
  const lines = text.split('\n');
  if (lines.some((line) => line.trim() === statement)) return text;
  const imports = lines.flatMap((line, index) => (IMPORT.test(line) ? [index] : []));
  if (imports.length === 0) {
    // After the package line, or at the top.
    const pkg = lines.findIndex((line) => line.startsWith('package '));
    lines.splice(pkg + 1, 0, ...(pkg >= 0 ? ['', statement] : [statement, '']));
    return lines.join('\n');
  }
  const after = sorted ? imports.find((index) => lines[index] > statement) : undefined;
  lines.splice(after ?? imports[imports.length - 1] + 1, 0, statement);
  return lines.join('\n');
}

/**
 * The text without the imports nothing else in it uses any more.
 * @param {string} text
 * @param {string[]} names fully qualified, such as `com.facebook.react.ReactActivity`
 */
function removeUnusedImports(text, names) {
  let result = text;
  for (const name of names) {
    const line = new RegExp(`^import\\s+${escape(name)}[ \\t]*\\r?\\n`, 'm');
    const simple = name.slice(name.lastIndexOf('.') + 1);
    // A name in a comment is no use of the import.
    const code = codeOf(result).replace(/^import\s.*$/gm, '');
    if (line.test(result) && !new RegExp(`\\b${escape(simple)}\\b`).test(code))
      result = result.replace(line, '');
  }
  return result;
}

/** @param {string} text */
function escape(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * The code of a Kotlin, Swift or JavaScript file without its comments, to look for calls a comment only mentions.
 * @param {string} text
 */
function codeOf(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

module.exports = { addImport, codeOf, removeUnusedImports };
