import React from 'react';
import { StyleSheet, View } from 'react-native';
import { brand } from '../../ui';
import { MiniPhone } from './MiniPhone';

/** Home with a logo, small: logo and avatar in the bar, an event photo, its title and a card. */
export function HomePreview() {
  return (
    <MiniPhone>
      <View style={styles.bar}>
        <View style={styles.logo} />
        <View style={styles.avatar} />
      </View>
      <View style={styles.photo}>
        <View style={styles.hill} />
        <View style={styles.sun} />
      </View>
      <View style={[styles.line, styles.title]} />
      <View style={[styles.line, styles.subtitle]} />
      <View style={styles.card} />
    </MiniPhone>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 16,
    paddingHorizontal: 8,
    paddingBottom: 6,
  },
  logo: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: brand.navy,
    backgroundColor: brand.coral,
  },
  avatar: { width: 16, height: 16, borderRadius: 8, backgroundColor: brand.lavender },
  photo: {
    marginTop: 4,
    marginHorizontal: 6,
    height: 58,
    borderRadius: 10,
    overflow: 'hidden',
    backgroundColor: brand.sky,
  },
  hill: {
    position: 'absolute',
    left: -6,
    right: -6,
    bottom: 0,
    height: 22,
    borderTopLeftRadius: 44,
    borderTopRightRadius: 44,
    backgroundColor: brand.teal,
  },
  sun: {
    position: 'absolute',
    right: 12,
    top: 10,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: brand.mustard,
  },
  line: { marginHorizontal: 6, borderRadius: 3, backgroundColor: brand.navy },
  title: { marginTop: 6, height: 6, width: 60, opacity: 0.55 },
  subtitle: { marginTop: 5, height: 5, width: 48, opacity: 0.25 },
  card: {
    marginTop: 10,
    marginHorizontal: 6,
    height: 34,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#E3D6BC',
    backgroundColor: '#FFFAF0',
  },
});
