import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { brand, fonts } from '../../ui';
import { MiniPhone } from './MiniPhone';

const CLUBS = [brand.lavender, brand.orange, brand.pink, '#3D8C6E'];

/** Clubs, small: an inline large title with an avatar, the search field and a list of clubs. */
export function ClubsPreview() {
  return (
    <MiniPhone>
      <View style={styles.bar}>
        <Text style={styles.title}>Clubs</Text>
        <View style={styles.avatar} />
      </View>
      <View style={styles.search} />
      <View style={styles.list}>
        {CLUBS.map((color) => (
          <View key={color} style={styles.row}>
            <View style={[styles.icon, { backgroundColor: color }]} />
            <View style={styles.name} />
          </View>
        ))}
      </View>
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
    paddingBottom: 4,
  },
  title: { fontFamily: fonts.display, fontSize: 15, color: brand.navy },
  avatar: { width: 16, height: 16, borderRadius: 8, backgroundColor: brand.mustard },
  search: {
    marginTop: 2,
    marginHorizontal: 6,
    height: 14,
    borderRadius: 7,
    backgroundColor: '#EFE4CC',
  },
  list: { marginTop: 8, marginHorizontal: 6, gap: 6 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  icon: { width: 16, height: 16, borderRadius: 5 },
  name: { flex: 1, height: 5, borderRadius: 3, backgroundColor: brand.navy, opacity: 0.45 },
});
