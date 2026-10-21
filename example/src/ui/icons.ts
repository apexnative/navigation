import { ImageRequireSource, Platform } from 'react-native';

export const icons = {
  home: require('../assets/icons/home.png') as ImageRequireSource,
  stack: require('../assets/icons/stack.png') as ImageRequireSource,
  native: require('../assets/icons/native.png') as ImageRequireSource,
  menu: require('../assets/icons/menu.png') as ImageRequireSource,
  plus: require('../assets/icons/plus.png') as ImageRequireSource,
  star: require('../assets/icons/star.png') as ImageRequireSource,
  bell: require('../assets/icons/bell.png') as ImageRequireSource,
  share: require('../assets/icons/share.png') as ImageRequireSource,
  close: require('../assets/icons/close.png') as ImageRequireSource,
  heart: require('../assets/icons/heart.png') as ImageRequireSource,
  gear: require('../assets/icons/gear.png') as ImageRequireSource,
  person: require('../assets/icons/person.png') as ImageRequireSource,
  search: require('../assets/icons/search.png') as ImageRequireSource,
};

/**
 * A Material Symbol on Android, where iOS shows an SF Symbol: the app's vector drawable `res/drawable/ic_<name>.xml`
 * (Material Symbols Outlined by Google, Apache 2.0), named by `icon`. Nothing on iOS.
 */
export function md(name: MaterialSymbol): string | undefined {
  return Platform.OS === 'android' ? `ic_${name}` : undefined;
}

export type MaterialSymbol =
  | 'add'
  | 'arrow_back'
  | 'bookmark'
  | 'calendar_add_on'
  | 'category'
  | 'close'
  | 'content_copy'
  | 'delete'
  | 'draw'
  | 'edit_square'
  | 'expand_more'
  | 'favorite'
  | 'filter_list'
  | 'flag'
  | 'folder'
  | 'format_bold'
  | 'format_italic'
  | 'format_list_bulleted'
  | 'format_underlined'
  | 'help'
  | 'home'
  | 'inbox'
  | 'library_add'
  | 'location_on'
  | 'mail'
  | 'menu'
  | 'more_horiz'
  | 'radio_button_checked'
  | 'redo'
  | 'refresh'
  | 'schedule'
  | 'search'
  | 'settings'
  | 'share'
  | 'star'
  | 'swap_vert'
  | 'text_format'
  | 'undo';

/** An SF Symbol on iOS, the PNG of a Material Symbol on Android. */
export function icon(name: keyof typeof icons, sfSymbol: string) {
  return { icon: { ios: sfSymbol, android: icons[name] } };
}
