// Made by scripts/contract.mjs from src/interfaces/contract/commands.json: change that file and run the script again.

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Whether the navigation state goes before the answer of the command `name`: every command that may change what
/// shows. Any thread.
FOUNDATION_EXPORT BOOL ANNCommandReportsState(NSString *name);

/// Whether `ANN.CommandCompleted` follows the answer of the command `name`: every command that shows or dismisses.
/// Any thread.
FOUNDATION_EXPORT BOOL ANNCommandSendsCompleted(NSString *name);

NS_ASSUME_NONNULL_END
