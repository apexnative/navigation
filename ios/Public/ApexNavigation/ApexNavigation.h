#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@class RCTHost;

NS_ASSUME_NONNULL_BEGIN

/// What a native screen gets when JS opens it, for as long as it is open.
NS_SWIFT_NAME(NativeScreenContext)
@interface ANNNativeScreenContext : NSObject

/// The screen's layout id: `externalComponent.id`, or the one JS generated.
@property(nonatomic, copy, readonly) NSString *componentId;

/// The props: those of the command that showed the screen, with what its `useProps` returns over them. Plain data;
/// they change while the screen is open, and `onPropsChange` tells when.
@property(nonatomic, copy, readonly) NSDictionary<NSString *, id> *props;

/// The names of the functions in the props, which `call(_:arguments:)` runs in JS.
@property(nonatomic, copy, readonly) NSSet<NSString *> *functions;

/// Called on the main thread with the new props, each time they change.
@property(nonatomic, copy, nullable) void (^onPropsChange)(NSDictionary<NSString *, id> *props);

/**
 * Runs a function of the props in JS. `completion` gets, on the main thread, what it returned (for a promise, what it
 * resolved with) as plain data, or the error: a function the props don't have, one that threw, a screen gone.
 *
 * ```swift
 * let saved = try await context.call("onSave", arguments: ["Ann"])
 * ```
 */
- (void)call:(NSString *)function
     arguments:(NSArray *)arguments
    completion:(nullable void (^)(id _Nullable_result value, NSError *_Nullable error))completion
    NS_SWIFT_NAME(call(_:arguments:completion:));

/**
 * Runs a command of the navigation from this screen in JS, as `useNavigation()` of a React screen does: push, pop,
 * popTo, popToRoot, showInColumn, showModal, dismiss, showOverlay, setOptions, selectTab, setTabBadge.
 * `arguments` are those of the JS command; `completion` gets what it resolved with.
 *
 * ```swift
 * try await context.navigate("push", arguments: ["Details", ["props": ["id": 1]]])
 * ```
 */
- (void)navigate:(NSString *)command
       arguments:(NSArray *)arguments
      completion:(nullable void (^)(id _Nullable_result value, NSError *_Nullable error))completion
    NS_SWIFT_NAME(navigate(_:arguments:completion:));

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

/// Makes the view controller of a native screen: a UIKit one, or a SwiftUI view in a `UIHostingController`.
typedef UIViewController *_Nonnull (^ANNNativeScreenCreator)(ANNNativeScreenContext *context)
    NS_SWIFT_NAME(NativeScreenCreator);

@interface ApexNavigation : NSObject

+ (void)bootstrapWithHost:(RCTHost *)host;

/**
 * Registers a native screen that JS opens with `externalComponent: { name, passProps }`. Call it any time before
 * JS opens the screen, for example in `application(_:didFinishLaunchingWithOptions:)`.
 *
 * ```swift
 * ApexNavigation.registerNativeScreen("Profile") { context in
 *   UIHostingController(rootView: ProfileView(props: context.props))
 * }
 * ```
 */
+ (void)registerNativeScreen:(NSString *)name
                     creator:(ANNNativeScreenCreator)creator NS_SWIFT_NAME(registerNativeScreen(_:creator:));

+ (nullable RCTHost *)getHost;

+ (nullable UIViewController *)findViewController:(NSString *)componentId;

/**
 * Moves the navigation window into `windowScene` and shows it. `ANNSceneDelegate` does this when the app's
 * scene connects; call it from your own `UIWindowSceneDelegate` if the app has one, in
 * `scene(_:willConnectTo:options:)`. Forward the connection options' `urlContexts` and `userActivities` to the app
 * delegate in that same method: the link that launched the app is the latest URL dispatched by the time it returns.
 */
+ (UIWindow *)attachToWindowScene:(UIWindowScene *)windowScene;

@end

NS_ASSUME_NONNULL_END
