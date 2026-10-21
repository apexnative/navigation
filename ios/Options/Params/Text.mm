#import "Text.h"

@implementation Text

+ (Text *)withValue:(NSString *)value {
    return [[Text alloc] initWithValue:value];
}

- (NSString *)get {
    return [super get];
}

- (NSString *)withDefault:(NSString *)defaultValue {
    return [super withDefault:defaultValue];
}

@end
