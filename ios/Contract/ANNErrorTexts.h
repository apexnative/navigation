#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The name of an exception whose reason is the whole text a command rejects with (one of the texts below), as it is:
/// a maker deep in a command's work throws it where the command can't answer itself. Any other exception rejects
/// with `couldNot:reason:`.
FOUNDATION_EXPORT NSExceptionName const ANNCommandRejectionException;

/// Every text the native side rejects a command with or logs for a command without a promise: one sentence per case,
/// the same on iOS and Android, the values in single quotes. Other files take their texts from here. Any thread.
@interface ANNErrorTexts : NSObject

#pragma mark - Where the screen is

+ (NSString *)screenNotShown:(NSString *)componentId;
+ (NSString *)notInStack:(NSString *)componentId;
+ (NSString *)firstScreenOfStack:(NSString *)componentId;
+ (NSString *)notInThisStack:(NSString *)componentId;
+ (NSString *)notInModal:(NSString *)componentId;
+ (NSString *)notInOverlay:(NSString *)componentId;
+ (NSString *)notInSplitView:(NSString *)componentId;
+ (NSString *)neitherSplitViewNorStack:(NSString *)componentId;
+ (NSString *)noSuchColumn:(NSString *)componentId column:(NSString *)column;
+ (NSString *)columnIsStack:(NSString *)componentId column:(NSString *)column;
+ (NSString *)columnNotStack:(NSString *)componentId column:(NSString *)column;
+ (NSString *)noScreensForStack:(NSString *)componentId;
+ (NSString *)alreadyShown:(NSString *)componentId;

#pragma mark - Root, modals, the app

+ (NSString *)setRootReplaced:(NSString *)rootId;
+ (NSString *)rootDestroyed:(NSString *)rootId;
+ (NSString *)modalWithoutRoot:(NSString *)componentId;
+ (NSString *)nowhereToShow;

#pragma mark - Native screens

+ (NSString *)nativeScreenNotRegistered:(NSString *)name;
+ (NSString *)nativeScreenNotCreated:(NSString *)name;
/// The error of a call of a native screen whose JavaScript reloaded before it answered.
+ (NSString *)nativeCallReloaded:(NSString *)name componentId:(NSString *)componentId;
/// The error of a call of a native screen while no JavaScript can answer it.
+ (NSString *)nativeCallNotRunning:(NSString *)name componentId:(NSString *)componentId;

#pragma mark - The layout

+ (NSString *)unknownNodeType:(NSString *)type;
+ (NSString *)nodeWithoutId:(NSString *)type;
+ (NSString *)sideMenuChild:(NSString *)menuId type:(NSString *)type;
+ (NSString *)stackTakesScreens:(NSString *)stackId type:(NSString *)type;
+ (NSString *)emptyStack:(NSString *)stackId;
+ (NSString *)emptyTabs:(NSString *)tabsId;
+ (NSString *)tooManyTabs:(NSString *)tabsId count:(NSUInteger)count;
+ (NSString *)splitViewColumns:(NSString *)splitViewId;
+ (NSString *)pairUnsupported:(NSString *)pairId;
+ (NSString *)pairNeedsTwoScreens:(NSString *)pairId;

/// The other cases of the layout check: the texts both platforms use meanwhile.
+ (NSString *)nodeWithoutName:(NSString *)type nodeId:(NSString *)nodeId;
+ (NSString *)rootMissing;
+ (NSString *)tabsInTabs:(NSString *)tabsId;
+ (NSString *)sideMenuSides:(NSString *)menuId;
+ (NSString *)sideChildCount:(NSString *)sideId menuId:(NSString *)menuId;
+ (NSString *)sideMenuInSide:(NSString *)menuId;
+ (NSString *)sideOutsideMenu:(NSString *)nodeId type:(NSString *)type;
+ (NSString *)splitViewInColumn:(NSString *)splitViewId;
+ (NSString *)idGivenTwice:(NSString *)nodeId;

#pragma mark - Options, previews, any other failure

+ (NSString *)unreadableOption:(NSString *)path componentId:(NSString *)componentId;
/// An unreadable value of `setDefaultOptions`: the default options belong to no screen.
+ (NSString *)unreadableDefaultOption:(NSString *)path;
+ (NSString *)previewGone:(NSString *)previewId;
/// Any other failure: what could not be done and why, `could not <what>: <reason>`.
+ (NSString *)couldNot:(NSString *)what reason:(NSString *)reason;
/// A command whose part of the native core is not written yet.
+ (NSString *)notDoneYet:(NSString *)method;

/// An `ANNCommandRejectionException` with `text` as its reason: the command that runs rejects with `text` as it is.
+ (NSException *)rejection:(NSString *)text;

@end

NS_ASSUME_NONNULL_END
