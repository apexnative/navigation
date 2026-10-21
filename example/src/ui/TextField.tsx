import React from 'react';
import { StyleSheet, TextInput } from 'react-native';
import { fonts } from './fonts';
import { useTheme } from './theme';

/** A text field; without `value` it keeps its own text. */
export function TextField({
  placeholder,
  value,
  onChangeText,
}: {
  placeholder: string;
  value?: string;
  onChangeText?: (text: string) => void;
}) {
  const t = useTheme();
  return (
    <TextInput
      placeholder={placeholder}
      placeholderTextColor={t.textMuted}
      value={value}
      onChangeText={onChangeText}
      autoCorrect={false}
      autoCapitalize="none"
      style={[
        styles.field,
        { backgroundColor: t.background, color: t.text, borderColor: t.border },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  field: {
    minHeight: 40,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontFamily: fonts.body,
    fontSize: 15,
  },
});
