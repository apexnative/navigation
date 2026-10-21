import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, TextInput, View } from 'react-native';
import {
  defineScreen,
  useNavigationButton,
  usePreventLeave,
  useScreenOptions,
} from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { useClipNavigation } from '../nav';
import { typeInto } from '../typing';

const SAVED = 'Pack the tent and the stove.';

/**
 * The screen of the "Building a screen" guide: it loads its note, Save in the bar is on once the text changes, and
 * back with changes not saved asks first.
 */
function EditNote() {
  const c = useColors();
  const nav = useClipNavigation();
  const field = useRef<TextInput>(null);
  const [text, setText] = useState<string>();
  const [saving, setSaving] = useState(false);
  const changed = text !== undefined && text !== SAVED && !saving;

  useEffect(() => {
    const timer = setTimeout(() => setText(SAVED), 700);
    return () => clearTimeout(timer);
  }, []);

  const save = () => {
    field.current?.blur();
    setSaving(true);
    setTimeout(() => nav.goBack(), 300);
  };
  useScreenOptions((s) =>
    s.rightButtons({ id: 'save', text: 'Save', enabled: changed, style: 'prominent' })
  );
  useNavigationButton('save', save);
  usePreventLeave(changed, () =>
    Alert.alert('Discard changes?', undefined, [
      { text: 'Keep editing', style: 'cancel' },
      { text: 'Discard', style: 'destructive', onPress: () => setText(SAVED) },
    ])
  );

  useClipEvent('type', (value) => {
    field.current?.focus();
    typeInto((tail) => setText(`${SAVED} ${tail}`), String(value));
  });
  useClipEvent('blur', () => field.current?.blur());
  useClipEvent('save', save);

  if (text === undefined) {
    return (
      <View style={[styles.loading, { backgroundColor: c.background }]}>
        <ActivityIndicator color={c.muted} />
      </View>
    );
  }
  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <TextInput
        ref={field}
        value={text}
        onChangeText={setText}
        multiline
        style={[styles.field, { color: c.text }]}
      />
    </View>
  );
}

export const EditNoteScreen = defineScreen({
  component: EditNote,
  options: (s) => s.title('Trip to the coast'),
});

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  screen: { flex: 1, padding: 20 },
  field: { fontFamily: fonts.body, fontSize: 19, lineHeight: 26 },
});
