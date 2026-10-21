import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { TILES, useColors } from '../colors';
import { fonts } from '../../ui';
import { PEOPLE } from '../data';
import { LIST_LAYOUT, useSidePadding } from '../insets';

export interface ProfileProps {
  index: number;
}

/** A person: initials in a circle, the name and a few facts. */
function Profile({ index }: ProfileProps) {
  const c = useColors();
  const sides = useSidePadding();
  const name = PEOPLE[index % PEOPLE.length] ?? 'Alex Kim';
  const initials = name
    .split(' ')
    .map((part) => part[0])
    .join('');
  return (
    <ScrollView
      contentContainerStyle={sides}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <View style={[styles.avatar, { backgroundColor: TILES[(index + 2) % TILES.length] }]}>
        <Text style={styles.initials}>{initials}</Text>
      </View>
      <Text style={[styles.name, { color: c.text }]}>{name}</Text>
      <Text style={[styles.facts, { color: c.muted }]}>12 notes · 3 shared lists · since 2023</Text>
    </ScrollView>
  );
}

export const ProfileScreen = defineScreen({
  component: Profile,
  options: (s) => s.title('Profile').layout(LIST_LAYOUT),
});

const styles = StyleSheet.create({
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
    alignSelf: 'center',
    marginTop: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: { color: '#FFFFFF', fontFamily: fonts.display, fontSize: 38 },
  name: { fontFamily: fonts.display, fontSize: 26, textAlign: 'center', marginTop: 16 },
  facts: { fontFamily: fonts.body, fontSize: 15, textAlign: 'center', marginTop: 6 },
});
