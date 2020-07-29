#ifndef RecognizedInk_h
#define RecognizedInk_h

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class MLKInk;

/**
 * Stores a piece of ink that has been sent to the recognizer, along with the recognition result
 * when it is returned. An array of these is used to represent the ink and results on the screen.
 */
@interface RecognizedInk : NSObject

/** Ink, displayed to the user. */
@property(nonatomic) MLKInk *ink;
/** Top recognition result candidate for the ink, displayed to the user. */
@property(copy, nullable, nonatomic) NSString *text;

/**
 * Creates a `RecognizedInk` with the given `Ink` once it is sent for recognition. Text is initially
 * empty.
 * @param ink The ink to be stored in the `RecognizedInk` object.
 * @return the initialized `RecognizedInk` object.
 */
- (nullable instancetype)initWithInk:(MLKInk *)ink NS_DESIGNATED_INITIALIZER;

- (nullable instancetype)init NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END

#endif /* RecognizedInk_h */
