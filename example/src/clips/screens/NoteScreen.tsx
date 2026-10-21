import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  defineScreen,
  type NavigationButton,
  useNavigationButton,
  useScreenOptions,
} from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { fonts } from '../../ui';
import { md } from '../../ui/icons';
import { ITEMS } from '../data';

export interface NoteProps {
  /**
   * What the bar shows: `menus` the buttons with menus (the default), `buttons` the kinds of bar buttons, `title` a
   * menu on the title with Rename, `toolbar` a toolbar at the bottom, `popover` a filter button and a sort chip that
   * popovers point at.
   */
  kind?: 'menus' | 'buttons' | 'title' | 'toolbar' | 'popover';
}

const MENUS: NavigationButton[] = [
  {
    id: 'more',
    icon: { ios: 'ellipsis.circle', android: 'ic_more_horiz' },
    menu: {
      items: [
        { id: 'share', title: 'Share', icon: { ios: 'square.and.arrow.up' } },
        { id: 'duplicate', title: 'Duplicate', icon: { ios: 'plus.square.on.square' } },
        { id: 'delete', title: 'Delete', destructive: true, icon: { ios: 'trash' } },
      ],
    },
  },
  {
    id: 'sort',
    icon: { ios: 'arrow.up.arrow.down', android: 'ic_swap_vert' },
    menu: {
      singleSelection: true,
      items: [
        { id: 'sort-date', title: 'Date', checked: true },
        { id: 'sort-name', title: 'Name' },
      ],
    },
  },
];

/** The kinds of bar buttons: a text, an icon (with a badge on iOS 26), a system one. */
function buttons(editing: boolean, badge: number): NavigationButton[] {
  return [
    { id: 'edit', text: editing ? 'Done' : 'Edit', ...(editing ? { style: 'prominent' } : {}) },
    {
      id: 'inbox',
      icon: { ios: 'tray', android: md('inbox') },
      ...(badge ? { badge } : {}),
    },
    { id: 'share', systemItem: 'action', icon: { android: md('share') } },
  ];
}

/** A note whose bar shows a kind of buttons or menus: the clips of the bar and its buttons. */
function Note({ kind = 'menus' }: NoteProps) {
  const c = useColors();
  const [sort, setSort] = useState('Date');
  const [editing, setEditing] = useState(false);
  const [badge, setBadge] = useState(0);
  useNavigationButton('sort-date', () => setSort('Date'));
  useNavigationButton('sort-name', () => setSort('Name'));
  useNavigationButton('edit', () => setEditing((value) => !value));
  useClipEvent('badge', (count) => setBadge(Number(count)));
  useScreenOptions((s) => (kind === 'buttons' ? s.rightButtons(...buttons(editing, badge)) : s));
  if (kind === 'toolbar' || kind === 'title') {
    return (
      <ScrollView
        contentContainerStyle={styles.page}
        contentInsetAdjustmentBehavior="automatic"
        style={{ backgroundColor: c.background }}
      >
        {ITEMS.slice(0, 18).map((item, index) => (
          <Text key={item} style={[styles.line, { color: c.text }]}>
            {index % 4 === 0 ? '◻︎ ' : '· '}
            {item}
          </Text>
        ))}
      </ScrollView>
    );
  }
  if (kind === 'popover') {
    return (
      <View style={[styles.screen, { backgroundColor: c.background }]}>
        <View nativeID="sort" style={[styles.chip, { backgroundColor: c.card }]}>
          <Text style={[styles.chipText, { color: c.text }]}>Sorted by Date ▾</Text>
        </View>
      </View>
    );
  }
  return (
    <View style={[styles.screen, { backgroundColor: c.background }]}>
      <Text style={[styles.text, { color: c.muted }]}>
        {kind === 'buttons' ? (editing ? 'Editing' : 'Weekly plan') : `Sorted by ${sort}`}
      </Text>
    </View>
  );
}

export const NoteScreen = defineScreen({
  component: Note,
  options: (s, { kind = 'menus' }: NoteProps) => {
    const note = s.title('Weekly plan');
    switch (kind) {
      case 'buttons':
        return note.rightButtons(...buttons(false, 0));
      case 'title':
        return note.topBar({
          title: {
            text: 'Weekly plan',
            rename: true,
            menu: {
              items: [
                { id: 'duplicate', title: 'Duplicate', icon: { ios: 'plus.square.on.square' } },
                { id: 'move', title: 'Move', icon: { ios: 'folder' } },
              ],
            },
          },
        });
      case 'toolbar':
        return note.toolbar({
          buttons: [
            { id: 'check', text: 'Check', icon: { ios: 'checklist' } },
            { systemItem: 'flexibleSpace' },
            { id: 'photo', text: 'Photo', icon: { ios: 'camera' } },
            { systemItem: 'flexibleSpace' },
            { id: 'compose', text: 'Compose', icon: { ios: 'square.and.pencil' } },
          ],
        });
      case 'popover':
        return note.rightButtons({
          id: 'filter',
          icon: { ios: 'line.3.horizontal.decrease.circle', android: md('filter_list') },
        });
      default:
        return note.rightButtons(...MENUS);
    }
  },
});

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  text: { fontFamily: fonts.body, fontSize: 17 },
  page: { padding: 20, gap: 14 },
  line: { fontFamily: fonts.body, fontSize: 18 },
  chip: { borderRadius: 20, paddingHorizontal: 18, paddingVertical: 10 },
  chipText: { fontFamily: fonts.bodySemiBold, fontSize: 16 },
});
