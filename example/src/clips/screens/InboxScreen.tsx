import React from 'react';
import { ScrollView } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useColors } from '../colors';
import { PEOPLE } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';
import { LIST_LAYOUT, useSidePadding } from '../insets';

/** Messages from people; a row opens its Profile. */
function Inbox() {
  const c = useColors();
  const sides = useSidePadding(16);
  const nav = useClipNavigation();
  return (
    <ScrollView
      contentContainerStyle={{ padding: 16, ...sides }}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
    >
      <List>
        {PEOPLE.map((name, index) => (
          <Row
            key={name}
            index={index + 3}
            title={name}
            subtitle="Shared a list with you"
            onPress={() => nav.push('Profile', { props: { index } })}
          />
        ))}
      </List>
    </ScrollView>
  );
}

export const InboxScreen = defineScreen({
  component: Inbox,
  options: (s) => s.title('Inbox').layout(LIST_LAYOUT),
});
