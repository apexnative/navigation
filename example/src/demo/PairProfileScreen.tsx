import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  type ArrangementState,
  defineScreen,
  useArrangement,
  useNavigation,
  useReservedRegions,
} from '@apexnative/navigation';
import { brand, Button, Card, fonts, KeyValue, Muted, Screen } from '../ui';

/**
 * The primary of the pair demo of 11.7, tinted coral. The bar of the pair is its own. Alone it says why: a window too
 * small for a pair, or a device without pairs.
 */
function PairProfile({ id }: { id: string }) {
  const nav = useNavigation();
  const pair = useArrangement();
  const { division } = useReservedRegions();
  const fold = division[0];
  return (
    <Screen style={[styles.tint, { backgroundColor: PRIMARY }]}>
      <PairBadge text={`${roleOf(pair)} · Profile`} color={brand.coral} />
      <Card title={`Profile ${id}`}>
        <Muted>{explain(pair)}</Muted>
        <KeyValue label="useArrangement" value={describePair(pair)} />
        <KeyValue
          label="The fold"
          value={fold ? `x ${Math.round(fold.x)} · y ${Math.round(fold.y)}` : 'none'}
        />
      </Card>
      <Card title="Around">
        <Button title="Back: both screens go" onPress={() => nav.pop()} />
      </Card>
    </Screen>
  );
}

const PRIMARY = 'rgba(231, 111, 81, 0.16)';

function explain(pair: ArrangementState | null): string {
  if (!pair) return 'Not in a pair.';
  if (pair.placement === 'secondary') {
    return 'Details leads this pair: the bar is Details’, and Details shows alone outside a large window. Profile stays on the leading side.';
  }
  if (pair.layout !== 'single') {
    return 'Details is the other screen of this pair. The bar is Profile’s; back takes both.';
  }
  return 'Only Profile shows: the window is too small for a pair (a phone, iPhone Duo folded) or the device shows no pairs (before iOS 27.1). Open iPhone Duo, or use “Push the pair on any window”.';
}

/** The state of a pair in one line, as the screens of 11.7 show it. */
export function describePair(pair: ArrangementState | null): string {
  if (!pair) return 'null — not in a pair';
  const marks = [pair.front && 'front', pair.hidden && 'hidden'].filter(Boolean);
  return [pair.placement, pair.layout, ...marks].join(' · ');
}

/** The role of a screen in its pair, for its badge: a screen alone counts as the primary. */
export function roleOf(pair: ArrangementState | null): string {
  return pair?.placement === 'secondary' ? 'SECONDARY' : 'PRIMARY';
}

/** A big label that tells the two screens of a pair apart. */
export function PairBadge({ text, color }: { text: string; color: string }) {
  return (
    <View style={[styles.badge, { backgroundColor: color }]}>
      <Text style={styles.badgeText}>{text}</Text>
    </View>
  );
}

export const PairProfileScreen = defineScreen({
  component: PairProfile,
  options: (s, props: { id: string }) => s.title(`Profile ${props.id}`),
  arrangement: { secondary: 'PairDetails' },
});

const styles = StyleSheet.create({
  tint: { flexGrow: 1 },
  badge: { alignSelf: 'flex-start', borderRadius: 999, paddingHorizontal: 14, paddingVertical: 6 },
  badgeText: { fontFamily: fonts.monoMedium, fontSize: 14, color: brand.deep },
});
