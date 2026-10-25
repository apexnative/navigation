import React from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { emit, useClipEvent } from '../bus';
import { useColors } from '../colors';
import { ITEMS } from '../data';
import { clipNavigation } from '../nav';
import { Row } from '../Row';
import { fonts, List } from '../../ui';
import { useBottomPadding } from '../insets';

/** The menu of a side menu: a pick closes it and opens the item in the center. */
function Menu() {
  const c = useColors();
  const bottom = useBottomPadding();
  const pick = async (index: number) => {
    await clipNavigation.closeMenu();
    // The center opens it: right after closeMenu the app's navigation still acts from the menu.
    emit('open-item', index);
  };
  useClipEvent('pick', (index) => pick(Number(index)));
  return (
    <ScrollView
      style={{ backgroundColor: c.background }}
      contentContainerStyle={[styles.content, bottom]}
    >
      <Text style={[styles.title, { color: c.text }]}>Lists</Text>
      <List>
        {ITEMS.slice(0, 8).map((title, index) => (
          <Row key={title} index={index} title={title} onPress={() => pick(index)} />
        ))}
      </List>
    </ScrollView>
  );
}

export const MenuScreen = defineScreen({ component: Menu });

const styles = StyleSheet.create({
  content: { paddingTop: 72, paddingHorizontal: 16 },
  title: { fontFamily: fonts.display, fontSize: 28, marginHorizontal: 4, marginBottom: 12 },
});
