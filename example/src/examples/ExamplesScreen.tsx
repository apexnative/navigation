import React from 'react';
import { StyleSheet, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import { brand, Muted, RichText, Screen } from '../ui';
import { ExampleCard } from './ExampleCard';
import { ClubsPreview } from './previews/ClubsPreview';
import { HomePreview } from './previews/HomePreview';
import { SafeAreaPreview } from './previews/SafeAreaPreview';

/**
 * Screens of a made-up app, built with the library as an app would build them. Each opens full screen as the first
 * screen of its own stack, as the root of an app: no back button.
 */
function Examples() {
  const nav = useNavigation();
  return (
    <Screen underBar>
      <RichText style={styles.intro}>
        Real screens with designed bars, built with the library's options only.
      </RichText>
      <ExampleCard
        title="Home with a logo"
        description="Logo on the left, avatar on the right, no title; an event opens with its photo under the bar."
        tags={['bar logo', 'avatar', 'under the bar']}
        color={brand.coral}
        preview={<HomePreview />}
        onPress={() => nav.showModal('ExampleHome', { style: 'fullScreen' })}
      />
      <ExampleCard
        title="Clubs"
        description="Inline large title, search and avatar; a post opens with its own title."
        tags={['inline large title', 'search']}
        color={brand.teal}
        preview={<ClubsPreview />}
        onPress={() => nav.showModal('ExampleClubs', { style: 'fullScreen' })}
      />
      <ExampleCard
        title="Safe area"
        description="The safe area in colors: switch its edges and the bars, and see where the content goes."
        tags={['safe area', 'edges']}
        color={brand.mustard}
        preview={<SafeAreaPreview />}
        onPress={() => nav.push('ExampleSafeArea')}
      />
      <View style={styles.gap} />
      <Muted>Tap the avatar in the bar to come back here.</Muted>
    </Screen>
  );
}

export const ExamplesScreen = defineScreen({
  component: Examples,
  options: (s) => s.title('Examples').largeTitle().topBar(barFonts),
});

const styles = StyleSheet.create({
  intro: { marginBottom: 18, marginLeft: 4 },
  gap: { height: 4 },
});
