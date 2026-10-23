#import "ANNOptionImages.h"
#import <React/RCTAssert.h>
#import <React/RCTLog.h>
#import <React/RCTUtils.h>

// The options' icons are small: 16 MB of decoded pixels holds hundreds of them.
static const NSUInteger ANNOptionImagesCostLimit = 16 * 1024 * 1024;

// The longest piece of an address a warning names (a `data:` address is the whole image).
static const NSUInteger ANNOptionImagesLoggedAddressLength = 80;

// How long a failure answers for its source without a new try: the many reads of one apply ask once, a later apply
// tries again (a file written since, Metro's server back).
static const NSTimeInterval ANNOptionImagesFailureMoment = 1;

static const void *ANNOptionImagesQueueKey = &ANNOptionImagesQueueKey;

static NSCache<NSString *, UIImage *> *ANNCache;
static dispatch_queue_t ANNQueue;
// The addresses already named in a warning: one each.
static NSMutableSet<NSString *> *ANNWarned;
// The keys whose last try failed, with the system's uptime then; guarded by itself. Failures never go into the cache.
static NSMutableDictionary<NSString *, NSNumber *> *ANNFailures;
// The loads off the main queue under way, with who waits for each. Main queue.
static NSMutableDictionary<NSString *, NSMutableArray *> *ANNLoading;

static void ANNSetUp(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      ANNCache = [NSCache new];
      ANNCache.name = @"ApexNavigation option images";
      ANNCache.totalCostLimit = ANNOptionImagesCostLimit;
      dispatch_queue_attr_t attributes = dispatch_queue_attr_make_with_qos_class(
          DISPATCH_QUEUE_SERIAL, QOS_CLASS_USER_INITIATED, 0);
      ANNQueue = dispatch_queue_create("dev.apexnative.navigation.images", attributes);
      dispatch_queue_set_specific(ANNQueue, ANNOptionImagesQueueKey,
                                  (void *)ANNOptionImagesQueueKey, NULL);
      ANNWarned = [NSMutableSet new];
      ANNFailures = [NSMutableDictionary new];
      ANNLoading = [NSMutableDictionary new];
      NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
      [center addObserverForName:UIApplicationDidReceiveMemoryWarningNotification
                          object:nil
                           queue:nil
                      usingBlock:^(NSNotification *notification) {
                        [ANNCache removeAllObjects];
                      }];
    });
}

// The bytes of the decoded image, the cost the cache weighs it with.
static NSUInteger ANNBytesOf(UIImage *image) {
    CGImageRef cgImage = image.CGImage;
    if (cgImage) {
        return CGImageGetBytesPerRow(cgImage) * CGImageGetHeight(cgImage);
    }
    CGFloat width = image.size.width * image.scale;
    CGFloat height = image.size.height * image.scale;
    return (NSUInteger)MAX(1, width * height * 4);
}

static void ANNWarnOnce(ANNImageSource *source, NSString *why) {
    @synchronized(ANNWarned) {
        if ([ANNWarned containsObject:source.address]) {
            return;
        }
        [ANNWarned addObject:source.address];
    }
    NSString *address = source.address;
    if (address.length > ANNOptionImagesLoggedAddressLength) {
        address = [[address substringToIndex:ANNOptionImagesLoggedAddressLength]
            stringByAppendingString:@"…"];
    }
    RCTLogWarn(@"[ApexNavigation] could not load the image %@: %@", address, why);
}

// The last try of `key` failed a moment ago.
static BOOL ANNFailedJustNow(NSString *key) {
    @synchronized(ANNFailures) {
        NSNumber *at = ANNFailures[key];
        if (!at) {
            return NO;
        }
        NSTimeInterval since = NSProcessInfo.processInfo.systemUptime - at.doubleValue;
        if (since < ANNOptionImagesFailureMoment) {
            return YES;
        }
        [ANNFailures removeObjectForKey:key];
        return NO;
    }
}

static void ANNSetFailed(NSString *key, BOOL failed) {
    @synchronized(ANNFailures) {
        if (failed) {
            ANNFailures[key] = @(NSProcessInfo.processInfo.systemUptime);
        } else {
            [ANNFailures removeObjectForKey:key];
        }
    }
}

// A file at the address: an image with nothing but its pixels, which can be decoded ahead. An asset of a catalog
// (found by name) may have a dark variant, which a decoded copy would lose.
static BOOL ANNIsFileAt(NSURL *url) {
    NSString *path = url.path;
    if (path.length == 0) {
        return NO;
    }
    NSFileManager *files = NSFileManager.defaultManager;
    if ([files fileExistsAtPath:path]) {
        return YES;
    }
    return path.pathExtension.length == 0 &&
           [files fileExistsAtPath:[path stringByAppendingPathExtension:@"png"]];
}

@implementation ANNOptionImages

+ (UIImage *)imageOf:(ANNImageSource *)source {
    if (source.kind == ANNImageSourceKindSymbol) {
        UIImage *symbol = [UIImage systemImageNamed:source.symbolName];
        return symbol ?: (source.fallback ? [self imageOf:source.fallback] : nil);
    }
    ANNSetUp();
    NSString *key = source.key;
    UIImage *cached = [ANNCache objectForKey:key];
    if (cached || ANNFailedJustNow(key)) {
        return cached;
    }
    if (dispatch_get_specific(ANNOptionImagesQueueKey)) {
        return [self loadOnQueue:source];
    }
    __block UIImage *image = nil;
    dispatch_sync(ANNQueue, ^{
      image = [self loadOnQueue:source];
    });
    return image;
}

// A failure of a moment ago answers at once, so an item built again when its load failed doesn't load again.
+ (BOOL)loadsLater:(ANNImageSource *)source {
    if (!source.loadsLater) {
        return NO;
    }
    ANNSetUp();
    NSString *key = source.key;
    return [ANNCache objectForKey:key] == nil && !ANNFailedJustNow(key);
}

+ (void)loadLater:(ANNImageSource *)source done:(void (^)(UIImage *))done {
    RCTAssertMainQueue();
    if (![self loadsLater:source]) {
        done([self imageOf:source]);
        return;
    }
    NSString *key = source.key;
    NSMutableArray *waiters = ANNLoading[key];
    if (waiters) {
        [waiters addObject:[done copy]];
        return;
    }
    ANNLoading[key] = [NSMutableArray arrayWithObject:[done copy]];
    dispatch_async(ANNQueue, ^{
      UIImage *image = [self loadOnQueue:source];
      dispatch_async(dispatch_get_main_queue(), ^{
        NSArray *waiting = ANNLoading[key];
        [ANNLoading removeObjectForKey:key];
        for (void (^waiter)(UIImage *) in waiting) {
            waiter(image);
        }
      });
    });
}

+ (void)clear {
    ANNSetUp();
    [ANNCache removeAllObjects];
    @synchronized(ANNFailures) {
        [ANNFailures removeAllObjects];
    }
}

#pragma mark - The loader's queue

// The cache again (a load that came first), else the decode. An image goes into the cache; a failure doesn't, as on
// Android: it answers only for a moment (`ANNFailedJustNow`), and the next apply after that tries again.
+ (UIImage *)loadOnQueue:(ANNImageSource *)source {
    NSString *key = source.key;
    UIImage *cached = [ANNCache objectForKey:key];
    if (cached || ANNFailedJustNow(key)) {
        return cached;
    }
    NSString *failure = nil;
    UIImage *image = nil;
    @try {
        image = [self decode:source failure:&failure];
    } @catch (NSException *exception) {
        image = nil;
        failure = [NSString
            stringWithFormat:@"decoding it threw %@ (%@)", exception.name, exception.reason];
    }
    if (!image) {
        ANNSetFailed(key, YES);
        ANNWarnOnce(source, failure ?: @"it can't be decoded");
        return nil;
    }
    ANNSetFailed(key, NO);
    [ANNCache setObject:image forKey:key cost:ANNBytesOf(image)];
    return image;
}

// The image of an address, as React Native's converter of images reads it, with the scale of the source; nil with
// what failed. A plain file and data are decoded here, so the first frame that shows them doesn't.
+ (UIImage *)decode:(ANNImageSource *)source failure:(NSString **)failure {
    NSURL *url = source.url;
    UIImage *image = nil;
    BOOL decodesAhead = YES;
    switch (source.kind) {
    case ANNImageSourceKindFile:
        image = RCTImageFromLocalAssetURL(url) ?: RCTImageFromLocalBundleAssetURL(url);
        if (!image) {
            *failure = @"there is no image at that address";
            return nil;
        }
        decodesAhead = ANNIsFileAt(url);
        break;
    case ANNImageSourceKindData:
    case ANNImageSourceKindMetro: {
        // Metro's assets in development: read from the dev server, as before the cache.
        NSError *error = nil;
        NSData *data = url ? [NSData dataWithContentsOfURL:url options:0 error:&error] : nil;
        if (data.length == 0) {
            *failure = error.localizedDescription ?: @"it has no data";
            return nil;
        }
        image = [UIImage imageWithData:data];
        if (!image) {
            *failure = @"its data is not an image";
            return nil;
        }
        break;
    }
    case ANNImageSourceKindSymbol:
        return nil;
    }
    if (decodesAhead) {
        UIImage *decoded = [image imageByPreparingForDisplay];
        if (!decoded) {
            *failure = @"its data can't be decoded";
            return nil;
        }
        image = decoded.CGImage ? [UIImage imageWithCGImage:decoded.CGImage
                                                      scale:image.scale
                                                orientation:image.imageOrientation]
                                : decoded;
    }
    if (!(image.size.width > 0) || !(image.size.height > 0)) {
        *failure = @"it is empty";
        return nil;
    }
    return [self image:image scaledBy:source];
}

// The scale of the source; without it the pixel width over `width`; without both, the file's own. A size that
// doesn't match is only logged.
+ (UIImage *)image:(UIImage *)image scaledBy:(ANNImageSource *)source {
    CGFloat scale = source.scale;
    CGFloat width = source.width;
    CGFloat height = source.height;
    if (scale <= 0 && width > 0 && image.CGImage) {
        scale = CGImageGetWidth(image.CGImage) / width;
    }
    if (scale > 0 && image.CGImage && scale != image.scale) {
        image = [UIImage imageWithCGImage:image.CGImage
                                    scale:scale
                              orientation:image.imageOrientation];
    }
    if (width > 0 && height > 0 && !CGSizeEqualToSize(image.size, CGSizeMake(width, height))) {
        RCTLogInfo(@"[ApexNavigation] an image of the options is %@, its source says %@",
                   NSStringFromCGSize(image.size), NSStringFromCGSize(CGSizeMake(width, height)));
    }
    return image;
}

@end
