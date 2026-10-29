const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
const { withApexNavigation } = require('@apexnative/navigation/metro');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {};

module.exports = withApexNavigation(mergeConfig(getDefaultConfig(__dirname), config));
