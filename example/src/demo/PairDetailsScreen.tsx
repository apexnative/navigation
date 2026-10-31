import React from 'react';
import { StyleSheet } from 'react-native';
import { defineScreen, useArrangement } from '@apexnative/navigation';
import { brand, Card, KeyValue, Muted, Screen } from '../ui';
import { describePair, PairBadge, roleOf } from './PairProfileScreen';

/**
 * The secondary of the pair demo of 11.7, tinted teal: beside or under Profile, out of the window when there is no room.
 * The primary of `PairDetailsFirst`: the bar is its own then, and it shows alone outside a large window.
 */
function PairDetails({ id }: { id: string }) {
  const pair = useArrangement();
  return (
    <Screen style={[styles.tint, { backgroundColor: SECONDARY }]}>
      <PairBadge text={`${roleOf(pair)} · Details`} color={brand.teal} />
      <Card title={`Details of ${id}`}>
        <Muted>
          A screen of its own with its own props. As the secondary its bar options have no effect:
          the bar is Profile’s. In an overlay it lies under Profile, and beside it when the device
          is partly folded. As the primary of the trailing pair the bar is its own, and it shows
          alone where Profile would.
        </Muted>
        <KeyValue label="useArrangement" value={describePair(pair)} />
      </Card>
    </Screen>
  );
}

const SECONDARY = 'rgba(42, 157, 143, 0.18)';

export const PairDetailsScreen = defineScreen({
  component: PairDetails,
  options: (s, props: { id: string }) => s.title(`Details ${props.id}`),
});

const styles = StyleSheet.create({
  tint: { flexGrow: 1 },
});
