#import "ANNComponentType.h"
#import "ANNEventEmitter.h"
#import <Foundation/Foundation.h>

@protocol ANNComponentProtocol;

NS_ASSUME_NONNULL_BEGIN

/// Where an appearance of a screen or component stands, as JavaScript is told it.
typedef NS_ENUM(NSInteger, ANNAppearancePhase) {
    /// Not on screen: nothing was asked since the last `DidDisappear`, or never.
    ANNAppearancePhaseHidden,
    /// `WillAppear` was asked (sent or held).
    ANNAppearancePhaseAppearing,
    /// `DidAppear` was asked (sent or held).
    ANNAppearancePhaseShown,
};

/// The appearance state of one thing that reports `ANN.ComponentWillAppear`, `DidAppear` and `DidDisappear`: the
/// content of a React screen (the core's React view, the slot screen view), a React component of the options, the
/// results component of a search, or a native screen (its node keeps the record, with no content to wait for).
///
/// The record says what JavaScript was told and what waits to be told; `ANNAppearanceDispatcher` moves it and is the
/// only sender of its events. The events of an appearance wait until the content mounted, or at most the hold limit
/// (0.5 s), so that a screen that renders nothing still appears, and every `DidDisappear` follows an appearance
/// JavaScript heard. Main queue.
@interface ANNScreenAppearance : NSObject

/// `componentName` is sent as given: the name without the `slot:` prefix, or a native screen's registration name.
/// `content` is what the record waits for (weakly); nil waits for nothing. `eventEmitter` is the emitter of the
/// JavaScript the record reports to; nil takes the one of the JavaScript that runs now.
- (instancetype)initWithComponentId:(NSString *)componentId
                      componentName:(NSString *)componentName
                      componentType:(ANNComponentType)componentType
                            content:(nullable id<ANNComponentProtocol>)content
                       eventEmitter:(nullable ANNEventEmitter *)eventEmitter
    NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, copy, readonly) NSString *componentId;
@property(nonatomic, copy, readonly) NSString *componentName;
@property(nonatomic, readonly) ANNComponentType componentType;
@property(nonatomic, weak, readonly, nullable) id<ANNComponentProtocol> content;
@property(nonatomic, strong, readonly) ANNEventEmitter *eventEmitter;

@property(nonatomic, readonly) ANNAppearancePhase phase;

/// Whether the events of an appearance go at once: the content mounted (or there is none to wait for), `markReady`
/// was called, or the hold limit passed once. It stays so for the life of the record.
@property(nonatomic, readonly) BOOL isReady;

/// A readiness wait of a command ended (the content mounted, or the command's own limit passed): the content counts as
/// mounted from now on, and the events held go now, in order.
- (void)markReady;

@end

NS_ASSUME_NONNULL_END
