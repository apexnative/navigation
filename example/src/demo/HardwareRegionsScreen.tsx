import React from 'react';
import { StyleSheet, View } from 'react-native';
import {
  defineScreen,
  type Hinge,
  type ReservedRegion,
  useHinge,
  useReservedRegions,
} from '@apexnative/navigation';
import { Card, KeyValue, Muted, Screen } from '../ui';

/**
 * The screen of 6.9: the reserved regions of the screen drawn where they are (the fold, a camera) and the hinge. Fold
 * the device, or open it partly, and they change.
 */
function HardwareRegions() {
  const { division, occlusion } = useReservedRegions();
  const hinge = useHinge();
  return (
    <View style={styles.root}>
      <Screen>
        <Card title="Reserved regions">
          <Muted>
            The fold (orange) and what covers the screen (blue), in the screen&apos;s coordinates.
            Empty on a device without them, and before iOS 27.1.
          </Muted>
          <KeyValue label="division" value={describeRegions(division)} />
          <KeyValue label="occlusion" value={describeRegions(occlusion)} />
        </Card>
        <Card title="The hinge">
          <KeyValue label="useHinge" value={describeHinge(hinge)} />
        </Card>
      </Screen>
      {division.map((region, index) => (
        <View key={`d${index}`} pointerEvents="none" style={[styles.division, frame(region)]} />
      ))}
      {occlusion.map((region, index) => (
        <View key={`o${index}`} pointerEvents="none" style={[styles.occlusion, frame(region)]} />
      ))}
    </View>
  );
}

function describeRegions(regions: ReservedRegion[]): string {
  if (regions.length === 0) return 'none';
  return regions
    .map(({ x, y, width, height }) => `${round(x)},${round(y)} ${round(width)}×${round(height)}`)
    .join('; ');
}

function describeHinge(hinge: Hinge | null): string {
  if (!hinge) return 'null — no hinge';
  return hinge.angle === null ? hinge.state : `${hinge.state} · ${round(hinge.angle)}°`;
}

const round = (value: number) => Math.round(value);

// A region 0 wide (the fold itself) is drawn 2 points wide, so it shows.
function frame({ x, y, width, height }: ReservedRegion) {
  return { left: x, top: y, width: Math.max(width, 2), height: Math.max(height, 2) };
}

export const HardwareRegionsScreen = defineScreen({
  component: HardwareRegions,
  options: (s) => s.title('Regions and hinge'),
});

const styles = StyleSheet.create({
  root: { flex: 1 },
  division: { position: 'absolute', backgroundColor: 'rgba(232, 112, 80, 0.6)' },
  occlusion: { position: 'absolute', backgroundColor: 'rgba(80, 140, 232, 0.4)' },
});
