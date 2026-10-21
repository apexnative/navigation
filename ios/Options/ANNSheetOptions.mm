#import "ANNSheetOptions.h"

@implementation ANNSheetOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    id detents = dict[@"detents"];
    self.detents = [detents isKindOfClass:NSArray.class] ? detents : nil;
    self.selectedDetent = [[Param alloc] initWithValue:dict[@"selectedDetent"]];
    self.largestUndimmedDetent = [[Param alloc] initWithValue:dict[@"largestUndimmedDetent"]];
    self.grabber = [BoolParser parse:dict key:@"grabber"];
    self.expandsOnScroll = [BoolParser parse:dict key:@"expandsOnScroll"];
    self.cornerRadius = [DoubleParser parse:dict key:@"cornerRadius"];
    self.testID = [TextParser parse:dict key:@"testID"];
    return self;
}

- (void)mergeOptions:(ANNSheetOptions *)options {
    if (options.detents)
        self.detents = options.detents;
    if (options.selectedDetent.hasValue)
        self.selectedDetent = options.selectedDetent;
    if (options.largestUndimmedDetent.hasValue)
        self.largestUndimmedDetent = options.largestUndimmedDetent;
    if (options.grabber.hasValue)
        self.grabber = options.grabber;
    if (options.expandsOnScroll.hasValue)
        self.expandsOnScroll = options.expandsOnScroll;
    if (options.cornerRadius.hasValue)
        self.cornerRadius = options.cornerRadius;
    if (options.testID.hasValue)
        self.testID = options.testID;
}

- (BOOL)hasValue {
    return self.detents != nil || self.selectedDetent.hasValue ||
           self.largestUndimmedDetent.hasValue || self.grabber.hasValue ||
           self.expandsOnScroll.hasValue || self.cornerRadius.hasValue || self.testID.hasValue;
}

@end
