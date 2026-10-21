#import "ANNBottomTabsAccessoryOptions.h"

@implementation ANNBottomTabsAccessoryOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    self.component = [[ANNComponentOptions alloc] initWithDict:ANNOptionsDictionary(dict, @"component")];
    self.visible = [BoolParser parse:dict key:@"visible"];
    self.testID = [TextParser parse:dict key:@"testID"];
    return self;
}

- (void)mergeOptions:(ANNBottomTabsAccessoryOptions *)options {
    [self.component mergeOptions:options.component];
    if (options.visible.hasValue)
        self.visible = options.visible;
    if (options.testID.hasValue)
        self.testID = options.testID;
}

- (BOOL)hasValue {
    return self.component.hasValue || self.visible.hasValue || self.testID.hasValue;
}

@end
