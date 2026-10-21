#import "ANNPopoverOptions.h"

@implementation ANNPopoverOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    self.sourceButtonId = [TextParser parse:dict key:@"sourceButtonId"];
    self.sourceNativeID = [TextParser parse:dict key:@"sourceNativeID"];
    id directions = dict[@"arrowDirections"];
    self.arrowDirections = [directions isKindOfClass:NSArray.class] ? directions : nil;
    self.adaptive = [BoolParser parse:dict key:@"adaptive"];
    return self;
}

- (void)mergeOptions:(ANNPopoverOptions *)options {
    if (options.sourceButtonId.hasValue)
        self.sourceButtonId = options.sourceButtonId;
    if (options.sourceNativeID.hasValue)
        self.sourceNativeID = options.sourceNativeID;
    if (options.arrowDirections)
        self.arrowDirections = options.arrowDirections;
    if (options.adaptive.hasValue)
        self.adaptive = options.adaptive;
}

- (BOOL)hasValue {
    return self.sourceButtonId.hasValue || self.sourceNativeID.hasValue ||
           self.arrowDirections != nil || self.adaptive.hasValue;
}

@end
