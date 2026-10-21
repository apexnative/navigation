import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { Button } from '../../ui';
import { useClipNavigation } from '../nav';

/** The first screen of the Quick start: a button that opens Details. */
function Start() {
  const c = useColors();
  const nav = useClipNavigation();
  const [pressed, setPressed] = useState(false);
  const open = () => nav.push('Details', { props: { index: 0 } });

  // A press the clip makes: the button dims for a moment, then Details comes in.
  useClipEvent('press', () => {
    setPressed(true);
    setTimeout(() => {
      setPressed(false);
      open();
    }, 180);
  });

  return (
    <View style={[styles.center, { backgroundColor: c.background }]}>
      <View style={[styles.button, { opacity: pressed ? 0.6 : 1 }]}>
        <Button title="Open Details" variant="primary" onPress={open} />
      </View>
    </View>
  );
}

export const StartScreen = defineScreen({
  component: Start,
  options: (s) => s.title('Home'),
});

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  button: { marginTop: 20, alignSelf: 'stretch' },
});
