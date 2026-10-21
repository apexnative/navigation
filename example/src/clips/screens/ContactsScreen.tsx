import React, { useRef, useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { defineScreen, useScreenOptions, useSearchBar } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { PEOPLE } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { fonts, List } from '../../ui';

export interface ContactsProps {
  /** The search has scopes (All, Friends, Work) and suggestions as the user types. */
  filters?: boolean;
}

const SCOPES = [
  { id: 'all', title: 'All' },
  { id: 'friends', title: 'Friends' },
  { id: 'work', title: 'Work' },
];

/** People with a search field in the bar; the list narrows as the user types. */
function Contacts({ filters = false }: ContactsProps) {
  const c = useColors();
  const nav = useClipNavigation();
  const list = useRef<ScrollView>(null);
  const [query, setQuery] = useState('');
  const [scope, setScope] = useState('all');
  useSearchBar({
    onChange: ({ text, scope: picked }) => {
      setQuery(text);
      if (picked) setScope(picked);
    },
    onCancel: () => setQuery(''),
  });
  useClipEvent('scroll', (y) => list.current?.scrollTo({ y: Number(y), animated: true }));
  const words = query.trim().toLowerCase();
  const people = [...PEOPLE, ...PEOPLE.map((name) => name.split(' ').reverse().join(' '))];
  const found = people
    .map((name, index) => ({ name, index }))
    .filter(({ name }) => name.toLowerCase().includes(words))
    .filter(({ index }) => scope === 'all' || (index % 2 === 0) === (scope === 'friends'));
  // The suggestions follow the text: the first names that start with it.
  useScreenOptions((s) =>
    filters
      ? s.searchBar({
          suggestions: words
            ? PEOPLE.filter((name) => name.toLowerCase().startsWith(words))
                .slice(0, 3)
                .map((name) => ({ id: name, title: name }))
            : [],
        })
      : s
  );
  return (
    <ScrollView
      contentContainerStyle={{ padding: 16 }}
      ref={list}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      {found.length === 0 ? (
        <Text style={[styles.empty, { color: c.muted }]}>No results for “{query}”</Text>
      ) : null}
      <List>
        {found.map(({ name, index }) => (
          <Row
            key={name}
            index={index}
            title={name}
            onPress={() => nav.push('Profile', { props: { index } })}
          />
        ))}
      </List>
    </ScrollView>
  );
}

export const ContactsScreen = defineScreen({
  component: Contacts,
  options: (s, { filters = false }: ContactsProps) =>
    s.title('Contacts').searchBar({
      visible: true,
      placeholder: 'Search',
      ...(filters ? { scopes: SCOPES } : {}),
    }),
});

const styles = StyleSheet.create({
  empty: { fontFamily: fonts.body, fontSize: 17, textAlign: 'center', marginVertical: 40 },
});
