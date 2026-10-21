import React, { ReactNode } from 'react';
import { StyleProp, View, ViewStyle } from 'react-native';

/**
 * A flat shadow of one color right under its child, `offset` points down, as on the brand's badges. The child has a
 * background of its own and the same `radius`.
 */
export function Raised({
  color,
  radius,
  offset = 4,
  style,
  children,
}: {
  color: string;
  radius: number;
  offset?: number;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  return (
    <View style={[{ backgroundColor: color, borderRadius: radius, paddingBottom: offset }, style]}>
      {children}
    </View>
  );
}
