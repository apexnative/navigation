import React, { useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import { findSection } from '../sections';
import { sectionControls, type Section } from '../sections/scenario';
import {
  Button,
  Card,
  Code,
  Footer,
  isOtherPlatform,
  Label,
  List,
  Muted,
  platformLabel,
  RichText,
  Screen,
  SectionBadge,
  Tag,
} from '../ui';
import { ControlsGroup } from './controls/ControlsGroup';
import { openDetails, setActiveSection } from './controls/openDetails';
import { openSideMenuDemo } from './controls/openSideMenuDemo';
import { openSplitViewDemo } from './controls/openSplitViewDemo';
import { openTabsDemo, splitTabOptions } from './controls/openTabsDemo';
import {
  buildOptions,
  showCode,
  showSideMenuCode,
  showSplitViewCode,
  showTabsCode,
} from './controls/optionsCode';
import { usePanelToolbar, type PanelMode } from './controls/usePanelToolbar';
import { useSectionValues } from './sectionValues';

/**
 * The panel of a section: its options in groups, the code they make, and the buttons that show Details with them: in
 * the toolbar on iOS, in a footer on Android.
 */
function Panel({ section, focus }: { section: Section; focus?: string }) {
  const [values, set, reset] = useSectionValues(section);
  const [open, setOpen] = useState(
    () =>
      new Set([
        focus ?? section.scenarios.find((scenario) => !isOtherPlatform(scenario.platform))?.id,
      ])
  );
  const options = buildOptions(sectionControls(section), values);
  const tabOptions = splitTabOptions(options.bottomTab);
  const push = () => openDetails(section, values, 'push');
  const modal = () => openDetails(section, values, 'modal');
  const list = () => openDetails(section, values, 'list');
  const demos = {
    tabs: () => openTabsDemo(section, values),
    sideMenu: () => openSideMenuDemo(section, values),
    splitView: () => openSplitViewDemo(section, values),
  };
  const show = section.demo ? demos[section.demo] : modal;
  const mode: PanelMode = section.runsOnly
    ? 'none'
    : section.modalOnly || section.demo
      ? 'show'
      : 'details';
  usePanelToolbar({ push, modal, list, show, reset }, mode);
  const toggle = (id: string) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  return (
    <View style={styles.flex}>
      <Screen underBar>
        <View style={styles.header}>
          <SectionBadge id={section.id} size={40} />
          <Tag look="dark" label={`${section.scenarios.length} scenarios`} />
          <Tag look="soft" label={platforms(section)} />
        </View>
        <RichText style={styles.intro}>{section.intro}</RichText>
        <Label>Scenarios</Label>
        <List>
          {section.scenarios.map((scenario) => (
            <ControlsGroup
              key={scenario.id}
              scenario={scenario}
              values={values}
              onChange={set}
              open={open.has(scenario.id)}
              onToggle={() => toggle(scenario.id)}
              onRun={() => setActiveSection(section)}
            />
          ))}
        </List>
        {section.runsOnly ? null : (
          <Card title="Code">
            <Code>
              {section.demo === 'tabs'
                ? showTabsCode(options, tabOptions.everyTab, tabOptions.inbox)
                : section.demo === 'sideMenu'
                  ? showSideMenuCode(options)
                  : section.demo === 'splitView'
                    ? showSplitViewCode(options)
                    : showCode(section.modalOnly ? 'modal' : 'push', options)}
            </Code>
            {mode === 'show' ? null : (
              <Muted>Modal: the same options with navigation.showModal.</Muted>
            )}
          </Card>
        )}
      </Screen>
      {Platform.OS === 'ios' || mode === 'none' ? null : (
        <Footer>
          {mode === 'show' ? <Button title="Show" variant="primary" onPress={show} /> : null}
          {mode === 'details' ? <Button title="Push" variant="primary" onPress={push} /> : null}
          {mode === 'details' ? <Button title="Modal" onPress={modal} /> : null}
          {mode === 'details' ? <Button title="List" onPress={list} /> : null}
          <Button title="Reset" variant="danger" onPress={reset} />
        </Footer>
      )}
    </View>
  );
}

/** The platforms of a section's scenarios: both, unless every scenario is of one. */
function platforms(section: Section) {
  const names = new Set(section.scenarios.map((scenario) => scenario.platform));
  return names.has(undefined)
    ? 'iOS · Android'
    : [...names].map((name) => platformLabel(name!)).join(' · ');
}

function SectionPanel({ id, focus }: { id: number; focus?: string }) {
  const section = findSection(id);
  if (!section) return <Muted>No section {id}.</Muted>;
  return <Panel section={section} focus={focus} />;
}

export const SectionScreen = defineScreen({
  component: SectionPanel,
  // The tabs are the Showcase's: the panels and the screens they show go without them. The screen goes down to the
  // bottom edge: on iOS the list keeps its rows above the toolbar itself, on Android the footer keeps its buttons above
  // the system bar. On Android the list keeps its first rows clear of the large title itself (`Screen underBar`). On
  // iOS it goes to the sides too, so its scroll bar stands at the edge as on a UIKit screen: `Screen` keeps the rows
  // clear of the vertical bar of iPhone Duo and of the cutout.
  options: (s, props) => {
    const screen = s
      .title(findSection(props.id)?.title ?? 'Section')
      .largeTitle()
      .topBar(barFonts)
      .hideBottomTabs();
    return Platform.OS === 'android' ? screen.safeArea('right', 'left') : screen.safeArea('top');
  },
});

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 },
  intro: { fontSize: 15, lineHeight: 23, marginBottom: 22, marginHorizontal: 4 },
});
