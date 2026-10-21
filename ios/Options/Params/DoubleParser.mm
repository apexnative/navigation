#import "DoubleParser.h"

@implementation DoubleParser

+ (Double *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    return [[Double alloc] initWithValue:ANNJSONIsNumber(value) ? value : nil];
}

+ (Double *)parseDuration:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    BOOL readable = ANNJSONIsNumber(value) && [value doubleValue] >= 0;
    return [[Double alloc] initWithValue:readable ? value : nil];
}

@end
