// Made by scripts/contract.mjs from src/interfaces/contract/commands.json: change that file and run the script again.

#import "ANNCommandContract.h"

// What each command resolves with:
// - `setRoot`: the id of the root, once the root and the layout's modals and overlays show.
// - `push`: the id of the screen that shows; of a pair, the pair's id.
// - `preparePush`: the frame `{ width, height, padding }` of the prepared screen, or null; always
//   null on iOS.
// - `pop`: the id of the screen that went.
// - `popTo`: the id of the screen it went back to.
// - `popToRoot`: the id of the first screen of the stack.
// - `setStackRoot`: the id of the new top screen.
// - `showModal`: the id of the modal's root.
// - `dismissModal`: the id of the modal's root.
// - `dismissAllModals`: an empty string.
// - `showOverlay`: the id of the overlay's root.
// - `dismissOverlay`: the id of the overlay's root.
// - `dismissAllOverlays`: an empty string.
// - `showInColumn`: the id of the screen that shows.
// - `setColumnShown`: nothing once the columns stopped moving (`undefined` on iOS, `null` on
//   Android).
// - `getLaunchLink`: the link that launched the app, or null.

static NSSet<NSString *> *ANNStateCommandNames(void) {
    static NSSet<NSString *> *names;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      names = [NSSet setWithArray:@[
          @"setRoot",
          @"push",
          @"pop",
          @"popTo",
          @"popToRoot",
          @"setStackRoot",
          @"showModal",
          @"dismissModal",
          @"dismissAllModals",
          @"showOverlay",
          @"dismissOverlay",
          @"dismissAllOverlays",
          @"showInColumn",
          @"setColumnShown",
      ]];
    });
    return names;
}

static NSSet<NSString *> *ANNCompletedCommandNames(void) {
    static NSSet<NSString *> *names;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      names = [NSSet setWithArray:@[
          @"setRoot",
          @"push",
          @"pop",
          @"popTo",
          @"popToRoot",
          @"setStackRoot",
          @"showModal",
          @"dismissModal",
          @"dismissAllModals",
          @"showOverlay",
          @"dismissOverlay",
          @"dismissAllOverlays",
          @"showInColumn",
      ]];
    });
    return names;
}

BOOL ANNCommandReportsState(NSString *name) {
    return [ANNStateCommandNames() containsObject:name];
}

BOOL ANNCommandSendsCompleted(NSString *name) {
    return [ANNCompletedCommandNames() containsObject:name];
}
