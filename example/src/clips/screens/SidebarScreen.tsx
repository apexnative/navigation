import React, { useState } from 'react';
import { ScrollView } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';
import { useScrollBottomPadding } from '../insets';

/** The list column of a split view: a pick shows the item in the column beside it. */
function Sidebar() {
  const c = useColors();
  const nav = useClipNavigation();
  const bottom = useScrollBottomPadding(16);
  const [, setOpen] = useState(0);
  const open = (index: number) => {
    setOpen(index);
    nav.showInColumn('Details', { props: { index } });
  };
  useClipEvent('pick', (index) => open(Number(index)));
  return (
    <ScrollView
      contentContainerStyle={{ padding: 16, ...bottom }}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <List>
        {ITEMS.slice(0, 14).map((title, index) => (
          <Row key={title} index={index} title={title} onPress={() => open(index)} />
        ))}
      </List>
    </ScrollView>
  );
}

export const SidebarScreen = defineScreen({
  component: Sidebar,
  options: (s) => s.title('Lists'),
});
