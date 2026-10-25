import React from 'react';
import { FlatList } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useColors } from '../colors';
import { useHingeForClips } from '../hinge';
import { ITEMS } from '../data';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { md } from '../../ui/icons';
import { LIST_LAYOUT, useScrollBottomPadding, useSidePadding } from '../insets';

/**
 * A list with four right buttons, the first tab of the iPhone Duo clip: open, the buttons and the tabs go into the
 * vertical bar at the side, and the rows keep clear of it with the insets.
 */
function Board() {
  const c = useColors();
  const nav = useClipNavigation();
  const sides = useSidePadding();
  const bottom = useScrollBottomPadding();
  useHingeForClips();
  return (
    <FlatList
      data={ITEMS}
      keyExtractor={(item) => item}
      contentInsetAdjustmentBehavior="automatic"
      style={{ backgroundColor: c.background }}
      contentContainerStyle={[sides, bottom]}
      renderItem={({ item, index }) => (
        <Row
          index={index}
          title={item}
          subtitle="Updated today"
          onPress={() => nav.push('Details', { props: { index } })}
        />
      )}
    />
  );
}

export const BoardScreen = defineScreen({
  component: Board,
  options: (s) =>
    s
      .title('Notes')
      .layout(LIST_LAYOUT)
      .rightButtons(
        { id: 'add', text: 'Add', icon: { ios: 'plus', android: md('add') } },
        { id: 'share', text: 'Share', icon: { ios: 'square.and.arrow.up', android: md('share') } },
        {
          id: 'sort',
          text: 'Sort',
          icon: { ios: 'arrow.up.arrow.down', android: md('swap_vert') },
        },
        {
          id: 'filter',
          text: 'Filter',
          icon: { ios: 'line.3.horizontal.decrease', android: md('filter_list') },
        }
      ),
});
