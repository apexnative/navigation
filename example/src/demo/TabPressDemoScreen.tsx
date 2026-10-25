import React, { useRef } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen, navigation, useSafeAreaInsets, useTabPress } from '@apexnative/navigation';
import { log } from '../showcase/journal';
import { Button, Card, Muted, useTheme } from '../ui';

const ROWS = Array.from({ length: 40 }, (_, index) => `Row ${index + 1}`);

/** A tab of the tab press demo: a second press on its tab scrolls it to the top, as apps do (`useTabPress`). */
function TabPressDemo({ title }: { title: string }) {
  const t = useTheme();
  const list = useRef<ScrollView>(null);
  const insets = useSafeAreaInsets();
  // iOS keeps the last rows clear of the home indicator with the content inset, on Android they end above the bar here.
  const bottom = Platform.OS === 'android' ? { paddingBottom: 56 + insets.bottom } : null;
  useTabPress((event) => {
    log('event', `useTabPress (${title})`, event.wasSelected ? 'again: to the top' : 'first press');
    if (event.wasSelected) list.current?.scrollTo({ y: 0, animated: true });
  });
  return (
    <ScrollView
      ref={list}
      style={{ backgroundColor: t.background }}
      contentContainerStyle={[styles.content, bottom]}
      contentInsetAdjustmentBehavior="automatic"
    >
      <Card title={title}>
        <Muted>
          Scroll down, then press this tab again: it goes to the top. New is not a tab to select: it
          opens a modal.
        </Muted>
        <Button
          title="Back to the Showcase"
          variant="primary"
          onPress={() => navigation.switchTo('showcase')}
        />
      </Card>
      <View style={[styles.rows, { backgroundColor: t.card, borderColor: t.border }]}>
        {ROWS.map((row) => (
          <Text key={row} style={[styles.row, { color: t.text, borderBottomColor: t.border }]}>
            {row}
          </Text>
        ))}
      </View>
    </ScrollView>
  );
}

export const TabPressDemoScreen = defineScreen({
  component: TabPressDemo,
  options: (s, props: { title: string }) => s.title(props.title),
});

const styles = StyleSheet.create({
  content: { paddingTop: 16, paddingBottom: 56, paddingHorizontal: 16 },
  rows: { borderRadius: 16, borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  row: {
    paddingVertical: 14,
    paddingHorizontal: 16,
    fontSize: 15,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
});
