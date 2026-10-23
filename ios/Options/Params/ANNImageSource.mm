#import "ANNImageSource.h"

// The path of a `file:` source the app may write again: a file outside the app's bundle (documents, caches, a temporary
// folder); nil for any other source. The bundle's files and assets don't change while the app runs.
static NSString *ANNWritablePathOf(ANNImageSourceKind kind, NSURL *url) {
    if (kind != ANNImageSourceKindFile || !url.isFileURL) {
        return nil;
    }
    NSString *path = url.path;
    NSString *bundle = NSBundle.mainBundle.bundlePath;
    if (!path.isAbsolutePath || [path isEqualToString:bundle] ||
        [path hasPrefix:[bundle stringByAppendingString:@"/"]]) {
        return nil;
    }
    return path;
}

// The modification date and size of the file at `path` (else of its `.png`, as the loader looks for it), or nil.
static NSString *ANNFileStampOf(NSString *path) {
    NSFileManager *files = NSFileManager.defaultManager;
    NSDictionary<NSFileAttributeKey, id> *attributes = [files attributesOfItemAtPath:path
                                                                                error:nil];
    if (!attributes && path.pathExtension.length == 0) {
        attributes = [files attributesOfItemAtPath:[path stringByAppendingPathExtension:@"png"]
                                             error:nil];
    }
    NSDate *modified = attributes.fileModificationDate;
    if (!modified) {
        return nil;
    }
    return [NSString stringWithFormat:@"%.6f|%llu", modified.timeIntervalSinceReferenceDate,
                                      attributes.fileSize];
}

@implementation ANNImageSource {
    // The key without the file's stamp, and the path whose stamp joins it.
    NSString *_baseKey;
    NSString *_writablePath;
}

+ (instancetype)symbolNamed:(NSString *)name fallback:(ANNImageSource *)fallback {
    ANNImageSource *source = [[self alloc] initWithKind:ANNImageSourceKindSymbol
                                                    url:nil
                                                address:name
                                                  scale:0
                                                  width:0
                                                 height:0];
    source->_symbolName = [name copy];
    source->_fallback = fallback;
    return source;
}

+ (instancetype)sourceOfKind:(ANNImageSourceKind)kind
                         url:(NSURL *)url
                     address:(NSString *)address
                       scale:(CGFloat)scale
                       width:(CGFloat)width
                      height:(CGFloat)height {
    return [[self alloc] initWithKind:kind
                                  url:url
                              address:address
                                scale:scale
                                width:width
                               height:height];
}

- (instancetype)initWithKind:(ANNImageSourceKind)kind
                         url:(NSURL *)url
                     address:(NSString *)address
                       scale:(CGFloat)scale
                       width:(CGFloat)width
                      height:(CGFloat)height {
    if (self = [super init]) {
        _kind = kind;
        _url = url;
        _address = [address copy];
        _scale = scale;
        _width = width;
        _height = height;
        _baseKey = [NSString stringWithFormat:@"%g|%g|%@", scale, width, _address];
        _writablePath = ANNWritablePathOf(kind, url);
    }
    return self;
}

// Read at each ask: a file the app writes again at the same path is another image.
- (NSString *)key {
    NSString *stamp = _writablePath ? ANNFileStampOf(_writablePath) : nil;
    return stamp ? [NSString stringWithFormat:@"%@|%@", _baseKey, stamp] : _baseKey;
}

- (NSString *)sourceKey {
    NSString *own = [NSString stringWithFormat:@"%ld|%@", (long)_kind, _baseKey];
    return _fallback ? [NSString stringWithFormat:@"%@|%@", own, _fallback.sourceKey] : own;
}

- (BOOL)loadsLater {
    return _kind == ANNImageSourceKindFile || _kind == ANNImageSourceKindData;
}

@end
