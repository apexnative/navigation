import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { Muted, Screen, useTheme } from '../ui';
import { PEOPLE } from './people';

/**
 * A profile of the shared element demo: its avatar and name came from the row of the list, the name turning into the
 * full name on the way (a different text).
 */
function Person({ index }: { index: number }) {
  const t = useTheme();
  const person = PEOPLE[index];
  return (
    <Screen>
      <View style={styles.header}>
        <View
          nativeID={`person-avatar-${index}`}
          style={[styles.avatar, { backgroundColor: person.color }]}
        >
          <Text style={styles.initial}>{person.name[0]}</Text>
        </View>
        <Text nativeID={`person-name-${index}`} style={[styles.name, { color: t.text }]}>
          {person.fullName}
        </Text>
      </View>
      <View nativeID={`person-bio-${index}`}>
        <Muted>
          The avatar and the name moved here from the list, the name turning into the full name;
          this text faded in after them. Back moves them home.
        </Muted>
      </View>
    </Screen>
  );
}

export const PersonScreen = defineScreen({
  component: Person,
  options: (s) => s.title('Profile').hideBottomTabs(),
});

const styles = StyleSheet.create({
  header: { alignItems: 'center', gap: 12, marginBottom: 16 },
  avatar: {
    width: 120,
    height: 120,
    borderRadius: 60,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initial: { color: '#FFFFFF', fontWeight: '800', fontSize: 48 },
  name: { fontSize: 24, fontWeight: '700' },
});
