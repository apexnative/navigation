import React, { useState } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { useSafeAreaInsets, useSearchBar } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { useTheme } from '../ui';

const PLACES = [
  'Amsterdam',
  'Barcelona',
  'Berlin',
  'Copenhagen',
  'Lisbon',
  'Madrid',
  'Oslo',
  'Paris',
  'Prague',
  'Vienna',
];

/**
 * A search screen of its own (`searchBar.resultsComponent`): recent searches while the field is empty, then the places
 * that match. It gets the search events of the screen with its own id.
 */
export function SearchResults() {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  useSearchBar({ onChange: (event) => setQuery(event.text) });
  const words = query.trim().toLowerCase();
  const rows = words
    ? PLACES.filter((place) => place.toLowerCase().includes(words))
    : PLACES.slice(0, 3);
  return (
    <ScrollView
      style={{ backgroundColor: t.background }}
      // iOS keeps the last rows clear of the home indicator with the content inset, on Android they end above the bar here.
      contentContainerStyle={
        Platform.OS === 'android' ? { paddingBottom: insets.bottom } : undefined
      }
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
    >
      <Text style={[styles.header, { color: t.textMuted }]}>
        {words ? `${rows.length} found` : 'Recent'}
      </Text>
      {rows.map((place) => (
        <Pressable
          key={place}
          onPress={() => log('event', 'Search result', place)}
          style={({ pressed }) => [
            styles.row,
            { borderBottomColor: t.border, opacity: pressed ? 0.6 : 1 },
          ]}
        >
          <Text style={[styles.text, { color: t.text }]}>{place}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  header: { fontSize: 13, fontWeight: '600', marginTop: 12, marginBottom: 6, marginHorizontal: 16 },
  row: { paddingVertical: 14, paddingHorizontal: 16, borderBottomWidth: StyleSheet.hairlineWidth },
  text: { fontSize: 17 },
});
