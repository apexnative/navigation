import React, { useState } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  defineScreen,
  useNavigation,
  useSafeAreaInsets,
  type SafeAreaEdge,
} from '@apexnative/navigation';
import { Chip } from '../ui';

const EDGES: SafeAreaEdge[] = ['top', 'right', 'bottom', 'left'];
// Coral: the screen out of its safe area (under the bars, the status bar, the gesture handle, the camera). Blue: its
// content, framed.
const OUTSIDE = '#FF7675';
const INSIDE = '#74B9FF';
const FRAME = '#0652DD';

/**
 * The safe area in colors, to check it on a device: each switch changes the screen's options at once. Coral shows
 * where the safe area keeps the content off, blue is the content; the numbers are `useSafeAreaInsets()`.
 */
function SafeArea() {
  const nav = useNavigation();
  const insets = useSafeAreaInsets();
  const [edges, setEdges] = useState<SafeAreaEdge[]>(EDGES);
  const [topBar, setTopBar] = useState(true);
  const [statusBar, setStatusBar] = useState(true);
  const [underTopBar, setUnderTopBar] = useState(false);
  const [underStatusBar, setUnderStatusBar] = useState(false);
  const [tabs, setTabs] = useState(false);
  const [landscape, setLandscape] = useState(false);

  const toggleEdge = (edge: SafeAreaEdge) => {
    const next = edges.includes(edge) ? edges.filter((e) => e !== edge) : [...edges, edge];
    setEdges(next);
    nav.setOptions({ layout: { safeAreaEdges: next } });
  };

  const switches: { label: string; on: boolean; toggle: () => void; platform?: string }[] = [
    {
      label: 'Top bar',
      on: topBar,
      toggle: () => {
        setTopBar(!topBar);
        nav.setOptions({ topBar: { visible: !topBar } });
      },
    },
    {
      label: 'Status bar',
      on: statusBar,
      toggle: () => {
        setStatusBar(!statusBar);
        nav.setOptions({ statusBar: { visible: !statusBar } });
      },
    },
    {
      label: 'Under the top bar',
      on: underTopBar,
      toggle: () => {
        setUnderTopBar(!underTopBar);
        nav.setOptions({ topBar: { drawBehind: !underTopBar } });
      },
    },
    {
      label: 'Under the status bar',
      on: underStatusBar,
      platform: 'android',
      toggle: () => {
        setUnderStatusBar(!underStatusBar);
        nav.setOptions({ statusBar: { drawBehind: !underStatusBar } });
      },
    },
    {
      label: 'Tabs',
      on: tabs,
      toggle: () => {
        setTabs(!tabs);
        nav.setOptions({ bottomTabs: { visible: !tabs } });
      },
    },
    {
      label: 'Landscape',
      on: landscape,
      toggle: () => {
        setLandscape(!landscape);
        nav.setOptions({ layout: { orientation: [!landscape ? 'landscape' : 'portrait'] } });
      },
    },
  ];

  return (
    <View style={styles.inside}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Safe area</Text>
        <Text style={styles.text}>
          Coral is the screen out of its safe area, blue is its content. useSafeAreaInsets():
        </Text>
        <Text style={styles.insets}>
          top {round(insets.top)} · right {round(insets.right)} · bottom {round(insets.bottom)} ·
          left {round(insets.left)}
        </Text>
        <Text style={styles.label}>Edges the content keeps off (layout.safeAreaEdges)</Text>
        <View style={styles.chips}>
          {EDGES.map((edge) => (
            <Chip
              key={edge}
              label={edge}
              code
              selected={edges.includes(edge)}
              onPress={() => toggleEdge(edge)}
            />
          ))}
        </View>
        <Text style={styles.label}>Bars</Text>
        <View style={styles.chips}>
          {switches
            .filter((item) => !item.platform || item.platform === Platform.OS)
            .map((item) => (
              <Chip key={item.label} label={item.label} selected={item.on} onPress={item.toggle} />
            ))}
        </View>
        <Text style={styles.text}>
          Off an edge, the content goes to it: under the bar, the status bar, the gesture handle or
          the camera cutout (landscape shows it on a side).
        </Text>
      </ScrollView>
    </View>
  );
}

function round(value: number) {
  return Math.round(value * 10) / 10;
}

export const SafeAreaScreen = defineScreen({
  component: SafeArea,
  // The color of the screen's own view, under its safe area view: it is coral wherever the content is kept off.
  options: (s) =>
    s.title('Safe area').layout({ componentBackgroundColor: OUTSIDE }).hideBottomTabs(),
});

const styles = StyleSheet.create({
  inside: { flex: 1, backgroundColor: INSIDE, borderWidth: 3, borderColor: FRAME },
  content: { padding: 16, gap: 10 },
  title: { fontSize: 22, fontWeight: '700', color: '#0B1F3A' },
  text: { fontSize: 14, color: '#0B1F3A' },
  insets: { fontSize: 16, fontWeight: '700', color: '#0B1F3A' },
  label: { fontSize: 13, fontWeight: '600', color: '#0B1F3A', marginTop: 6 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
});
