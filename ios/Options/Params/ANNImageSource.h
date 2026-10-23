#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Where an image of the options comes from.
typedef NS_ENUM(NSInteger, ANNImageSourceKind) {
    /// An SF Symbol (`{ system, fallback? }`); a name the system doesn't have takes the fallback.
    ANNImageSourceKindSymbol,
    /// A `file:` address: a file, or an asset of the app's bundle that React Native resolved.
    ANNImageSourceKindFile,
    /// A `data:` address.
    ANNImageSourceKindData,
    /// Metro's asset in development, read from the dev server.
    ANNImageSourceKindMetro,
};

/// An image the options name, as they name it: read when the options are read, loaded when an element shows it
/// (`ANNOptionImages`). Immutable; any thread.
@interface ANNImageSource : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// An SF Symbol, with the source a name the system doesn't have takes.
+ (instancetype)symbolNamed:(NSString *)name fallback:(nullable ANNImageSource *)fallback;

/// An address of `kind` (not a symbol) with the source's `scale`, `width` and `height` (0 without them).
+ (instancetype)sourceOfKind:(ANNImageSourceKind)kind
                         url:(NSURL *)url
                     address:(NSString *)address
                       scale:(CGFloat)scale
                       width:(CGFloat)width
                      height:(CGFloat)height;

@property(nonatomic, readonly) ANNImageSourceKind kind;
@property(nonatomic, copy, readonly, nullable) NSString *symbolName;
@property(nonatomic, strong, readonly, nullable) ANNImageSource *fallback;
@property(nonatomic, strong, readonly, nullable) NSURL *url;
/// The address as the options gave it (with its bundle); the symbol's name for a symbol.
@property(nonatomic, copy, readonly) NSString *address;
@property(nonatomic, readonly) CGFloat scale;
@property(nonatomic, readonly) CGFloat width;
@property(nonatomic, readonly) CGFloat height;

/// What the decoded image depends on: the address, the scale and the width (the scale without one), and for a `file:`
/// source outside the app's bundle the file's modification date and size, read at each ask, so a file the app writes
/// again at the same path shows its new content. The cache's key.
@property(nonatomic, copy, readonly) NSString *key;

/// What the options name, as text: the kind, the address, the scale and the width, and a symbol's fallback, without the
/// file's stamp, so what the file holds now never changes it. Reads nothing from the disk. For telling whether two
/// options name the same image (`key` is for the decoded one).
@property(nonatomic, copy, readonly) NSString *sourceKey;

/// The image reads from the device alone (a file, an asset of the app, a `data:` address), so it may load off the main
/// queue; Metro's asset reads from the network and a symbol is the system's.
@property(nonatomic, readonly) BOOL loadsLater;

@end

NS_ASSUME_NONNULL_END
