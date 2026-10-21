#import "TextParser.h"

@implementation TextParser

+ (Text *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    BOOL readable = [value isKindOfClass:NSString.class] || value == NSNull.null;
    return [[Text alloc] initWithValue:readable ? value : nil];
}

+ (Text *)parse:(NSDictionary *)json key:(NSString *)key values:(NSSet<NSString *> *)values {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    BOOL readable = [value isKindOfClass:NSString.class] && [values containsObject:value];
    return [[Text alloc] initWithValue:readable ? value : nil];
}

@end
