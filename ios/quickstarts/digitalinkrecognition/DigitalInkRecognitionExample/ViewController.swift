import UIKit

import MLKit

/// The `ViewController` manages the display seen by the user. The drawing canvas is actually two
/// overlapping image views. The top one contains the ink that the user is drawing before it is sent
/// to the recognizer. It can be thought of as a temporary buffer for ink in progress. When the user
/// presses the "Recognize" button, the ink is transferred to the other canvas, which displays a
/// grayed out version of the ink together with the recognition result.
///
/// The management of the interaction with the recognizer happens in `StrokeManager`.
/// `ViewController` just takes care of receiving user events, rendering the temporary ink, and
/// handles redraw requests from the `StrokeManager` when the ink is recognized. This latter request
/// comes through the `StrokeManagerDelegate` protocol.
///
/// The `ViewController` provides a number of buttons for controlling the `StrokeManager` which allow
/// for selecting the recognition language, downloading or deleting the recognition model, triggering
/// recognition, and clearing the ink.
@objc(ViewController)
class ViewController: UIViewController, StrokeManagerDelegate, UIPickerViewDelegate,
  UIPickerViewDataSource
{

  /** Constant defining how to render strokes. */
  private var kBrushWidth: CGFloat = 2.0

  /** All possible language tags supported by the digital ink recognition API. */
  private var allLanguageTags: [String] = []

  /** Mapping between the langugae tags and their display names. */
  private var languageTagDisplayNames: [String: String] = [:]

  /** Default language selected when demo app starts up. */
  private var defaultLanguage: String = ""

  /**
   * Object that takes care of the logic of saving the ink, sending ink to the recognizer after a
   * long enough pause, and storing the recognition results.
   */
  private var strokeManager: StrokeManager!

  /** Coordinates of the previous touch point as the user is drawing ink. */
  private var lastPoint: CGPoint!

  /** This view displays all the ink that has been sent for recognition, and recognition results. */
  @IBOutlet private var recognizedImage: UIImageView!

  /** This view shows only the ink that is currently being drawn, before sending for recognition. */
  @IBOutlet private var drawnImage: UIImageView!

  /**
   * Input field showing the currently selected language; when tapped brings up the `languagePicker`.
   */
  @IBOutlet private var selectedLanguageField: UITextField!

  /** Text region used to display status messages to the user about the results of their actions. */
  @IBOutlet private var messageLabel: UILabel!

  /** Clear button clears the canvases and also tells the `StrokeManager` to delete everything. */
  @IBAction func didPressClear() {
    recognizedImage.image = nil
    drawnImage.image = nil
    strokeManager!.clear()
    displayMessage(message: "")
  }

  /** Relays the download model command to the `StrokeManager`. */
  @IBAction func didPressDownload() {
    strokeManager!.downloadModel()
  }

  /** Relays the delete model command to the `StrokeManager`. */
  @IBAction func didPressDelete() {
    strokeManager!.deleteModel()
  }

  /** Relays the recognize ink command to the `StrokeManager`. */
  @IBAction func didPressRecognize() {
    strokeManager!.recognizeInk()
  }

  /** Initializes the view, in turn creating the StrokeManager and recognizer. */
  override func viewDidLoad() {
    super.viewDidLoad()
    // Create a `StrokeManager` to store the drawn ink. This also creates the recognizer object.
    strokeManager = StrokeManager.init(delegate: self)

    // Create the language picker which will be brought up when the user taps the selected language
    // field.
    let languagePicker = UIPickerView.init()
    languagePicker.delegate = self
    languagePicker.dataSource = self

    // Toolbar on top of the picker which will be used to finalize the selection.
    let toolbar = UIToolbar.init(
      frame: CGRect(x: 0, y: 0, width: self.view.frame.size.width, height: 44))
    let leftSpace = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
    let done = UIBarButtonItem.init(
      title: "Select Language",
      style: .done,
      target: selectedLanguageField,
      action: #selector(selectedLanguageField!.resignFirstResponder))
    let rightSpace = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
    toolbar.setItems([leftSpace, done, rightSpace], animated: false)

    // Associate the above two UI elements with the selected language input field.
    selectedLanguageField!.inputView = languagePicker
    selectedLanguageField!.inputAccessoryView = toolbar

    // Find the language most closely associated with the preferred language, falling back to English
    // if we can't find a match.
    let defaultLanguageIdentifier: DigitalInkRecognitionModelIdentifier =
      NSLocale.preferredLanguages.lazy.compactMap {
        try? DigitalInkRecognitionModelIdentifier.from(languageTag: $0)
      }.first ?? (try! DigitalInkRecognitionModelIdentifier.from(languageTag: "en"))
    defaultLanguage = defaultLanguageIdentifier.languageTag

    strokeManager!.selectLanguage(languageTag: defaultLanguageIdentifier.languageTag)

    // Initialize the language picker and scroll it to have the above language selected.
    computeAllLanguageTags()
    languagePicker.reloadAllComponents()
    languagePicker.selectRow(
      allLanguageTags.firstIndex(of: defaultLanguage)!,
      inComponent: 0,
      animated: false)

    // This has to happen after calling computeAllLanguageTags() which also sets up the
    // display names mapping.
    selectedLanguageField!.text = languageTagDisplayNames[defaultLanguage]
  }

  /** Handle start of stroke: Draw the point, and pass it along to the `StrokeManager`. */
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    let touch = touches.first
    // Since this is a new stroke, make last point the same as the current point.
    lastPoint = touch!.location(in: drawnImage)
    let time = touch!.timestamp
    drawLineSegment(touch: touch)
    strokeManager!.startStrokeAtPoint(point: lastPoint!, t: time)
  }

  /** Handle continuing a stroke: Draw the line segment, and pass along to the `StrokeManager`. */
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    let touch = touches.first
    drawLineSegment(touch: touch)
    strokeManager!.continueStrokeAtPoint(point: lastPoint!, t: touch!.timestamp)
  }

  /** Handle end of stroke: Draw the line segment, and pass along to the `StrokeManager`. */
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    let touch = touches.first
    drawLineSegment(touch: touch)
    strokeManager!.endStrokeAtPoint(point: lastPoint!, t: touch!.timestamp)
  }

  /** Displays a status message from the `StrokeManager` to the user. */
  func displayMessage(message: String) {
    messageLabel!.text = message
  }

  /**
   * Clear temporary ink in progress. This is invoked by the `StrokeManager` when the temporary ink is
   * sent to the recognizer.
   */
  func clearInk() {
    drawnImage.image = nil
  }

  /**
   * Iterate through all the saved ink/recognition results in the `StrokeManager` and render them.
   * This is invoked by the `StrokeManager` when an ink is sent to the recognizer, and when a
   * recognition result is returned.
   */
  func redraw() {
    recognizedImage.image = nil
    let recognizedInks = strokeManager!.recognizedInks
    for recognizedInk in recognizedInks {
      drawInk(ink: recognizedInk.ink)
      if recognizedInk.text != nil {
        drawText(recognizedInk: recognizedInk)
      }
    }
  }

  /**
   * Invoked by the language picker when the user scrolls to a particular position in either
   * component. In the left component, the language subtag is selected, so the right component needs
   * to be updated with corresponding list of full language codes and the first one is selected by
   * default; also the `StrokeManager` is informed.
   */
  func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
    let tag: String = allLanguageTags[row]
    selectedLanguageField.text = languageTagDisplayNames[tag]
    strokeManager!.selectLanguage(languageTag: tag)
  }

  /**
 * Invoked by the language picker to get the contents of each row. If the model for the language
 * is already downloaded, prepend the title with the string "[D]".
 */
  func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int)
    -> String?
  {
    let tag = allLanguageTags[row]
    let title = languageTagDisplayNames[tag]!
    if strokeManager.isLanguageDownloaded(languageTag: tag) {
      return "[D] \(title)"
    } else {
      return title
    }
  }

  /** Invoked by the language picker to find out how many entries are in each component. */
  func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: NSInteger)
    -> NSInteger
  {
    return allLanguageTags.count
  }

  /** Invoked by the language picker to get the number of components. */
  func numberOfComponents(in pickerView: UIPickerView) -> Int {
    return 1
  }

  /**
   * Draws a line segment from `self.lastPoint` to the current touch point given in the argument
   * to the temporary ink canvas.
   */
  func drawLineSegment(touch: UITouch!) {
    let currentPoint = touch.location(in: drawnImage)

    UIGraphicsBeginImageContext(drawnImage.frame.size)
    drawnImage.image?.draw(
      in: CGRect(
        x: 0, y: 0, width: drawnImage.frame.size.width, height: drawnImage.frame.size.height))
    let ctx: CGContext! = UIGraphicsGetCurrentContext()
    ctx.move(to: lastPoint!)
    ctx.addLine(to: currentPoint)
    ctx.setLineCap(CGLineCap.round)
    ctx.setLineWidth(kBrushWidth)
    // Unrecognized strokes are drawn in blue.
    ctx.setStrokeColor(red: 0, green: 0, blue: 1, alpha: 1)
    ctx.setBlendMode(CGBlendMode.normal)
    ctx.strokePath()
    ctx.flush()
    drawnImage.image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    lastPoint = currentPoint
  }

  /**
  * Goes through all language tags supported by the library, and attempt to create human readable names for
  * each of them (although the library supports more languages than iOS's NSLocale library knows about).
  * Ordered the languages alphabetically by their display names, but places the default language and the non-text
  * recognizers (emoji, autodraw, and shapes) at the top of the list.
  */
  func computeAllLanguageTags() {
    let all = DigitalInkRecognitionModelIdentifier.allModelIdentifiers()
    let locale: Locale = NSLocale.current
    var nonText: [String] = []
    var allTags: [String] = []
    for identifier in all {
      let tag = identifier.languageTag
      var displayName: String!
      if tag.hasPrefix("zxx-") {
        nonText.append(tag)
        displayName = tag.components(separatedBy: "-x-").last
      } else {
        displayName = locale.localizedString(forIdentifier: tag)
      }
      if displayName == nil {
        displayName = identifier.languageSubtag
        if identifier.regionSubtag != nil {
          displayName += " (\(identifier.regionSubtag!))"
        }
        if identifier.scriptSubtag != nil {
          displayName += " \(identifier.scriptSubtag!) Script"
        }
      }
      languageTagDisplayNames[tag] = displayName
      allTags.append(tag)
    }
    allLanguageTags = allTags.sorted(by: {
      var priorityA = 2
      if $0 == defaultLanguage {
        priorityA = 0
      } else if nonText.firstIndex(of: $0) != nil {
        priorityA = 1
      }
      var priorityB = 2
      if $1 == self.defaultLanguage {
        priorityB = 0
      } else if nonText.firstIndex(of: $1) != nil {
        priorityB = 1
      }
      if priorityA < priorityB {
        return true
      }
      if priorityA > priorityB {
        return false
      }
      return languageTagDisplayNames[$0]!.compare(
        languageTagDisplayNames[$1]!, options: NSString.CompareOptions.caseInsensitive)
        == ComparisonResult.orderedAscending
    })
  }

  /** Given an `Ink`, draw it into the `recognizedImage` canvas in gray. */
  func drawInk(ink: Ink) {
    UIGraphicsBeginImageContext(drawnImage.frame.size)
    recognizedImage.image?.draw(
      in: CGRect(
        x: 0, y: 0, width: drawnImage.frame.size.width, height: drawnImage.frame.size.height))
    let ctx: CGContext! = UIGraphicsGetCurrentContext()
    for stroke in ink.strokes {
      if stroke.points.isEmpty {
        continue
      }
      let point = CGPoint.init(x: Double(stroke.points[0].x), y: Double(stroke.points[0].y))
      ctx.move(to: point)
      ctx.addLine(to: point)
      for point in stroke.points {
        ctx.addLine(to: CGPoint.init(x: Double(point.x), y: Double(point.y)))
      }
    }
    ctx.setLineCap(CGLineCap.round)
    ctx.setLineWidth(kBrushWidth)
    // Recognized strokes are drawn in gray.
    ctx.setStrokeColor(red: 0.7, green: 0.7, blue: 0.7, alpha: 1)
    ctx.setBlendMode(CGBlendMode.normal)
    ctx.strokePath()
    ctx.flush()
    recognizedImage.image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
  }

  /** Given an `Ink`, returned the bounding box of the ink. */
  func getInkRect(ink: Ink) -> CGRect {
    var rect = CGRect.null
    if ink.strokes.count == 0 {
      return rect
    }
    for stroke in ink.strokes {
      for point in stroke.points {
        rect = rect.union(CGRect(x: Double(point.x), y: Double(point.y), width: 0, height: 0))
      }
    }
    // Make the minimum size 10x10 pixels.
    rect = rect.union(
      CGRect(
        x: rect.midX - 5,
        y: rect.midY - 5,
        width: 10,
        height: 10))
    return rect
  }

  /**
   * Given a `recognizedInk`, compute the bounding box of the ink that it contains, and render the
   * text at roughly the same size as the bounding box.
   */
  func drawText(recognizedInk: RecognizedInk) {
    let rect = getInkRect(ink: recognizedInk.ink)
    UIGraphicsBeginImageContext(drawnImage.frame.size)
    recognizedImage.image?.draw(
      in: CGRect(
        x: 0, y: 0, width: drawnImage.frame.size.width, height: drawnImage.frame.size.height))
    let ctx: CGContext! = UIGraphicsGetCurrentContext()
    ctx.setBlendMode(CGBlendMode.normal)

    let arbitrarySize: CGFloat = 20
    let font = UIFont.systemFont(ofSize: arbitrarySize)
    let attributes = [
      NSAttributedString.Key.font: font, NSAttributedString.Key.foregroundColor: UIColor.green,
    ]
    var size = recognizedInk.text!.size(withAttributes: attributes)
    if size.width <= 0 {
      size.width = 1
    }
    if size.height <= 0 {
      size.height = 1
    }
    ctx.translateBy(x: rect.origin.x, y: rect.origin.y)
    ctx.scaleBy(x: ceil(rect.size.width) / size.width, y: ceil(rect.size.height) / size.height)
    recognizedInk.text!.draw(at: CGPoint.init(x: 0, y: 0), withAttributes: attributes)
    ctx.flush()
    recognizedImage.image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
  }

}
