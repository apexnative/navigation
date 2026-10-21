#import "ImageParser.h"
#import "ANNOptionsReader.h"
#import <React/RCTConvert.h>
#import <React/RCTLog.h>
#import <React/RCTUtils.h>

@implementation ImageParser

+ (Image *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    if (!value || value == NSNull.null) {
        return [[Image alloc] initWithValue:nil];
    }
    NSString *path = [ANNOptionsReader pathOfKey:key];
    return [[Image alloc] initWithValue:[self imageOf:value path:path]];
}

+ (UIImage *)imageOf:(id)value path:(NSString *)path {
    if ([value isKindOfClass:NSArray.class]) {
        RCTLogError(@"[ApexNavigation] the image of '%@' is a list of sources, which options don't support: give "
                    @"one source",
                    path);
        return nil;
    }
    if ([value isKindOfClass:NSDictionary.class] && value[@"system"]) {
        return [self systemImageOf:value path:path];
    }
    if (![value isKindOfClass:NSString.class] && ![value isKindOfClass:NSDictionary.class]) {
        return nil;
    }
    return [self imageOfSource:value path:path];
}

// An SF Symbol; a name the system doesn't have takes the fallback source, read the same way.
+ (UIImage *)systemImageOf:(NSDictionary *)value path:(NSString *)path {
    id name = value[@"system"];
    UIImage *image = [name isKindOfClass:NSString.class] ? [UIImage systemImageNamed:name] : nil;
    if (image) {
        return image;
    }
    id fallback = value[@"fallback"];
    return fallback && fallback != NSNull.null ? [self imageOf:fallback path:path] : nil;
}

// A resolved source, loaded synchronously as React Native's converter of images does, with the scale of the source.
+ (UIImage *)imageOfSource:(id)source path:(NSString *)path {
    NSDictionary *fields = [source isKindOfClass:NSDictionary.class] ? source : nil;
    id address = fields ? (fields[@"uri"] ?: fields[@"url"]) : source;
    if (![address isKindOfClass:NSString.class] || [address length] == 0) {
        RCTLogError(@"[ApexNavigation] the image of '%@' has no address", path);
        return nil;
    }
    id bundle = fields[@"bundle"];
    if ([bundle isKindOfClass:NSString.class]) {
        address = [NSString stringWithFormat:@"%@.bundle/%@", bundle, address];
    }
    NSURL *url = [RCTConvert NSURL:address];
    NSString *scheme = url.scheme.lowercaseString;
    BOOL metroAsset = ANNJSONIsBoolean(fields[@"__packager_asset"]) && [fields[@"__packager_asset"] boolValue];
    UIImage *image = nil;
    if ([scheme isEqualToString:@"file"]) {
        image = RCTImageFromLocalAssetURL(url) ?: RCTImageFromLocalBundleAssetURL(url);
    } else if ([scheme isEqualToString:@"data"]) {
        image = [UIImage imageWithData:[NSData dataWithContentsOfURL:url]];
    } else if (([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"]) && metroAsset) {
        // Metro's assets in development: loaded from the dev server, synchronously.
        image = [UIImage imageWithData:[NSData dataWithContentsOfURL:url]];
    } else {
        RCTLogError(@"[ApexNavigation] the image of '%@' is not loaded: remote images are not supported in options "
                    @"(%@)",
                    path, address);
        return nil;
    }
    if (!image) {
        RCTLogError(@"[ApexNavigation] the image of '%@' could not be loaded: %@", path, address);
        return nil;
    }
    return [self image:image scaledBy:fields];
}

// The scale of the source; without it the pixel width over `width`; without both, the file's own. A size that
// doesn't match is only logged.
+ (UIImage *)image:(UIImage *)image scaledBy:(NSDictionary *)fields {
    id scaleValue = fields[@"scale"];
    id widthValue = fields[@"width"];
    id heightValue = fields[@"height"];
    CGFloat scale = ANNJSONIsNumber(scaleValue) ? [scaleValue doubleValue] : 0;
    CGFloat width = ANNJSONIsNumber(widthValue) ? [widthValue doubleValue] : 0;
    CGFloat height = ANNJSONIsNumber(heightValue) ? [heightValue doubleValue] : 0;
    if (scale <= 0 && width > 0 && image.CGImage) {
        scale = CGImageGetWidth(image.CGImage) / width;
    }
    if (scale > 0 && image.CGImage && scale != image.scale) {
        image = [UIImage imageWithCGImage:image.CGImage scale:scale orientation:image.imageOrientation];
    }
    if (width > 0 && height > 0 && !CGSizeEqualToSize(image.size, CGSizeMake(width, height))) {
        RCTLogInfo(@"[ApexNavigation] an image of the options is %@, its source says %@",
                   NSStringFromCGSize(image.size), NSStringFromCGSize(CGSizeMake(width, height)));
    }
    return image;
}

@end
