#import "ANNReactView.h"

NS_ASSUME_NONNULL_BEGIN

/// How the core makes the views of screens and of the components of the options; a layout node gets it at creation.
@protocol ANNComponentViewCreator <NSObject>

/// The content of the screen or component `name` with the id `componentId`: a React view, or the slot screen view for
/// a name with `ANNSlotPrefix`.
- (UIView<ANNComponentProtocol> *)createComponentView:(NSString *)name
                                          componentId:(NSString *)componentId
                                        componentType:(ANNComponentType)componentType
                                  reactViewReadyBlock:
                                      (nullable ANNReactViewReadyCompletionBlock)reactViewReadyBlock;

@end

NS_ASSUME_NONNULL_END
