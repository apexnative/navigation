const path = require('path');
const { getConfig } = require('react-native-builder-bob/babel-config');
const pkg = require('../package.json');

const root = path.resolve(__dirname, '..');

module.exports = getConfig(
  {
    presets: ['module:@react-native/babel-preset'],
    // The Showcase shows the code of its scenarios as it is in their files.
    plugins: [require.resolve('./babel/scenario-source')],
  },
  { root, pkg }
);
