import React, { useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useFocusEffect } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';
import { useScrollBottomPadding } from '../insets';

/** Runs `refresh` each time the screen gets the focus again, as the data fetching guide shows. */
function useRefreshOnFocus(refresh: () => void) {
  const first = useRef(true);
  useFocusEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    refresh();
  });
}

/**
 * A feed with the time of its last update. It refreshes when the user comes back to it; a step of a clip breaks its
 * next render, and the app shows its error fallback in its place.
 */
function Feed() {
  const c = useColors();
  const nav = useClipNavigation();
  const bottom = useScrollBottomPadding(16);
  const [minute, setMinute] = useState(4);
  const [loading, setLoading] = useState(false);
  const [broken, setBroken] = useState(false);
  useClipEvent('break', () => setBroken(true));
  useRefreshOnFocus(() => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setMinute((m) => m + 1);
    }, 700);
  });
  if (broken) throw new Error('The feed did not load.');
  return (
    <ScrollView
      contentContainerStyle={{ padding: 16, ...bottom }}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <View style={styles.updated}>
        {loading ? <ActivityIndicator color={c.muted} /> : null}
        <Text style={[styles.updatedText, { color: c.muted }]}>
          {loading ? 'Updating…' : `Updated 12:0${minute}`}
        </Text>
      </View>
      <List>
        {ITEMS.slice(6, 20).map((title, index) => (
          <Row
            key={title}
            index={index + 6}
            title={title}
            subtitle={`${(index % 4) + 1} new`}
            onPress={() => nav.push('Details', { props: { index: index + 6 } })}
          />
        ))}
      </List>
    </ScrollView>
  );
}

export const FeedScreen = defineScreen({
  component: Feed,
  options: (s) => s.title('Feed'),
});

const styles = StyleSheet.create({
  updated: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 28,
    marginBottom: 10,
  },
  updatedText: { fontFamily: fonts.body, fontSize: 14 },
});
