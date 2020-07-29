import Foundation

import MLKit

class RecognizedInk: NSObject {

  /** Ink, displayed to the user. */
  var ink: Ink
  /** Top recognition result candidate for the ink, displayed to the user. */
  var text: String? = nil

  /**
   * Creates a `RecognizedInk` with the given `Ink` once it is sent for recognition. Text is initially
   * empty.
   * @param ink The ink to be stored in the `RecognizedInk` object.
   * @return the initialized `RecognizedInk` object.
   */
  init(ink: Ink) {
    self.ink = ink
  }

}
