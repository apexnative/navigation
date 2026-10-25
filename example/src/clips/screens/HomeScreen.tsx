import React, { useRef, useState } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { defineScreen, useSafeAreaInsets, useScreenOptions } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';
import { LIST_LAYOUT, useScrollBottomPadding, useSidePadding } from '../insets';

export interface HomeProps {
  /** The screen has a large title: on Android the list starts below it. */
  largeTitle?: boolean;
}

/** A list of items; a row pushes its Details. A note saved in Compose shows above the list. */
function Home({ largeTitle = false }: HomeProps) {
  const insets = useSafeAreaInsets();
  const c = useColors();
  const sides = useSidePadding(16);
  const bottom = useScrollBottomPadding(16);
  const nav = useClipNavigation();
  const list = useRef<ScrollView>(null);
  const [saved, setSaved] = useState<string>();
  // A step of a clip puts a dot on the tab of the screen.
  const [dot, setDot] = useState<boolean>();
  useClipEvent('dot', (value) => setDot(value === true));
  useScreenOptions((s) =>
    dot === undefined ? s : s.options({ bottomTab: { dotIndicator: { visible: dot } } })
  );

  useClipEvent('scroll', (y) => list.current?.scrollTo({ y: Number(y), animated: true }));
  useClipEvent('saved', (text) => setSaved(String(text)));
  useClipEvent('open-item', (index) => nav.push('Details', { props: { index: Number(index) } }));

  return (
    <ScrollView
      contentContainerStyle={{
        padding: 16,
        ...sides,
        ...bottom,
        paddingTop: largeTitle && Platform.OS === 'android' ? 16 + insets.top : 16,
      }}
      ref={list}
      nestedScrollEnabled
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      {saved ? (
        <View style={[styles.saved, { backgroundColor: c.grouped }]}>
          <Text style={[styles.savedText, { color: c.text }]}>Saved: {saved}</Text>
        </View>
      ) : null}
      <List>
        {ITEMS.map((title, index) => (
          <Row
            key={title}
            index={index}
            title={title}
            subtitle={`${(index % 5) + 2} notes`}
            onPress={() => nav.push('Details', { props: { index } })}
          />
        ))}
      </List>
    </ScrollView>
  );
}

export const HomeScreen = defineScreen({
  component: Home,
  options: (s) => s.title('Home').layout(LIST_LAYOUT),
});

const styles = StyleSheet.create({
  saved: { margin: 16, padding: 14, borderRadius: 12 },
  savedText: { fontFamily: fonts.body, fontSize: 16 },
});
