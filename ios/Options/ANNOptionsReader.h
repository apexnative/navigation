#import <Foundation/Foundation.h>

@class ANNNavigationOptions;

NS_ASSUME_NONNULL_BEGIN

/// The one entry from the JSON of options to the typed tree, and the place the parse keeps where it is: the dotted path
/// of the group it reads (`topBar.rightButtons[1]`), for the warnings of a value and for the first value that could not
/// be read. Main queue: images load while the options are read.
@interface ANNOptionsReader : NSObject

/// The options of `json` (an empty tree when it is not an object). `unreadablePath` gets the path of the first value
/// whose reading threw, nil when every value could be read; that value's group reads as empty, the rest as usual.
+ (ANNNavigationOptions *)optionsFrom:(nullable NSDictionary *)json
                       unreadablePath:(NSString *_Nullable *_Nullable)unreadablePath;

#pragma mark - The path of the parse, for the groups and parsers

/// The path of `key` in the group read now.
+ (NSString *)pathOfKey:(nullable NSString *)key;

/// The parse goes into the group under `component` (a key, or `key[i]` for an item of an array).
+ (void)enter:(NSString *)component;

/// The parse leaves the group it entered last.
+ (void)leave;

/// Reading `key` of the group read now threw (nil: the group itself); the first failure of a parse is kept.
+ (void)failAtKey:(nullable NSString *)key;

@end

NS_ASSUME_NONNULL_END
