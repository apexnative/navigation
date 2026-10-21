import React, { useRef, useState } from 'react';
import { Alert, StyleSheet, TextInput, View } from 'react-native';
import { defineScreen, usePreventLeave, useScreenOptions } from '@apexnative/navigation';
import { emit, useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { useClipNavigation } from '../nav';
import { typeInto } from '../typing';

/** A form in a modal: Cancel and Save, and a question before changes are lost. */
function Compose() {
  const c = useColors();
  const nav = useClipNavigation();
  const field = useRef<TextInput>(null);
  const [text, setText] = useState('');
  const changed = text.length > 0;

  const save = () => {
    emit('saved', text);
    nav.dismiss();
  };
  const ask = () =>
    Alert.alert('Discard the note?', undefined, [
      { text: 'Keep editing', style: 'cancel' },
      { text: 'Discard', style: 'destructive', onPress: () => nav.dismiss() },
    ]);

  useScreenOptions((s) =>
    s
      .title('New note')
      .leftButtons({
        id: 'cancel',
        text: 'Cancel',
        onPress: () => (changed ? ask() : nav.dismiss()),
      })
      .rightButtons({ id: 'save', text: 'Save', enabled: changed, onPress: save })
  );
  usePreventLeave(changed, ask);

  useClipEvent('type', (value) => {
    field.current?.focus();
    typeInto(setText, String(value));
  });
  useClipEvent('blur', () => field.current?.blur());
  useClipEvent('save', save);

  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <TextInput
        ref={field}
        value={text}
        onChangeText={setText}
        placeholder="Write a note"
        placeholderTextColor={c.muted}
        style={[styles.field, { color: c.text, borderColor: c.line }]}
      />
    </View>
  );
}

export const ComposeScreen = defineScreen({ component: Compose });

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 16 },
  field: {
    fontFamily: fonts.body,
    fontSize: 18,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 10,
    padding: 12,
  },
});
