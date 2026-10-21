import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { defineScreen, useSafeAreaInsets } from '@apexnative/navigation';
import { useColors } from '../colors';
import { fonts } from '../../ui';

/** An overlay: a small note at the top, which the clip dismisses. Touches around it reach the app under it. */
function Toast() {
  const c = useColors();
  const insets = useSafeAreaInsets();
  return (
    <View pointerEvents="box-none" style={[styles.screen, { paddingTop: insets.top + 56 }]}>
      <View style={[styles.toast, { backgroundColor: c.text }]}>
        <Text style={[styles.text, { color: c.background }]}>✓ Saved</Text>
      </View>
    </View>
  );
}

export const ToastScreen = defineScreen({ component: Toast });

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center' },
  toast: { borderRadius: 22, paddingHorizontal: 22, paddingVertical: 12 },
  text: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
});
