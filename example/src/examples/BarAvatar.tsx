import React from 'react';
import { Image, Pressable, StyleSheet } from 'react-native';
import { navigation } from '@apexnative/navigation';
import { avatarUrl, me } from './data';

/**
 * The user's avatar as a button of the bar (a `component` button: no glass capsule). A component button handles its
 * own press; here it closes the example and goes back to the Showcase.
 */
export function BarAvatar() {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Close the example"
      hitSlop={6}
      onPress={() => navigation.dismissAllModals()}
      style={({ pressed }) => [styles.frame, { opacity: pressed ? 0.7 : 1 }]}
    >
      <Image source={{ uri: avatarUrl(me.name) }} style={styles.avatar} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  frame: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  avatar: { width: 38, height: 38, borderRadius: 19 },
});
