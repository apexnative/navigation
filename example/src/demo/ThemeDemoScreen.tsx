import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  defineScreen,
  navigation,
  useNavigation,
  useNavigationTheme,
} from '@apexnative/navigation';
import { showcaseTheme } from '../app/theme';
import { Button, Card, Muted, Screen } from '../ui';
import { themes } from './themes';

/**
 * The theme of the navigation: the bar, the buttons and the backgrounds are native and take it at once; the preview
 * here is React content, which follows the theme through `useNavigationTheme`.
 */
function ThemeDemo() {
  const nav = useNavigation();
  // The colors for this appearance; one the theme leaves out is the platform's.
  const { colors } = useNavigationTheme();
  const primary = colors.primary ?? '#007AFF';
  const bar = colors.bar ?? 'transparent';
  const text = colors.text ?? '#888888';
  return (
    <Screen>
      <Card title="Pick a theme">
        <Button
          title="Ocean (with a bar color)"
          onPress={() => navigation.setTheme(themes.ocean)}
        />
        <Button title="Forest (glass bars)" onPress={() => navigation.setTheme(themes.forest)} />
        <Button
          title="The Showcase theme"
          variant="primary"
          onPress={() => navigation.setTheme(showcaseTheme)}
        />
      </Card>
      <Card title="React content from useNavigationTheme">
        <View style={[styles.preview, { backgroundColor: bar, borderColor: primary }]}>
          <Text style={[styles.title, { color: text }]}>A title in the theme</Text>
          <View style={[styles.pill, { backgroundColor: primary }]}>
            <Text style={styles.pillText}>A button in the theme</Text>
          </View>
        </View>
        <Muted>Push Details: a new screen takes the theme too; back: so did the panel.</Muted>
        <Button title="Push Details" onPress={() => nav.push('Details')} />
      </Card>
    </Screen>
  );
}

export const ThemeDemoScreen = defineScreen({
  component: ThemeDemo,
  options: (s) =>
    s
      .title('Theme')
      .hideBottomTabs()
      .rightButtons({ id: 'theme-done', text: 'Done', onPress: () => navigation.pop() }),
});

const styles = StyleSheet.create({
  preview: { borderRadius: 16, borderWidth: 2, padding: 16, gap: 12 },
  title: { fontSize: 17, fontWeight: '700' },
  pill: { alignSelf: 'flex-start', borderRadius: 999, paddingVertical: 8, paddingHorizontal: 14 },
  pillText: { color: '#FFFFFF', fontWeight: '600' },
});
