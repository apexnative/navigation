// @ts-check
'use strict';

/**
 * The definition of a native screen in TypeScript: its props, which codegen makes Swift and Kotlin types of, and its
 * options.
 * @param {string} name such as `Profile`
 * @param {{ ios?: string; android?: string; typescript: boolean }} options where its native files are, for the comment
 * @returns {string}
 */
function nativeDefinition(name, { ios, android, typescript }) {
  const where = [ios && ` * - iOS: ${ios}`, android && ` * - Android: ${android}`]
    .filter(Boolean)
    .join('\n');
  const props = typescript
    ? `/** The props a command gives the screen: \`push('${name}', { props })\`. */
export interface ${name}Props {
  title?: string;
}

`
    : '';
  const generic = typescript ? `<${name}Props>` : '';
  return `import { nativeScreen } from '@apexnative/navigation';

${props}/**
 * A native screen:
${where}
 *
 * Its props in Swift and Kotlin come from the types here: after a change, run \`npx apex-navigation codegen\`.
 * \`useProps\` gives it more props from the app while it is open: data of the stores, and functions it calls.
 */
export const ${name}Screen = nativeScreen${generic}('${name}', {
  options: (s, props) => s.title(props.title ?? '${name}'),
  // useProps: (props) => ({}),
});
`;
}

module.exports = { nativeDefinition };
