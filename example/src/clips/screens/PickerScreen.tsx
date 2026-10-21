import React from 'react';
import { ScrollView } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';

/** The content of a sheet: a list to pick from. */
function Picker() {
  const c = useColors();
  const nav = useClipNavigation();
  // A step of the clip moves the sheet to a detent, as a drag would.
  useClipEvent('detent', (detent) =>
    nav.setOptions({ modal: { sheet: { selectedDetent: detent } } })
  );
  return (
    <ScrollView contentContainerStyle={{ padding: 16 }} style={{ backgroundColor: c.background }}>
      <List>
        {ITEMS.slice(0, 16).map((title, index) => (
          <Row key={title} index={index} title={title} onPress={() => nav.dismiss()} />
        ))}
      </List>
    </ScrollView>
  );
}

export const PickerScreen = defineScreen({
  component: Picker,
  options: (s) => s.title('Pick a list'),
});
