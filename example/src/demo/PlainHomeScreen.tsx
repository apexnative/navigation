import React, { useEffect } from 'react';
import { Button, Platform, PlatformColor, StyleSheet, Text, View } from 'react-native';
import { defineScreen, navigation, useNavigation } from '@apexnative/navigation';
import { showcaseTheme } from '../app/theme';

/**
 * The first screen of an app as it is written at first: a red `View`, a `Text` and a `Button` that pushes Details.
 * 6.11 shows it under a theme without a background; the Showcase theme comes back as it goes.
 */
function PlainHome({ largeTitle }: { largeTitle?: boolean }) {
  const nav = useNavigation();
  useEffect(() => () => navigation.setTheme(showcaseTheme), []);
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Home</Text>
      <Button
        title="Open Details"
        onPress={() => nav.push('PlainDetails', { props: { title: 'Details', largeTitle } })}
      />
    </View>
  );
}

export const PlainHomeScreen = defineScreen({
  component: PlainHome,
  options: (s) => s.title('Home'),
});

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: 'red',
    justifyContent: 'center',
    gap: 12,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color:
      Platform.OS === 'ios'
        ? PlatformColor('label')
        : PlatformColor('?android:attr/textColorPrimary'),
  },
});
