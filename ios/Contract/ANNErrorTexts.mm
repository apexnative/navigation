#import "ANNErrorTexts.h"

NSExceptionName const ANNCommandRejectionException = @"ANNCommandRejectionException";

static NSString *ANNText(NSString *format, ...) NS_FORMAT_FUNCTION(1, 2);

static NSString *ANNText(NSString *format, ...) {
    va_list arguments;
    va_start(arguments, format);
    NSString *text = [[NSString alloc] initWithFormat:format arguments:arguments];
    va_end(arguments);
    return text;
}

@implementation ANNErrorTexts

#pragma mark - Where the screen is

+ (NSString *)screenNotShown:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not shown", componentId);
}

+ (NSString *)notInStack:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not in a stack", componentId);
}

+ (NSString *)firstScreenOfStack:(NSString *)componentId {
    return ANNText(@"the screen '%@' is the first screen of its stack", componentId);
}

+ (NSString *)notInThisStack:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not in this stack", componentId);
}

+ (NSString *)notInModal:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not in a modal", componentId);
}

+ (NSString *)notInOverlay:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not in an overlay", componentId);
}

+ (NSString *)notInSplitView:(NSString *)componentId {
    return ANNText(@"the screen '%@' is not in a split view", componentId);
}

+ (NSString *)neitherSplitViewNorStack:(NSString *)componentId {
    return ANNText(@"the screen '%@' is neither in a split view nor in a stack", componentId);
}

+ (NSString *)noSuchColumn:(NSString *)componentId column:(NSString *)column {
    return ANNText(@"the split view of the screen '%@' has no '%@' column", componentId, column);
}

+ (NSString *)columnIsStack:(NSString *)componentId column:(NSString *)column {
    return ANNText(@"the '%@' column of the split view of the screen '%@' is a stack: it takes a "
                   @"screen, not a stack",
                   column, componentId);
}

+ (NSString *)columnNotStack:(NSString *)componentId column:(NSString *)column {
    return ANNText(@"the '%@' column of the split view of the screen '%@' is not a stack", column,
                   componentId);
}

+ (NSString *)noScreensForStack:(NSString *)componentId {
    return ANNText(@"there are no screens to put in the stack of the screen '%@'", componentId);
}

+ (NSString *)alreadyShown:(NSString *)componentId {
    return ANNText(@"the screen '%@' shows already: an id goes to one screen", componentId);
}

#pragma mark - Root, modals, the app

+ (NSString *)setRootReplaced:(NSString *)rootId {
    return ANNText(@"setRoot was replaced by a newer setRoot before the root '%@' finished showing",
                   rootId);
}

+ (NSString *)rootDestroyed:(NSString *)rootId {
    return ANNText(@"the root '%@' was destroyed before it showed", rootId);
}

+ (NSString *)modalWithoutRoot:(NSString *)componentId {
    return ANNText(@"the modal '%@' can't be dismissed: there is no root under it", componentId);
}

+ (NSString *)nowhereToShow {
    return @"the navigation has nowhere to show screens: the app has no activity or window";
}

#pragma mark - Native screens

+ (NSString *)nativeScreenNotRegistered:(NSString *)name {
    return ANNText(@"no native screen is registered as '%@'", name);
}

+ (NSString *)nativeScreenNotCreated:(NSString *)name {
    return ANNText(@"the native screen '%@' could not be created: its creator returned nothing",
                   name);
}

+ (NSString *)nativeCallReloaded:(NSString *)name componentId:(NSString *)componentId {
    return ANNText(@"JavaScript reloaded before the call '%@' of the screen '%@' was answered",
                   name, componentId);
}

+ (NSString *)nativeCallNotRunning:(NSString *)name componentId:(NSString *)componentId {
    return ANNText(@"the navigation is not running: the call '%@' of the screen '%@' can't be "
                   @"answered",
                   name, componentId);
}

#pragma mark - The layout

+ (NSString *)unknownNodeType:(NSString *)type {
    return ANNText(@"the layout has a node of unknown type '%@'", type);
}

+ (NSString *)nodeWithoutId:(NSString *)type {
    return ANNText(@"the layout has a node of type '%@' without an id", type);
}

+ (NSString *)sideMenuChild:(NSString *)menuId type:(NSString *)type {
    return ANNText(@"the side menu '%@' has a child of type '%@': it takes SideMenuLeft, "
                   @"SideMenuCenter and SideMenuRight",
                   menuId, type);
}

+ (NSString *)stackTakesScreens:(NSString *)stackId type:(NSString *)type {
    return ANNText(@"the stack '%@' takes screens, not a '%@'", stackId, type);
}

+ (NSString *)emptyStack:(NSString *)stackId {
    return ANNText(@"the stack '%@' has no screens", stackId);
}

+ (NSString *)emptyTabs:(NSString *)tabsId {
    return ANNText(@"the tabs '%@' have no tabs", tabsId);
}

+ (NSString *)tooManyTabs:(NSString *)tabsId count:(NSUInteger)count {
    return ANNText(@"the tabs '%@' have %lu tabs: Android shows at most 5", tabsId,
                   (unsigned long)count);
}

+ (NSString *)splitViewColumns:(NSString *)splitViewId {
    return ANNText(@"the split view '%@' needs its primary and its secondary column", splitViewId);
}

+ (NSString *)pairUnsupported:(NSString *)pairId {
    return ANNText(@"the pair '%@' needs iOS 27.1 and an app built with its SDK", pairId);
}

+ (NSString *)pairNeedsTwoScreens:(NSString *)pairId {
    return ANNText(@"the pair '%@' needs two screens: its primary and its secondary", pairId);
}

+ (NSString *)nodeWithoutName:(NSString *)type nodeId:(NSString *)nodeId {
    return ANNText(@"the layout has a node '%@' of type '%@' without a name", nodeId, type);
}

+ (NSString *)rootMissing {
    return @"the layout of setRoot has no root";
}

+ (NSString *)tabsInTabs:(NSString *)tabsId {
    return ANNText(@"the tabs '%@' have a tab of type 'BottomTabs': tabs don't go inside tabs",
                   tabsId);
}

+ (NSString *)sideMenuSides:(NSString *)menuId {
    return ANNText(@"the side menu '%@' takes one SideMenuCenter and at most one SideMenuLeft and "
                   @"one SideMenuRight",
                   menuId);
}

+ (NSString *)sideChildCount:(NSString *)sideId menuId:(NSString *)menuId {
    return ANNText(@"the side '%@' of the side menu '%@' needs exactly one child", sideId, menuId);
}

+ (NSString *)sideMenuInSide:(NSString *)menuId {
    return ANNText(@"the side menu '%@' has a side menu in a side: a side menu doesn't go inside a "
                   @"side menu",
                   menuId);
}

+ (NSString *)sideOutsideMenu:(NSString *)nodeId type:(NSString *)type {
    return ANNText(@"the layout has a node '%@' of type '%@' outside a side menu", nodeId, type);
}

+ (NSString *)splitViewInColumn:(NSString *)splitViewId {
    return ANNText(@"the split view '%@' has a column of type 'SplitView': a split view doesn't go "
                   @"inside a split view",
                   splitViewId);
}

+ (NSString *)idGivenTwice:(NSString *)nodeId {
    return ANNText(@"the layout gives the id '%@' to two nodes", nodeId);
}

#pragma mark - Options, previews, any other failure

+ (NSString *)unreadableOption:(NSString *)path componentId:(NSString *)componentId {
    return ANNText(@"the option '%@' of the screen '%@' has a value the navigation can't read",
                   path, componentId);
}

+ (NSString *)unreadableDefaultOption:(NSString *)path {
    return ANNText(@"the option '%@' of the default options has a value the navigation can't read", path);
}

+ (NSString *)previewGone:(NSString *)previewId {
    return ANNText(@"the preview '%@' went without being opened", previewId);
}

+ (NSString *)couldNot:(NSString *)what reason:(NSString *)reason {
    return ANNText(@"could not %@: %@", what, reason);
}

+ (NSString *)notDoneYet:(NSString *)method {
    return ANNText(@"could not run '%@': the native core doesn't do it yet", method);
}

+ (NSException *)rejection:(NSString *)text {
    return [NSException exceptionWithName:ANNCommandRejectionException reason:text userInfo:nil];
}

@end
