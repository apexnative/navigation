import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { Shape, Transform } from './shapes';

/** A shape of the turned shared elements at `size`, turned by `transform`, with the `nativeID` `id`. */
export function ShapeView({
  shape,
  id,
  size,
  transform,
}: {
  shape: Shape;
  id: string;
  size: number;
  transform: Transform;
}) {
  if (shape.kind === 'text') {
    return (
      <Text
        nativeID={id}
        style={[styles.text, { color: shape.color, fontSize: size * 0.28, transform }]}
      >
        Turn
      </Text>
    );
  }
  if (shape.kind === 'plain') {
    return (
      <View
        nativeID={id}
        style={{
          width: size,
          height: size * 0.5,
          borderRadius: size * 0.08,
          backgroundColor: shape.color,
          transform,
        }}
      />
    );
  }
  return (
    <View
      nativeID={id}
      style={[
        styles.marked,
        {
          width: size,
          height: size,
          borderRadius: size * 0.14,
          backgroundColor: shape.color,
          transform,
        },
      ]}
    >
      <View
        style={[
          styles.mark,
          {
            width: size * 0.22,
            height: size * 0.22,
            borderRadius: size * 0.11,
            margin: size * 0.1,
          },
        ]}
      />
      <Text style={[styles.label, { fontSize: size * 0.16 }]}>Top</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  marked: { alignItems: 'flex-start' },
  mark: { backgroundColor: 'white' },
  label: { color: 'white', fontWeight: '800', marginLeft: '10%' },
  text: { fontWeight: '800' },
});
