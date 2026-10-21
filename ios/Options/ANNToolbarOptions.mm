#import "ANNToolbarOptions.h"
#import "ANNButtonsParser.h"

@implementation ANNToolbarOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    self.visible = [BoolParser parse:dict key:@"visible"];
    self.buttons = [ANNButtonsParser parse:dict[@"buttons"]];
    self.testID = [TextParser parse:dict key:@"testID"];
    return self;
}

- (void)mergeOptions:(ANNToolbarOptions *)options {
    if (options.visible.hasValue)
        self.visible = options.visible;
    if (options.buttons)
        self.buttons = options.buttons;
    if (options.testID.hasValue)
        self.testID = options.testID;
}

- (BOOL)shouldShow {
    return [self.visible withDefault:YES] && self.buttons.count > 0;
}

@end
