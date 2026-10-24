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

/// Two values a group of options holds are the same: params and groups by `isSameValueAs:`, arrays item by item,
/// anything else as `ANNJSONValuesSame` says. nil is the same only as nil (an unset array is not an empty one).
BOOL ANNOptionValuesSame(id _Nullable a, id _Nullable b);

/// A hash of a value `ANNOptionValuesSame` compares: values that are the same have the same hash.
NSUInteger ANNOptionValueHash(id _Nullable value);

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

/// A group of the same class with the same options, field by field at any depth: two reads of the same JSON, a copy,
/// two resolutions of the same layers. The fields are the field table's; a module's group (an empty table) compares
/// every property of an options kind it declares. A value that is nil is the same only as nil.
///
/// Not `isEqual:`: a group changes in place (a merge, the pick of a menu's item), so a hash by value would move under
/// a set or a dictionary that holds it. `==` and `isEqual:` stay identity.
- (BOOL)isSameValueAs:(nullable ANNOptions *)other;

/// A hash consistent with `isSameValueAs:`; it changes when the group does.
- (NSUInteger)valueHash;

@end

NS_ASSUME_NONNULL_END
