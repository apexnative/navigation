#import "ANNImageSource.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The images of the options, every one of them (bar buttons and their menus, the back button, tab icons, search
/// tokens and suggestions): one cache for the app, by the source's key (`NSCache`, its cost the image's bytes, let go
/// at a memory warning). The images don't depend on the JavaScript, so a reload keeps them.
///
/// Every decode runs on one serial queue of the loader: `imageOf:` waits for it, `loadLater:done:` doesn't. A failure
/// (a missing file, data that isn't an image or doesn't decode, an exception while decoding) never throws: no image,
/// and one warning per address in the log. It is not cached, as on Android: it answers for a moment (1 s, the reads of
/// one apply), then the next read tries again, so a file written later or Metro's server back shows.
@interface ANNOptionImages : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The image of `source`: a symbol, what the cache has, or else loaded before this returns; nil on a failure. Any
/// thread.
+ (nullable UIImage *)imageOf:(ANNImageSource *)source;

/// The cache doesn't have the image of `source`, it didn't fail a moment ago, and it loads off the main queue
/// (`loadsLater` of the source): what `loadLater:done:` doesn't give at once. Any thread.
+ (BOOL)loadsLater:(ANNImageSource *)source;

/// Loads `source` on the loader's queue and gives `done` the image, or nil on a failure, on the main queue; one load
/// for every caller that asks for the same image meanwhile. What the cache has, or what doesn't load off the main
/// queue, comes at once, before this returns. Main queue.
+ (void)loadLater:(ANNImageSource *)source done:(void (^)(UIImage *_Nullable image))done;

/// Lets every image of the cache go. Any thread.
+ (void)clear;

@end

NS_ASSUME_NONNULL_END
