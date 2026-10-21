#import "Param.h"

@implementation Param

- (id)get {
    return self.isNull ? nil : self.value;
}

- (id)withDefault:(id)defaultValue {
    return self.hasValue ? self.get : defaultValue;
}

@end
