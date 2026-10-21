import { navigation, Screen, SideMenu, Stack } from '@apexnative/navigation';
import type { ControlValues } from '../../sections/controls';
import { sectionControls, type Section } from '../../sections/scenario';
import { buildOptions } from './optionsCode';

/** A menu screen of the demo; on glass it has no background, so the glass shows through. */
function menu(side: 'left' | 'right', glass: boolean) {
  const screen = Screen('SideMenuMenu', { side, glass });
  return glass
    ? screen.options({
        layout: { backgroundColor: 'transparent', componentBackgroundColor: 'transparent' },
      })
    : screen;
}

/**
 * Shows a demo app with a side menu on each side as the root, with the `sideMenu` options of the Side menu panel. Its
 * center has buttons that open the menus and one back to the Showcase.
 */
export function openSideMenuDemo(section: Section, values: ControlValues) {
  const options = buildOptions(sectionControls(section), values);
  const sideMenu = options.sideMenu ?? {};
  return navigation.switchTo(
    SideMenu({
      left: menu('left', sideMenu.left?.glass === true),
      center: Stack(Screen('SideMenuCenter')),
      right: menu('right', sideMenu.right?.glass === true),
    }).options(sideMenu)
  );
}
