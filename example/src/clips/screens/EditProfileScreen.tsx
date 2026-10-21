import React, { useRef, useState } from 'react';
import { Alert, StyleSheet, Text, TextInput, View } from 'react-native';
import { defineScreen, usePreventLeave, useScreenOptions } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { useClipNavigation } from '../nav';
import { typeInto } from '../typing';

export interface EditProfileProps {
  /** The bar follows the field: the title is the name as it is typed, Save is off while it is empty. */
  live?: boolean;
}

/** A pushed form: back with unsaved changes asks first. */
function EditProfile({ live = false }: EditProfileProps) {
  const c = useColors();
  const nav = useClipNavigation();
  const field = useRef<TextInput>(null);
  const [name, setName] = useState(live ? '' : 'Alex Kim');
  const [leaving, setLeaving] = useState(false);
  const changed = !live && name !== 'Alex Kim' && !leaving;

  useScreenOptions((s) =>
    live
      ? s.title(name || 'New contact').rightButtons({
          id: 'save',
          text: 'Save',
          enabled: name.trim() !== '',
          style: 'prominent',
        })
      : s
  );

  usePreventLeave(changed, () =>
    Alert.alert('Discard the changes?', undefined, [
      { text: 'Keep editing', style: 'cancel' },
      {
        text: 'Discard',
        style: 'destructive',
        onPress: () => {
          setLeaving(true);
          setTimeout(() => nav.goBack(), 50);
        },
      },
    ])
  );

  useClipEvent('type', (value) => {
    field.current?.focus();
    typeInto(setName, String(value));
  });
  useClipEvent('blur', () => field.current?.blur());

  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <Text style={[styles.label, { color: c.muted }]}>Name</Text>
      <TextInput
        ref={field}
        value={name}
        onChangeText={setName}
        placeholder="Name"
        placeholderTextColor={c.muted}
        style={[styles.field, { color: c.text, borderColor: c.line }]}
      />
    </View>
  );
}

export const EditProfileScreen = defineScreen({
  component: EditProfile,
  options: (s) => s.title('Edit profile'),
});

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 16 },
  label: { fontFamily: fonts.body, fontSize: 13, marginBottom: 6 },
  field: {
    fontFamily: fonts.body,
    fontSize: 18,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 10,
    padding: 12,
  },
});
