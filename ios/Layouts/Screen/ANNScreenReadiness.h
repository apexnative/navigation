#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// What the safe area view of a screen said about its content.
typedef NS_ENUM(NSInteger, ANNContentRendered) {
    /// No safe area view ever spoke for the id (a screen of the old API): its root's children decide.
    ANNContentRenderedUnknown,
    /// The screen has a safe area view, and its content didn't render into it yet.
    ANNContentRenderedNo,
    /// The content rendered into the screen's safe area view (also when it rendered nothing, `null`); for good.
    ANNContentRenderedYes,
};

/// Who waits for the signals of one id: the content of a screen or of a component (`ANNReactView`).
@protocol ANNScreenReadinessListener <NSObject>

@optional

/// The safe area view of the id is laid out in its place, with a size. Once per id.
- (void)screenReadinessDidLayOut:(NSString *)screenId;

/// The content of the id rendered into its safe area view. Once per id.
- (void)screenReadinessContentDidRender:(NSString *)screenId;

@end

/// Where the safe area view of a screen tells the screen's container that the screen is ready, by the screen's id: "it
/// has a safe area view", "laid out" and "content rendered". The core waits for these, with no search through the views
/// and no filter of React Native's shared notifications. The signals are sticky: the listener of an id hears each once,
/// at the signal or as it starts to listen. Main queue.
@interface ANNScreenReadiness : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

#pragma mark - What the safe area view asks and says

/// Whether the screen or component with this id waits to be laid out in its place before it renders: YES unless the
/// core marked the id as one that renders at once.
+ (BOOL)screenWaitsForReadiness:(NSString *)screenId;

/// The screen of this id has a safe area view: its content's readiness comes from it, never from its root's
/// children. The safe area view says it as its `screenId` arrives.
+ (void)screenHasSafeAreaView:(NSString *)screenId;

/// The safe area view of the screen is laid out in its place, with a size.
+ (void)screenDidLayOut:(NSString *)screenId;

/// The screen rendered into its safe area view, also when it rendered nothing (`null`).
+ (void)screenContentDidRender:(NSString *)screenId;

#pragma mark - What the core asks and marks

/// A root the core made that renders at once: the React root of a component of a bar (title, button, background),
/// of a tab item, of the tabs' accessory. Its content view marks it at its init and unmarks it at its stop. A screen
/// and the results component of a search are never marked.
+ (void)setRendersAtOnce:(BOOL)rendersAtOnce forScreenId:(NSString *)screenId;

/// Whether the safe area view of the id was laid out.
+ (BOOL)isLaidOut:(NSString *)screenId;

/// What the safe area view of the id said about its content.
+ (ANNContentRendered)contentRenderedOfScreen:(NSString *)screenId;

/// The one listener of the id (held weakly). A signal given already reaches it at once. An id marked "renders at once"
/// (a component of a bar, which several screens may show with one id) keeps every live listener instead: a new one
/// leaves the others theirs, gets what was said before only when it is the id's only listener, and every later signal
/// of the id reaches all of them, each deciding by its own mount whether the signal is its own.
+ (void)setListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId;

/// Removes `listener` if it is the one of the id (or one of an id that renders at once): a later screen with the same
/// id keeps its own.
+ (void)removeListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId;

/// Whether `listener` is the one of the id now (or one of them, for an id that renders at once).
+ (BOOL)isListener:(id<ANNScreenReadinessListener>)listener forScreenId:(NSString *)screenId;

/// How many live listeners the id has.
+ (NSUInteger)listenerCountForScreenId:(NSString *)screenId;

/// The screen of the id is destroyed and no other node has the id, a root that renders at once stops while it still
/// holds the id, or a new root of the id starts: what the safe area view of the id said goes.
+ (void)removeScreenId:(NSString *)screenId;

/// The JavaScript reloaded: everything goes before the new JavaScript renders. It counts its ids anew, and a sticky
/// "rendered" of an old screen would make the new screen with the same id ready at once.
+ (void)reset;

@end

NS_ASSUME_NONNULL_END
