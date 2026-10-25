import React from 'react';
import { ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { PEOPLE } from '../data';
import { useBottomPadding } from '../insets';

export interface ChatProps {
  id: string;
}

const MESSAGES = [
  { mine: false, text: 'Are we still on for Saturday?' },
  { mine: true, text: 'Yes! The coast, leaving at 9.' },
  { mine: false, text: 'Lunch on the way?' },
  { mine: true, text: 'The harbor café, I booked a table.' },
];

/** A conversation: messages and a field to write in. */
function Chat() {
  const c = useColors();
  // The field sits above the home indicator or the navigation bar.
  const { paddingBottom } = useBottomPadding(40);
  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <ScrollView
        contentContainerStyle={styles.messages}
        contentInsetAdjustmentBehavior="automatic"
      >
        {MESSAGES.map((message, index) => (
          <View
            key={index}
            style={[
              styles.bubble,
              message.mine
                ? [styles.mine, { backgroundColor: c.tint }]
                : { backgroundColor: c.grouped },
            ]}
          >
            <Text style={[styles.text, { color: message.mine ? '#FFFFFF' : c.text }]}>
              {message.text}
            </Text>
          </View>
        ))}
      </ScrollView>
      <TextInput
        placeholder="Message"
        placeholderTextColor={c.muted}
        style={[styles.field, { color: c.text, borderColor: c.line, marginBottom: paddingBottom }]}
      />
    </View>
  );
}

export const ChatScreen = defineScreen({
  component: Chat,
  options: (s, { id }: ChatProps) =>
    s.title(PEOPLE[Number(id) % PEOPLE.length] ?? 'Chat').hideBottomTabs(),
});

const styles = StyleSheet.create({
  screen: { flex: 1 },
  messages: { padding: 16, gap: 10 },
  bubble: { maxWidth: '78%', borderRadius: 18, paddingHorizontal: 14, paddingVertical: 10 },
  mine: { alignSelf: 'flex-end' },
  text: { fontFamily: fonts.body, fontSize: 16 },
  field: {
    fontFamily: fonts.body,
    fontSize: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    margin: 12,
  },
});
