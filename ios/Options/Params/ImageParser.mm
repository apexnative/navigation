#import "ImageParser.h"
#import "ANNOptionsReader.h"
#import <React/RCTConvert.h>
#import <React/RCTLog.h>

@implementation ImageParser

+ (Image *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    if (!value || value == NSNull.null) {
        return [[Image alloc] initWithValue:nil];
    }
    NSString *path = [ANNOptionsReader pathOfKey:key];
    return [[Image alloc] initWithValue:[self sourceOf:value path:path]];
}

+ (ANNImageSource *)sourceOf:(id)value path:(NSString *)path {
    if ([value isKindOfClass:NSArray.class]) {
        RCTLogError(@"[ApexNavigation] the image of '%@' is a list of sources, which options don't support: give "
                    @"one source",
                    path);
        return nil;
    }
    if ([value isKindOfClass:NSDictionary.class] && value[@"system"]) {
        return [self symbolOf:value path:path];
    }
    if (![value isKindOfClass:NSString.class] && ![value isKindOfClass:NSDictionary.class]) {
        return nil;
    }
    return [self addressOf:value path:path];
}

// An SF Symbol; a name the system doesn't have takes the fallback source, read the same way.
+ (ANNImageSource *)symbolOf:(NSDictionary *)value path:(NSString *)path {
    id name = value[@"system"];
    id fallback = value[@"fallback"];
    ANNImageSource *fallbackSource =
        fallback && fallback != NSNull.null ? [self sourceOf:fallback path:path] : nil;
    if (![name isKindOfClass:NSString.class] || [name length] == 0) {
        return fallbackSource;
    }
    return [ANNImageSource symbolNamed:name fallback:fallbackSource];
}

// A resolved source: its address and the fields its scale comes from.
+ (ANNImageSource *)addressOf:(id)source path:(NSString *)path {
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
    id packagerAsset = fields[@"__packager_asset"];
    BOOL metroAsset = ANNJSONIsBoolean(packagerAsset) && [packagerAsset boolValue];
    ANNImageSourceKind kind;
    if ([scheme isEqualToString:@"file"]) {
        kind = ANNImageSourceKindFile;
    } else if ([scheme isEqualToString:@"data"]) {
        kind = ANNImageSourceKindData;
    } else if (([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"]) && metroAsset) {
        kind = ANNImageSourceKindMetro;
    } else {
        RCTLogError(@"[ApexNavigation] the image of '%@' is not loaded: remote images are not supported in options "
                    @"(%@)",
                    path, address);
        return nil;
    }
    id scale = fields[@"scale"];
    id width = fields[@"width"];
    id height = fields[@"height"];
    return [ANNImageSource sourceOfKind:kind
                                    url:url
                                address:address
                                  scale:ANNJSONIsNumber(scale) ? [scale doubleValue] : 0
                                  width:ANNJSONIsNumber(width) ? [width doubleValue] : 0
                                 height:ANNJSONIsNumber(height) ? [height doubleValue] : 0];
}

@end
