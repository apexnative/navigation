// @ts-check
'use strict';

/**
 * A React screen: its component, its props with their type, and its options next to it (`defineScreen`). Its text has
 * the system's color, light or dark as the screen behind it: Text alone is black on any background.
 * @param {string} name such as `Profile`
 * @param {{ typescript: boolean }} options
 * @returns {string}
 */
function reactScreen(name, { typescript }) {
  const props = typescript
    ? `\n/** The props a command gives the screen: \`push('${name}', { props })\`. */\nexport interface ${name}Props {\n  title?: string;\n}\n`
    : '';
  const parameter = typescript ? `props: ${name}Props` : 'props';
  return `import React from 'react';
import { Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
${props}
function ${name}(${parameter}) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>{props.title ?? '${name}'}</Text>
    </View>
  );
}

export const ${name}Screen = defineScreen({
  component: ${name},
  options: (s, props) => s.title(props.title ?? '${name}'),
});

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color:
      Platform.OS === 'ios'
        ? PlatformColor('label')
        : PlatformColor('?android:attr/textColorPrimary'),
  },
});
`;
}

module.exports = { reactScreen };
