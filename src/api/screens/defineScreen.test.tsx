import React, { createElement } from 'react';
import { commands as native } from '../../engine/native';
import { setProps } from '../../engine/props';
import {
  arrangementOf,
  loadScreens,
  noteShown,
  screenKey,
  screenOptions,
} from '../../engine/screens';
import { defineScreen, definitionOf } from './defineScreen';
import { registerScreens } from './registerScreens';

jest.mock('../../engine/native', () => ({
  commands: { mergeOptions: jest.fn(), clearOptions: jest.fn() },
  getStartInfo: () => ({ coldStart: true, arrangements: false }),
  onNativeEvent: () => () => {},
}));

// React Refresh as React Native gives it in development. Metro registers what a module exports in a family; when the
// module runs again, the new version joins the family of the old one, and the refresh after it makes it the current.
const families = new Map<unknown, { current: unknown }>();
function register(type: unknown, older?: unknown) {
  const family = (older !== undefined && families.get(older)) || { current: type };
  families.set(type, family);
}
function refresh(older: unknown, newer: unknown) {
  if (!families.has(older)) register(older);
  register(newer, older);
  families.get(older)!.current = newer;
}
beforeEach(() => {
  families.clear();
  (globalThis as Record<string, unknown>).__ReactRefresh = {
    getFamilyByType: (type: unknown) => families.get(type),
  };
});
afterEach(() => {
  delete (globalThis as Record<string, unknown>).__ReactRefresh;
});

function Profile(_props: { name: string }) {
  return null;
}

describe('a screen of defineScreen', () => {
  it('is a component of its own that renders its component, a boundary of Fast Refresh', () => {
    const ProfileScreen = defineScreen({
      component: Profile,
      options: { topBar: { title: { text: 'A' } } },
    });
    expect(ProfileScreen).not.toBe(Profile);
    expect(ProfileScreen.name).toMatch(/^[A-Z]/);
    expect(ProfileScreen.displayName).toBe('Profile');
    const element = (ProfileScreen as (props: object) => React.ReactElement)({ name: 'Ann' });
    expect(element.type).toBe(Profile);
    expect(definitionOf(ProfileScreen)?.options).toEqual({ topBar: { title: { text: 'A' } } });
  });

  it("leaves the app's component as it is, for a plain use of it and a second definition", () => {
    function Card() {
      return null;
    }
    const First = defineScreen({ component: Card, options: { topBar: { title: { text: '1' } } } });
    const Second = defineScreen({ component: Card, options: { topBar: { title: { text: '2' } } } });
    registerScreens([{ source: 'screens', screens: { First, Second, Plain: Card } }]);
    expect(screenOptions('First', {})).toEqual({ topBar: { title: { text: '1' } } });
    expect(screenOptions('Second', {})).toEqual({ topBar: { title: { text: '2' } } });
    expect(screenOptions('Plain', {})).toEqual({});
  });

  it('keeps its definition when the module of its component runs again', () => {
    // Profile's own module ran again: Profile's family has a new version, the screen's is the same.
    const ProfileScreen = defineScreen({
      component: Profile,
      options: { topBar: { title: { text: 'Kept' } } },
    });
    register(ProfileScreen);
    refresh(Profile, function ProfileEdited() {
      return null;
    });
    registerScreens([{ source: 'screens', screens: { Kept: ProfileScreen } }]);
    expect(screenOptions('Kept', {})).toEqual({ topBar: { title: { text: 'Kept' } } });
  });

  it("puts its component in a family by the screen's, once the module ran", async () => {
    const registered = jest.fn();
    (globalThis as unknown as { __ReactRefresh: Record<string, unknown> }).__ReactRefresh.register =
      registered;
    function Inbox() {
      return null;
    }
    const InboxScreen = defineScreen({ component: Inbox });
    register(InboxScreen);
    await Promise.resolve();
    const [[component, id]] = registered.mock.calls;
    expect(component).toBe(Inbox);

    // The module ran again: the new component goes under the same id.
    const InboxEdited = function InboxEdited() {
      return null;
    };
    const edited = defineScreen({ component: InboxEdited });
    register(edited, InboxScreen);
    await Promise.resolve();
    expect(registered.mock.calls[1]).toEqual([InboxEdited, id]);
  });

  it('shows with the options, key and pair of the newest version after Fast Refresh', () => {
    function Chat() {
      return null;
    }
    const before = defineScreen({
      component: Chat,
      options: (s) => s.title('Before'),
      key: (props: { id: string }) => props.id,
    });
    registerScreens([{ source: 'screens', screens: { Chat: before, Details: () => null } }]);
    expect(screenOptions('Chat', { id: '1' })).toEqual({ topBar: { title: { text: 'Before' } } });

    // The module ran again: a new function with a new definition, kept in the family of the first.
    const ChatEdited = function ChatEdited() {
      return null;
    };
    const after = defineScreen({
      component: ChatEdited,
      options: (s) => s.title('After'),
      key: (props: { id: string }) => `room-${props.id}`,
      arrangement: { secondary: 'Details' },
    });
    refresh(before, after);
    expect(screenOptions('Chat', { id: '1' })).toEqual({ topBar: { title: { text: 'After' } } });
    expect(screenKey('Chat', { id: '1' })).toBe('Chat:room-1');
    expect(arrangementOf('Chat')).toMatchObject({ primary: 'Chat', secondary: 'Details' });
  });

  it('keeps a plain definition object working', () => {
    registerScreens([
      {
        source: 'screens',
        screens: { Settings: { component: () => null, options: { topBar: { visible: false } } } },
      },
    ]);
    expect(screenOptions('Settings', {})).toEqual({ topBar: { visible: false } });
  });

  it('of a lazy screen gives its options once loaded, the newest after Fast Refresh', async () => {
    function Later() {
      return null;
    }
    const loaded = defineScreen({
      component: Later,
      options: { topBar: { title: { text: 'Loaded' } } },
    });
    registerScreens([
      { source: 'screens', screens: { Later: { load: () => ({ default: loaded }) } } },
    ]);
    await loadScreens(['Later']);
    expect(screenOptions('Later', {})).toEqual({ topBar: { title: { text: 'Loaded' } } });

    const LaterEdited = function LaterEdited() {
      return null;
    };
    refresh(
      loaded,
      defineScreen({ component: LaterEdited, options: { topBar: { title: { text: 'Edited' } } } })
    );
    expect(screenOptions('Later', {})).toEqual({ topBar: { title: { text: 'Edited' } } });
  });

  it('renders its screen when rendered on its own', () => {
    function Home(_props: { name: string }) {
      return null;
    }
    const element = createElement(defineScreen({ component: Home }), { name: 'Ann' });
    const rendered = (element.type as (props: object) => React.ReactElement)(element.props);
    expect(rendered.type).toBe(Home);
  });
});

describe('an edit of the options of a screen that shows', () => {
  const sent = native.mergeOptions as jest.Mock;

  it('sends what it changed to every screen of it that shows, with the props of each', async () => {
    function Chat() {
      return null;
    }
    const before = defineScreen({
      component: Chat,
      options: (s, props: { name: string }) => s.title(props.name).subtitle('Online'),
    });
    register(before);
    registerScreens([{ source: 'screens', screens: { Chat: before } }]);
    setProps('Component7', { name: 'Ann' });
    setProps('Component9', { name: 'Bob' });
    const hide7 = noteShown('Component7', 'Chat');
    noteShown('Component9', 'Chat');

    // The module ran again: Metro registers the new version, then React Refresh makes it the current.
    const ChatEdited = function ChatEdited() {
      return null;
    };
    const after = defineScreen({
      component: ChatEdited,
      options: (s, props: { name: string }) => s.title(`${props.name}!`).subtitle('Online'),
    });
    register(after, before);
    await Promise.resolve();
    refresh(before, after);

    expect(sent.mock.calls).toEqual([
      ['Component7', { topBar: { title: { text: 'Ann!' } } }],
      ['Component9', { topBar: { title: { text: 'Bob!' } } }],
    ]);

    // A screen that went is left alone, and the same options again send nothing.
    hide7();
    sent.mockClear();
    const again = defineScreen({
      component: function ChatAgain() {
        return null;
      },
      options: (s, props: { name: string }) => s.title(`${props.name}!`).subtitle('Online'),
    });
    register(again, after);
    await Promise.resolve();
    expect(sent).not.toHaveBeenCalled();
  });

  it('sends nothing for a screen of another module', async () => {
    function Home() {
      return null;
    }
    const home = defineScreen({
      component: Home,
      options: { topBar: { title: { text: 'Home' } } },
    });
    register(home);
    registerScreens([{ source: 'screens', screens: { Home: home } }]);
    noteShown('Component3', 'Home');
    const other = defineScreen({
      component: function Other() {
        return null;
      },
      options: { topBar: { title: { text: 'Other' } } },
    });
    register(other);
    await Promise.resolve();
    expect(sent).not.toHaveBeenCalled();
  });

  it('takes the options it took out off the screens that show', async () => {
    function Inbox() {
      return null;
    }
    const before = defineScreen({
      component: Inbox,
      options: (s) => s.title('Inbox').subtitle('3 new').largeTitle(),
    });
    register(before);
    registerScreens([{ source: 'screens', screens: { Inbox: before } }]);
    noteShown('Component11', 'Inbox');
    const after = defineScreen({
      component: function InboxEdited() {
        return null;
      },
      options: (s) => s.title('Inbox'),
    });
    register(after, before);
    await Promise.resolve();
    expect((native.clearOptions as jest.Mock).mock.calls).toEqual([
      ['Component11', ['topBar.subtitle', 'topBar.largeTitle']],
    ]);
    expect(sent).not.toHaveBeenCalled();
  });
});
