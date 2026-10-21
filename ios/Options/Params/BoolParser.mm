#import "BoolParser.h"

@implementation BoolParser

+ (Bool *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    return [[Bool alloc] initWithValue:ANNJSONIsBoolean(value) ? value : nil];
}

@end
