#import "ANNLayoutType.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// One node of a command's layout, as `ANNLayoutReader` read and checked it on the module's queue. Immutable: made off
/// the main queue, read on it by the command. The data stays the JSON JS sent; the accessors give its fields with the
/// kind the contract has, or nothing.
@interface ANNLayoutNode : NSObject

/// Made by `ANNLayoutReader` only: the id as JS gave it, the type, the node's `data` (empty when missing) and its
/// children (empty when missing, and always for a screen, whose children are not read).
- (instancetype)initWithNodeId:(NSString *)nodeId
                          type:(ANNLayoutType)type
                          data:(NSDictionary<NSString *, id> *)data
                      children:(NSArray<ANNLayoutNode *> *)children NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, copy, readonly) NSString *nodeId;
@property(nonatomic, readonly) ANNLayoutType type;
/// The name of the type, as in the JSON.
@property(nonatomic, readonly) NSString *typeName;
@property(nonatomic, copy, readonly) NSDictionary<NSString *, id> *data;
@property(nonatomic, copy, readonly) NSArray<ANNLayoutNode *> *children;

#pragma mark - Fields of data

/// `data.name`: the screen's name (`slot:<Name>` for a screen of the one React tree), the name of a native screen,
/// or the name of a pair.
@property(nonatomic, readonly, nullable) NSString *name;
/// `data.options`, empty when missing. Parsed on the main queue.
@property(nonatomic, readonly) NSDictionary<NSString *, id> *options;
/// `data.key`, the key of a screen (`<Name>:<key>`).
@property(nonatomic, readonly, nullable) NSString *key;
/// `data.passProps` of a native screen; nil for any other node (the props of a React screen stay in JS).
@property(nonatomic, readonly, nullable) NSDictionary<NSString *, id> *passProps;
/// `data.functions` of a native screen: the names of the functions among its props; empty for any other node.
@property(nonatomic, readonly) NSArray<NSString *> *functions;
/// `data.columns` of a split view: the column name of each child, in order; an entry that is no string is `''`.
@property(nonatomic, readonly) NSArray<NSString *> *columns;
/// The children of a split view by their column (`columnNamesOfData:childCount:`). Empty for any other node.
@property(nonatomic, readonly) NSDictionary<NSString *, ANNLayoutNode *> *columnChildren;

/// The column of each child of a split view, `''` for a child that is no column: `columns[i]` names child i, a name
/// the contract doesn't have and a name given before don't count; without `columns` (missing, no array, or empty)
/// the first two children are `primary` and `secondary`.
+ (NSArray<NSString *> *)columnNamesOfData:(NSDictionary<NSString *, id> *)data
                                childCount:(NSUInteger)childCount;

#pragma mark - Kinds

/// A screen: `Component` or `ExternalComponent`.
@property(nonatomic, readonly) BOOL isScreen;
/// A side of a side menu: `SideMenuLeft`, `SideMenuCenter` or `SideMenuRight`.
@property(nonatomic, readonly) BOOL isSideMenuSide;

/// This node and every node under it, depth first, each before its children.
- (NSArray<ANNLayoutNode *> *)allNodes;

@end

NS_ASSUME_NONNULL_END
