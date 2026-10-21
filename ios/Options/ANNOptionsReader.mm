#import "ANNOptionsReader.h"
#import "ANNAssert.h"
#import "ANNNavigationOptions.h"

// The parse runs on the main queue, one tree at a time: the path and the first failure are its state. A group read
// outside a parse (a module that reads its own JSON) finds an empty path and keeps no failure.
static NSMutableArray<NSString *> *ANNPathComponents;
static NSString *ANNFirstFailure;

@implementation ANNOptionsReader

+ (ANNNavigationOptions *)optionsFrom:(NSDictionary *)json unreadablePath:(NSString **)unreadablePath {
    ANNAssertMainQueue();
#if DEBUG
    static dispatch_once_t checkOnce;
    dispatch_once(&checkOnce, ^{
      ANNCheckOptionClassesFrom(ANNNavigationOptions.class);
    });
#endif
    NSMutableArray<NSString *> *outerPath = ANNPathComponents;
    NSString *outerFailure = ANNFirstFailure;
    ANNPathComponents = [NSMutableArray new];
    ANNFirstFailure = nil;
    ANNNavigationOptions *options = nil;
    @try {
        options = [[ANNNavigationOptions alloc] initWithDict:[json isKindOfClass:NSDictionary.class] ? json : nil];
    } @catch (NSException *exception) {
        [self failAtKey:nil];
        options = [[ANNNavigationOptions alloc] initWithDict:nil];
    }
    if (unreadablePath) {
        *unreadablePath = ANNFirstFailure;
    }
    ANNPathComponents = outerPath;
    ANNFirstFailure = outerFailure;
    return options;
}

+ (NSString *)pathOfKey:(NSString *)key {
    NSArray<NSString *> *components = ANNPathComponents ?: @[];
    if (key.length) {
        components = [components arrayByAddingObject:key];
    }
    return [components componentsJoinedByString:@"."];
}

+ (void)enter:(NSString *)component {
    [ANNPathComponents addObject:component];
}

+ (void)leave {
    if (ANNPathComponents.count) {
        [ANNPathComponents removeLastObject];
    }
}

+ (void)failAtKey:(NSString *)key {
    if (ANNPathComponents && !ANNFirstFailure) {
        ANNFirstFailure = [self pathOfKey:key];
    }
}

@end
