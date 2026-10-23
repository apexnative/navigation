#import "ANNNavigationOptions.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Writes a choice into options being resolved: `options` is a copy the block may change, but its lists and their
/// items are shared with other trees, so the block puts in a new list with a copy of what it changes, never a change in
/// place. It finds what it changes by identity (a button by its id, a menu item by its id, a scope by its id) and
/// leaves the options as they are when that is not there.
typedef void (^ANNUserChoiceApply)(ANNNavigationOptions *options, id value);

/// The value of a choice after the app's `mergeOptions` into the node, by Android's rules (`UserChoices.yieldTo`):
/// `merge` is what the app sent, `own` the node's own options before it, `before` and `after` the node's resolution
/// with the defaults and without any user layer (the app's options alone) before and after the merge. The same `value`
/// keeps the choice, a smaller value keeps part of it, nil drops it. A part leaves when what it was made in is gone
/// from `after` (a button, a menu item, a scope), or when `merge` gives its field a value other than the one the app
/// had there: `own`'s, or `before`'s where `own` has no such field (a list the node takes from its stack or the
/// defaults). The same value sent again (a re-render) keeps it.
typedef id _Nullable (^ANNUserChoiceMerged)(id value, ANNNavigationOptions *merge,
                                             ANNNavigationOptions *own,
                                             ANNNavigationOptions *before,
                                             ANNNavigationOptions *after);

/// One choice the user made in native UI (a toggled button, the check of a single-selection menu, the search scope, the
/// tokens and the suggestions UIKit removed, a renamed title), kept in the node's user layer
/// (`ANNNodeHelper setUserChoice:forKey:`) apart from its own options. The node's resolution writes it over the own
/// options (`applyTo:`); the app's real change of the same field, or a merge after which what it is about is gone,
/// takes it away (`choiceAfterMerge:own:before:after:`). The blocks capture only plain data (a side, an id, a key),
/// never a presenter: the node keeps them as long as the choice.
@interface ANNUserChoice : NSObject

/// What the user chose, never changed: a number, a string, an array of ids or of option copies.
@property(nonatomic, strong, readonly) id value;

+ (instancetype)choiceWithValue:(id)value
                          apply:(ANNUserChoiceApply)apply
                         merged:(ANNUserChoiceMerged)merged;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The same choice with another value.
- (instancetype)choiceWithValue:(id)value;

/// The choice written into `options` (a copy being resolved).
- (void)applyTo:(ANNNavigationOptions *)options;

/// The choice after the app's `mergeOptions` (`ANNUserChoiceMerged`): itself, the choice with a smaller value, or nil
/// when it goes.
- (nullable ANNUserChoice *)choiceAfterMerge:(ANNNavigationOptions *)merge
                                         own:(ANNNavigationOptions *)own
                                      before:(ANNNavigationOptions *)before
                                       after:(ANNNavigationOptions *)after;

@end

NS_ASSUME_NONNULL_END
