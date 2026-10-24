#import "Image.h"
#import "ANNOptionImages.h"

@implementation Image

- (ANNImageSource *)source {
    id value = self.value;
    return [value isKindOfClass:ANNImageSource.class] ? value : nil;
}

- (UIImage *)get {
    id value = self.value;
    if ([value isKindOfClass:UIImage.class]) {
        return value;
    }
    ANNImageSource *source = self.source;
    return source ? [ANNOptionImages imageOf:source] : nil;
}

- (BOOL)isSameValueAs:(ANNParam *)other {
    if (other == self) {
        return YES;
    }
    if (other.class != self.class) {
        return NO;
    }
    ANNImageSource *source = self.source;
    ANNImageSource *otherSource = ((Image *)other).source;
    if (source || otherSource) {
        return [source.sourceKey isEqualToString:otherSource.sourceKey];
    }
    return self.value == other.value;
}

- (NSUInteger)valueHash {
    ANNImageSource *source = self.source;
    return source ? source.sourceKey.hash : (NSUInteger)(__bridge void *)self.value;
}

// A source that doesn't load is no image: the default then, as without a value.
- (UIImage *)withDefault:(UIImage *)defaultValue {
    UIImage *image = self.hasValue ? self.get : nil;
    return image ?: defaultValue;
}

@end
