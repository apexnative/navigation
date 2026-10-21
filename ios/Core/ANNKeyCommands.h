#import <UIKit/UIKit.h>

/**
 * `keyCommands` of a screen: `{ id, input, modifiers, title }` as UIKeyCommands. `input` is a
 * character or 'escape', 'enter', 'tab', 'delete', 'up', 'down', 'left', 'right'; `modifiers` are
 * 'command', 'shift', 'option' (or 'alternate') and 'control'. The id of a command is its
 * property list.
 */
NSArray<UIKeyCommand *> *ANNKeyCommandsFromOptions(NSArray<NSDictionary *> *options, SEL action);

/** Nothing takes the keyboard now: no first responder, or the window or the app. */
BOOL ANNNoFirstResponder(void);
