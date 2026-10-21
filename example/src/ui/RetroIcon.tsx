import React from 'react';
import { Image, ImageRequireSource, ImageStyle, StyleProp } from 'react-native';

// Drawn from the RetroIcon of the ApexNative brand (SVG), at 96 points and @2x, @3x.
const portholes = {
  navigation: require('../assets/brand/navigation.png') as ImageRequireSource,
  apexnative: require('../assets/brand/apexnative.png') as ImageRequireSource,
};

/** A porthole of the ApexNative brand: the mark of this package, or of all of them (`apexnative`). */
export function RetroIcon({
  kind = 'navigation',
  size,
  style,
}: {
  kind?: keyof typeof portholes;
  size: number;
  style?: StyleProp<ImageStyle>;
}) {
  return <Image source={portholes[kind]} style={[{ width: size, height: size }, style]} />;
}
