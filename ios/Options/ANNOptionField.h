#import "ANNParam.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// What a field of a group of options holds.
typedef NS_ENUM(NSInteger, ANNOptionFieldKind) {
    /// An immutable value (`ANNParam` and its kinds), read by the field's parse block.
    ANNOptionFieldKindParam,
    /// A text kept as an `NSString` (a name a module reads as a string), one of the allowed values; nil when unset.
    ANNOptionFieldKindString,
    /// A group (`ANNOptions` subclass) under the key, never nil; a field without a key reads the group from the same
    /// object as its parent (the font of a text).
    ANNOptionFieldKindGroup,
    /// An array of groups of one class; nil when unset. It replaces as a whole and is copied item by item.
    ANNOptionFieldKindGroupArray,
    /// An array of strings (the allowed values only, when the field has them); nil when unset.
    ANNOptionFieldKindStringArray,
    /// An array of objects kept as JS sent them (a module reads them); nil when unset.
    ANNOptionFieldKindObjectArray,
};

/// Reads a value of `json` under `key`; never nil (no value is a param without one).
typedef ANNParam *_Nonnull (^ANNOptionParamParse)(NSDictionary *_Nullable json, NSString *key);

/// Whether a value of a union (`boolean | 'edge'`, `number | 'content'`) has one of the forms its type allows.
typedef BOOL (^ANNOptionValueCheck)(id value);

/// One field of a group of options: its JSON key, the property that holds it (the key, unless a module named it
/// otherwise) and its kind. A group declares its fields once (`+[ANNOptions optionFields]`) and gets its parse, merge,
/// copy and `hasValue` from `ANNOptions`. Immutable.
@interface ANNOptionField : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The JSON key; nil for a group read from its parent's object.
@property(nonatomic, copy, readonly, nullable) NSString *key;
@property(nonatomic, copy, readonly) NSString *property;
@property(nonatomic, readonly) ANNOptionFieldKind kind;
/// The class of a group, or of the items of an array of groups.
@property(nonatomic, readonly, nullable) Class groupClass;
/// The allowed values of a string or of the strings of an array; nil: any string.
@property(nonatomic, copy, readonly, nullable) NSSet<NSString *> *values;
@property(nonatomic, copy, readonly, nullable) ANNOptionParamParse parse;

#pragma mark - Values

/// `boolean`: `Bool`.
+ (instancetype)boolField:(NSString *)key;
/// `number` (a size, a width, an alpha, a radius, an index): `Double`.
+ (instancetype)doubleField:(NSString *)key;
/// A duration in milliseconds: `Double`, never negative.
+ (instancetype)durationField:(NSString *)key;
/// `number` a module reads as `NSNumber`: `Number`.
+ (instancetype)numberField:(NSString *)key;
/// `string`: `Text`; `null` is an explicit "no text".
+ (instancetype)textField:(NSString *)key;
/// A union of string literals: `Text` with one of `values`.
+ (instancetype)enumField:(NSString *)key values:(NSArray<NSString *> *)values;
/// `Color`: `Color`.
+ (instancetype)colorField:(NSString *)key;
/// `ImageResource`: `Image`, loaded as it is read.
+ (instancetype)imageField:(NSString *)key;
/// Another union: `Param` with the value as it came when `check` takes it.
+ (instancetype)paramField:(NSString *)key check:(ANNOptionValueCheck)check;
/// A value of its own kind, read by `parse` (the param it makes is the property's class).
+ (instancetype)paramField:(NSString *)key parse:(ANNOptionParamParse)parse;
/// A union of string literals a module reads as `NSString`; nil `values`: any string, which the module maps itself.
+ (instancetype)stringField:(NSString *)key values:(nullable NSArray<NSString *> *)values;

#pragma mark - Groups and arrays

/// An interface: a group of `groupClass` under the key.
+ (instancetype)groupField:(NSString *)key class:(Class)groupClass;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle` of a text: `ANNFontOptions` in the property `font`, read from
/// the text's own object.
+ (instancetype)fontField;
/// An array of interfaces: the items of `itemClass`; an item that is not an object is left out.
+ (instancetype)groupArrayField:(NSString *)key class:(Class)itemClass;
/// `string[]` or an array of literals: the strings (of `values`, when given); other items are left out.
+ (instancetype)stringArrayField:(NSString *)key values:(nullable NSArray<NSString *> *)values;
/// An array of objects a module reads as JSON: the objects as they came.
+ (instancetype)objectArrayField:(NSString *)key;

/// The same field held by another property (a module's name: `id` → `identifier`).
- (instancetype)withProperty:(NSString *)property;

@end

NS_ASSUME_NONNULL_END
