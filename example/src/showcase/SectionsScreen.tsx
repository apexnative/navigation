import React from 'react';
import { StyleSheet, View } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import { sections } from '../sections';
import { Hero, Label, List, ListRow, Screen } from '../ui';

const scenarios = sections.reduce((count, section) => count + section.scenarios.length, 0);

/** The first tab: every section. */
function Sections() {
  const nav = useNavigation();
  return (
    <Screen underBar>
      <Hero
        title="ApexNative Navigation"
        subtitle="The new API, section by section: what to check, the code, and a button to run it."
        badge={`${sections.length} sections · ${scenarios} scenarios`}
      />
      <View style={styles.gap} />
      <Label>Sections</Label>
      <List>
        {sections.map((section) => (
          <ListRow
            key={section.id}
            id={section.id}
            porthole
            title={section.title}
            subtitle={`${section.scenarios.length} scenarios`}
            testID={`section-${section.id}`}
            onPress={() => nav.push('Section', { props: { id: section.id } })}
          />
        ))}
      </List>
    </Screen>
  );
}

export const SectionsScreen = defineScreen({
  component: Sections,
  // The list goes to every edge on iOS: its scroll view keeps the last rows above the home indicator, and the sides
  // clear of the vertical bar of iPhone Duo come from `Screen`.
  options: (s) =>
    s
      .title('Showcase')
      .largeTitle()
      .topBar(barFonts)
      .ios((i) => i.safeArea()),
});

const styles = StyleSheet.create({
  gap: { height: 8 },
});
