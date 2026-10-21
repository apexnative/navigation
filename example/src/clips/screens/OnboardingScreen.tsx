import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { defineScreen, Root, useSafeAreaInsets } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { TILES, useColors } from '../colors';
import { Button, fonts } from '../../ui';
import { useClipNavigation } from '../nav';
import { mainTabs } from '../session';

export interface OnboardingProps {
  step: number;
}

const STEPS = [
  { title: 'Keep your lists', text: 'Notes, plans and ideas, in one place.' },
  { title: 'Share them', text: 'Invite people to a list and plan together.' },
  { title: 'Never miss a thing', text: 'Reminders when a list changes.' },
];

/** A step of the onboarding: Next pushes the next one, the last one and Skip go to the app. */
function Onboarding({ step }: OnboardingProps) {
  const c = useColors();
  const insets = useSafeAreaInsets();
  const nav = useClipNavigation();
  const last = step >= STEPS.length;
  const next = () =>
    last
      ? nav.switchTo(Root(mainTabs()).transition('fade'))
      : nav.push('Onboarding', { props: { step: step + 1 } });
  useClipEvent('next', next);
  const page = STEPS[step - 1] ?? STEPS[0]!;
  return (
    <View
      style={[
        styles.screen,
        {
          backgroundColor: c.background,
          paddingTop: insets.top + 12,
          paddingBottom: insets.bottom + 24,
        },
      ]}
    >
      <Pressable
        style={styles.skip}
        onPress={() => nav.switchTo(Root(mainTabs()).transition('fade'))}
      >
        <Text style={[styles.skipText, { color: c.muted }]}>Skip</Text>
      </Pressable>
      <View style={[styles.art, { backgroundColor: TILES[step % TILES.length] }]} />
      <Text style={[styles.title, { color: c.text }]}>{page.title}</Text>
      <Text style={[styles.text, { color: c.muted }]}>{page.text}</Text>
      <View style={styles.dots}>
        {STEPS.map((_, index) => (
          <View
            key={index}
            style={[styles.dot, { backgroundColor: index === step - 1 ? c.tint : c.line }]}
          />
        ))}
      </View>
      <Button title={last ? 'Get started' : 'Next'} variant="primary" onPress={next} />
    </View>
  );
}

export const OnboardingScreen = defineScreen({
  component: Onboarding,
  options: (s) => s.hideTopBar(),
});

const styles = StyleSheet.create({
  screen: { flex: 1, paddingHorizontal: 24 },
  skip: { alignSelf: 'flex-end', padding: 8 },
  skipText: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
  art: { flex: 1, borderRadius: 28, marginVertical: 24 },
  title: { fontFamily: fonts.display, fontSize: 30, textAlign: 'center' },
  text: { fontFamily: fonts.body, fontSize: 17, textAlign: 'center', marginTop: 10 },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginVertical: 24 },
  dot: { width: 8, height: 8, borderRadius: 4 },
});
