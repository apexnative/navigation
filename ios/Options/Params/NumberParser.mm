#import "NumberParser.h"

@implementation NumberParser

+ (Number *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    return [[Number alloc] initWithValue:ANNJSONIsNumber(value) ? value : nil];
}

@end
