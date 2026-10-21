import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Screen, useTheme } from '../ui';
import { PEOPLE } from './people';

/**
 * A list of people: a tap opens the profile, and the avatar and the name move from the row to the profile, the name
 * turning into the full name (`transition: { shared }` from the row's `nativeID`s to the profile's, and back on pop);
 * the bio fades in (`elementTransitions`).
 */
function People() {
  const t = useTheme();
  const nav = useNavigation();
  const open = (index: number) =>
    nav.push('Person', {
      props: { index },
      // The avatar and the name move into the profile and back; the rest fades.
      transition: {
        shared: [
          [`avatar-${index}`, `person-avatar-${index}`],
          [`name-${index}`, `person-name-${index}`],
        ],
        duration: 400,
      },
      // An animation of its own over the preset: the bio fades in after the avatar.
      options: {
        animations: {
          push: {
            elementTransitions: [
              { id: `person-bio-${index}`, alpha: { from: 0, duration: 300, startDelay: 200 } },
            ],
          },
        },
      },
    });
  return (
    <Screen>
      <View style={[styles.list, { backgroundColor: t.card, borderColor: t.border }]}>
        {PEOPLE.map((person, index) => (
          <Pressable
            key={person.name}
            onPress={() => open(index)}
            style={[styles.row, { borderBottomColor: t.border }]}
          >
            <View
              nativeID={`avatar-${index}`}
              style={[styles.avatar, { backgroundColor: person.color }]}
            >
              <Text style={styles.initial}>{person.name[0]}</Text>
            </View>
            <Text nativeID={`name-${index}`} style={[styles.name, { color: t.text }]}>
              {person.name}
            </Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

export const PeopleScreen = defineScreen({
  component: People,
  options: (s) => s.title('People').hideBottomTabs(),
});

const styles = StyleSheet.create({
  list: { borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initial: { color: '#FFFFFF', fontWeight: '700', fontSize: 18 },
  name: { fontSize: 17, fontWeight: '600' },
});
