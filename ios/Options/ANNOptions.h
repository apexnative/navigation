#import "ANNIntegerParam.h"
#import "ANNOptionField.h"
#import "Bool.h"
#import "BoolParser.h"
#import "Color.h"
#import "ColorParser.h"
#import "Double.h"
#import "DoubleParser.h"
#import "Image.h"
#import "ImageParser.h"
#import "Number.h"
#import "NumberParser.h"
#import "Param.h"
#import "Text.h"
#import "TextParser.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The group of options under `key`: nil when it is not an object (`null` included), which reads as an empty group.
NSDictionary *_Nullable ANNOptionsDictionary(NSDictionary *_Nullable dict, NSString *key);

#if DEBUG
/// Development only: checks the field table of `root` and of every group class reached through its group and
/// group-array fields.
void ANNCheckOptionClassesFrom(Class root);
#endif

/// A group of options read from the JSON JS sends. Reading is tolerant: a value of another type is no value, a group
/// that is not an object is empty, unknown keys are not read, and a value whose reading throws empties its group only.
/// Merging follows one rule: a group merges key by key at any depth, a value or an array replaces, an unset key leaves
/// what is there.
///
/// A group of the core declares its fields once in `+optionFields` and gets all of this from here: groups are never
/// nil (an empty group when missing), arrays are nil when missing. A module's group overrides `initWithDict:` (calling
/// `super`, which reads its empty table), `mergeOptions:` and `hasValue`.
@interface ANNOptions : NSObject <NSCopying>

/// The fields of the group; a subclass that adds fields to a group adds them to `[super optionFields]`.
+ (NSArray<ANNOptionField *> *)optionFields;

- (instancetype)initWithDict:(nullable NSDictionary *)dict;

/// An empty group.
- (instancetype)init;

/// Takes the values `options` has over these, in place: a value replaces, a group merges, an unset one leaves what is
/// here.
- (void)mergeOptions:(ANNOptions *)options;

/// The group has any value.
- (BOOL)hasValue;

/// A deep copy: a new group of the same class with these merged into it. The copy shares values, never groups.
- (instancetype)copy;

/// These over a copy of `defaults`; neither changes.
- (instancetype)withDefault:(nullable ANNOptions *)defaults;

@end

NS_ASSUME_NONNULL_END
