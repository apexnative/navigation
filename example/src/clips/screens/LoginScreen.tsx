import React, { useRef, useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { Button, fonts } from '../../ui';
import { signIn } from '../session';
import { typeInto } from '../typing';

/** Signed out: an email and a Sign in button that switches to the app. */
function Login() {
  const c = useColors();
  const field = useRef<TextInput>(null);
  const [email, setEmail] = useState('');
  const [pressed, setPressed] = useState(false);

  useClipEvent('type', (value) => {
    field.current?.focus();
    typeInto(setEmail, String(value));
  });
  useClipEvent('press', () => {
    field.current?.blur();
    setPressed(true);
    setTimeout(signIn, 200);
  });

  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <Text style={[styles.title, { color: c.text }]}>Welcome back</Text>
      <TextInput
        ref={field}
        value={email}
        onChangeText={setEmail}
        placeholder="Email"
        placeholderTextColor={c.muted}
        autoCapitalize="none"
        style={[styles.field, { color: c.text, borderColor: c.line }]}
      />
      <View style={[styles.button, { opacity: pressed ? 0.6 : 1 }]}>
        <Button title="Sign in" variant="primary" onPress={signIn} />
      </View>
    </View>
  );
}

export const LoginScreen = defineScreen({
  component: Login,
  options: (s) => s.title('Login'),
});

const styles = StyleSheet.create({
  screen: { flex: 1, padding: 24, paddingTop: 48 },
  title: { fontFamily: fonts.display, fontSize: 30, marginBottom: 24 },
  field: {
    fontFamily: fonts.body,
    fontSize: 18,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 10,
    padding: 12,
  },
  button: { marginTop: 20, alignSelf: 'stretch' },
});
