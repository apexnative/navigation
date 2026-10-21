import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import { brand, Chip, fonts, Muted, Screen, useTheme } from '../ui';
import { clearJournal, JournalKind, useJournal } from './journal';
import { JournalEntryRow } from './JournalEntryRow';

// A few stars by the lead, as on the board.
const STARS = [
  { left: '58%', top: 22, size: 2, opacity: 0.6, gold: false },
  { left: '72%', top: 2, size: 2, opacity: 0.4, gold: false },
  { left: '88%', top: 14, size: 3, opacity: 0.8, gold: true },
] as const;

const FILTERS: { kind: JournalKind | 'all'; title: string }[] = [
  { kind: 'all', title: 'All' },
  { kind: 'event', title: 'Event' },
  { kind: 'state', title: 'State' },
  { kind: 'command', title: 'Command' },
  { kind: 'error', title: 'Error' },
];

/** The journal tab: what the navigation did, newest first, on a timeline. */
function Journal() {
  const t = useTheme();
  const entries = useJournal();
  const [filter, setFilter] = useState<JournalKind | 'all'>('all');
  const shown = filter === 'all' ? entries : entries.filter((entry) => entry.kind === filter);
  return (
    <Screen underBar>
      {STARS.map((star, index) => (
        <View
          key={index}
          style={[
            styles.star,
            {
              left: star.left,
              top: star.top,
              width: star.size,
              height: star.size,
              borderRadius: star.size / 2,
              opacity: star.opacity,
              backgroundColor: star.gold ? brand.mustard : t.text,
            },
          ]}
        />
      ))}
      <Text style={[styles.lead, { color: t.textMuted }]}>What the app did, newest first.</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filters}
      >
        {FILTERS.map((item) => (
          <Chip
            key={item.kind}
            label={item.kind === 'all' ? `${item.title} ${entries.length}` : item.title}
            code
            selected={filter === item.kind}
            onPress={() => setFilter(item.kind)}
          />
        ))}
      </ScrollView>
      <View style={styles.gap} />
      {shown.length === 0 ? (
        <Muted>Nothing yet: run a scenario.</Muted>
      ) : (
        <View style={styles.timeline}>
          <View style={[styles.line, { backgroundColor: t.border }]} />
          {shown.map((entry) => (
            <JournalEntryRow key={entry.id} entry={entry} />
          ))}
        </View>
      )}
    </Screen>
  );
}

export const JournalScreen = defineScreen({
  component: Journal,
  options: (s) =>
    s
      .title('Journal')
      .largeTitle()
      .topBar(barFonts)
      .rightButtons({ id: 'clear', text: 'Clear', onPress: clearJournal }),
});

const styles = StyleSheet.create({
  star: { position: 'absolute' },
  lead: { fontFamily: fonts.body, fontSize: 14, marginBottom: 16, marginLeft: 4 },
  filters: { gap: 8, paddingHorizontal: 4 },
  timeline: { marginHorizontal: 4 },
  gap: { height: 22 },
  line: { position: 'absolute', left: 5, top: 6, bottom: 0, width: 2 },
});
