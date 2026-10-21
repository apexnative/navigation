import React, { useState } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import {
  Button,
  Card,
  fonts,
  Hero,
  KeyValue,
  RichText,
  Screen,
  Segmented,
  Tag,
  useTheme,
} from '../ui';
import { appearanceMode, setAppearanceMode, type AppearanceMode } from '../app/appearance';
import { oneTree, setOneTree } from '../app/launchMode';
import { clearJournal, useJournal } from './journal';
import library from '../../../package.json';

/** The system the app runs on, as its chip says it. */
function platformName() {
  if (Platform.OS === 'ios') return `iOS ${Platform.Version}`;
  const release = (Platform.constants as { Release?: string }).Release;
  return `Android ${release ?? Platform.Version}`;
}

/** The version of React Native the app runs. */
function reactNativeVersion() {
  const { major, minor, patch } = Platform.constants.reactNativeVersion;
  return `RN ${major}.${minor}.${patch}`;
}

const APPEARANCES: { mode: AppearanceMode; label: string }[] = [
  { mode: 'system', label: 'System' },
  { mode: 'light', label: 'Light' },
  { mode: 'dark', label: 'Dark' },
];

/** The settings tab: what runs now, and actions on the whole Showcase. */
function Settings() {
  const t = useTheme();
  const entries = useJournal();
  const [appearance, setAppearance] = useState(appearanceMode);
  return (
    <Screen underBar>
      <Hero title="Showcase" compact>
        <Text style={styles.package}>
          {library.name} {library.version}
        </Text>
        <View style={styles.chips}>
          <Tag look="gold" label={platformName()} />
          <Tag look="space" label={reactNativeVersion()} />
        </View>
      </Hero>
      <Card title="Theme">
        <Segmented
          words
          segments={APPEARANCES.map((item) => ({
            key: item.mode,
            label: item.label,
            selected: appearance === item.mode,
            onPress: () => {
              setAppearanceMode(item.mode);
              setAppearance(item.mode);
            },
          }))}
        />
        <RichText style={[styles.note, { color: t.textMuted }]}>
          Light or dark for the whole app, the native bars too, until it starts again.
        </RichText>
      </Card>
      <Card title="Screens">
        <Segmented
          words
          segments={[
            {
              key: 'roots',
              label: 'A root each',
              selected: !oneTree,
              onPress: () => {
                if (oneTree) setOneTree(false);
              },
            },
            {
              key: 'tree',
              label: 'One tree',
              selected: oneTree,
              onPress: () => {
                if (!oneTree) setOneTree(true);
              },
            },
          ]}
        />
        <RichText style={[styles.note, { color: t.textMuted }]}>
          {oneTree
            ? 'Every screen renders in one React tree under `appProvider`.'
            : 'Every screen gets its own React root with `screenProvider`.'}
        </RichText>
        <Text style={[styles.reload, { color: t.pillText }]}>Switching reloads the app.</Text>
      </Card>
      <Card title="Journal">
        <KeyValue label="Entries" value={entries.length} />
        <Button title="Clear the journal" variant="danger" onPress={clearJournal} />
      </Card>
    </Screen>
  );
}

export const SettingsScreen = defineScreen({
  component: Settings,
  options: (s) => s.title('Settings').largeTitle().topBar(barFonts),
});

const styles = StyleSheet.create({
  package: { fontFamily: fonts.mono, fontSize: 11.5, color: '#B5A88C', marginTop: 3 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 8 },
  note: { fontSize: 13, lineHeight: 19 },
  reload: { fontFamily: fonts.mono, fontSize: 11 },
});
