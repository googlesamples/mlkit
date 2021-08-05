//
// Copyright (c) 2020 Google LLC.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import UIKit

import MLKitEntityExtraction

@objc(EntityViewController)
class EntityViewController: UIViewController, UITextViewDelegate, UIPickerViewDataSource,
  UIPickerViewDelegate
{
  @IBOutlet var inputTextView: UITextView!
  @IBOutlet var outputTextView: UITextView!
  @IBOutlet var languagePicker: UIPickerView!
  @IBOutlet var localePicker: UIPickerView!

  var modelForExtractor = EntityExtractionModelIdentifier.english
  var entityExtractor = EntityExtractor.entityExtractor(
    options: EntityExtractorOptions(modelIdentifier: EntityExtractionModelIdentifier.english))
  let colorPalette: [UIColor]! = EntityViewController.simplePalette()
  let languages = EntityExtractionModelIdentifier.allModelIdentifiersSorted()

  class func simplePalette() -> [UIColor]! {
    return [
      UIColor.blue.withAlphaComponent(0.25),
      UIColor.red.withAlphaComponent(0.25),
      UIColor.green.withAlphaComponent(0.25),
      UIColor.cyan.withAlphaComponent(0.25),
      UIColor.magenta.withAlphaComponent(0.25),
      UIColor.yellow.withAlphaComponent(0.25),
    ]
  }

  override func viewDidLoad() {
    inputTextView.delegate = self
    inputTextView.returnKeyType = .done
    languagePicker.delegate = self
    languagePicker.dataSource = self

    let languageRow = languages.firstIndex(of: EntityExtractionModelIdentifier.english)!
    languagePicker.selectRow(languageRow, inComponent: 0, animated: false)
    downloadModelAndAnnotate()
  }

  func downloadModelAndAnnotate() {
    let model = languages[languagePicker.selectedRow(inComponent: 0)]
    let locale = Locale.current  // Use system locale or a locale of your choice.
    if model != modelForExtractor {
      modelForExtractor = model
      let options = EntityExtractorOptions(modelIdentifier: model)
      entityExtractor = EntityExtractor.entityExtractor(options: options)
    }
    let extractor = entityExtractor
    let text = inputTextView.attributedText!
    extractor.downloadModelIfNeeded(completion: {
      [weak self]
      error in
      guard let self = self else { return }
      guard error == nil else {
        self.outputTextView.text = "Failed to download model with error \(error!)"
        return
      }
      self.annotateText(text: text, extractor: extractor, locale: locale)
    })
  }

  func textView(
    _ textView: UITextView, shouldChangeTextIn range: NSRange,
    replacementText text: String
  ) -> Bool {
    // Hide the keyboard when "Done" is pressed.
    // See: https://stackoverflow.com/questions/26600359/dismiss-keyboard-with-a-uitextview
    if text == "\n" {
      textView.resignFirstResponder()
      return false
    }
    return true
  }

  func textViewDidChange(_ textView: UITextView) {
    self.downloadModelAndAnnotate()
  }

  class func stringFromPaymentCardNetwork(_ network: PaymentCardNetwork) -> String {
    switch network {
    case PaymentCardNetwork.unknown:
      return "unknown"
    case PaymentCardNetwork.amex:
      return "Amex"
    case PaymentCardNetwork.dinersClub:
      return "DinersClub"
    case PaymentCardNetwork.discover:
      return "Discover"
    case PaymentCardNetwork.interPayment:
      return "InterPayment"
    case PaymentCardNetwork.JCB:
      return "JCB"
    case PaymentCardNetwork.maestro:
      return "Maestro"
    case PaymentCardNetwork.mastercard:
      return "Mastercard"
    case PaymentCardNetwork.mir:
      return "Mir"
    case PaymentCardNetwork.troy:
      return "Troy"
    case PaymentCardNetwork.unionpay:
      return "Unionpay"
    case PaymentCardNetwork.visa:
      return "Visa"
    }
  }

  class func stringFromGranularity(_ granularity: DateTimeGranularity) -> String {
    switch granularity {
    case DateTimeGranularity.year:
      return "year"
    case DateTimeGranularity.month:
      return "month"
    case DateTimeGranularity.week:
      return "week"
    case DateTimeGranularity.day:
      return "day"
    case DateTimeGranularity.hour:
      return "hour"
    case DateTimeGranularity.minute:
      return "minute"
    case DateTimeGranularity.second:
      return "second"
    case DateTimeGranularity.unknown:
      return "unknown"
    }
  }

  class func stringFromCarrier(_ carrier: ParcelTrackingCarrier) -> String {
    switch carrier {
    case ParcelTrackingCarrier.unknown:
      return "unknown"
    case ParcelTrackingCarrier.fedEx:
      return "FedEx"
    case ParcelTrackingCarrier.UPS:
      return "UPS"
    case ParcelTrackingCarrier.DHL:
      return "DHL"
    case ParcelTrackingCarrier.USPS:
      return "USPS"
    case ParcelTrackingCarrier.ontrac:
      return "Ontrac"
    case ParcelTrackingCarrier.lasership:
      return "Lasership"
    case ParcelTrackingCarrier.israelPost:
      return "IsraelPost"
    case ParcelTrackingCarrier.swissPost:
      return "SwissPost"
    case ParcelTrackingCarrier.MSC:
      return "MSC"
    case ParcelTrackingCarrier.amazon:
      return "Amazon"
    case ParcelTrackingCarrier.iParcel:
      return "IParcel"
    }
  }

  class func stringFromAnnotation(annotation: EntityAnnotation) -> String {
    var outputs: [String] = []
    for entity in annotation.entities {
      var output = ""
      if entity.entityType == EntityType.address {
        // Identifies a physical address.
        // No structured data available.
        output = "Address"
      } else if entity.entityType == EntityType.dateTime {
        // Identifies a date and time reference that may include a specific time. May be absolute
        // such as "01/01/2000 5:30pm" or relative like "tomorrow at 5:30pm".
        output = "Datetime: "
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        output.append(formatter.string(from: entity.dateTimeEntity!.dateTime))
        output.append(" (")
        output.append(
          EntityViewController.stringFromGranularity(entity.dateTimeEntity!.dateTimeGranularity))
      } else if entity.entityType == EntityType.email {
        // Identifies an e-mail address.
        // No structured data available.
        output = "E-mail"
      } else if entity.entityType == EntityType.flightNumber {
        // Identifies a flight number in IATA format.
        output = "Flight number: "
        output.append(entity.flightNumberEntity!.airlineCode)
        output.append(" ")
        output.append(entity.flightNumberEntity!.flightNumber)
      } else if entity.entityType == EntityType.IBAN {
        // Identifies an International Bank Account Number (IBAN).
        output = "IBAN: "
        output.append(entity.ibanEntity!.countryCode)
        output.append(" ")
        output.append(entity.ibanEntity!.iban)
      } else if entity.entityType == EntityType.ISBN {
        // Identifies an International Standard Book Number (ISBN).
        output = "ISBN: "
        output.append(entity.isbnEntity!.isbn)
      } else if entity.entityType == EntityType.paymentCard {
        // Identifies a payment card.
        output = "Payment card: "
        output.append(
          EntityViewController.stringFromPaymentCardNetwork(
            entity.paymentCardEntity!.paymentCardNetwork))
        output.append(" ")
        output.append(entity.paymentCardEntity!.paymentCardNumber)
      } else if entity.entityType == EntityType.phone {
        // Identifies a phone number.
        // No structured data available.
        output = "Phone number"
      } else if entity.entityType == EntityType.trackingNumber {
        // Identifies a shipment tracking number.
        output = "Tracking number: "
        output.append(
          EntityViewController.stringFromCarrier(entity.trackingNumberEntity!.parcelCarrier))
        output.append(" ")
        output.append(entity.trackingNumberEntity!.parcelTrackingNumber)
      } else if entity.entityType == EntityType.URL {
        // Identifies a URL.
        // No structured data available.
        output = "URL"
      } else if entity.entityType == EntityType.money {
        // Identifies currencies.
        output = "Money: "
        output.append(entity.moneyEntity!.description)
      }
      outputs.append(output)
    }
    return "[" + outputs.joined(separator: ", ") + "]\n"
  }

  func annotateText(text: NSAttributedString!, extractor: EntityExtractor!, locale: Locale!) {
    extractor.annotateText(
      text.string,
      completion: {
        [weak self]
        result, error in

        guard let self = self else { return }
        let outputAttributes = [NSAttributedString.Key.font: self.outputTextView.font as Any]
        let output = NSMutableAttributedString()
        let input = text.mutableCopy() as! NSMutableAttributedString
        input.removeAttribute(
          NSAttributedString.Key.backgroundColor, range: NSMakeRange(0, input.string.count))
        if error != nil {
          output.append(
            NSMutableAttributedString.init(
              string: "Entity Extractor failed with error \(error!)", attributes: outputAttributes))
        }
        guard let result = result else {
          print("Result is nil.")
          return
        }
        if result.count == 0 {
          output.append(
            NSMutableAttributedString.init(
              string: "No results returned.", attributes: outputAttributes))
        } else {
          var i = 0
          for annotation in result {
            let color = self.colorPalette[i % self.colorPalette.count]
            i += 1
            input.addAttributes(
              [NSAttributedString.Key.backgroundColor: color], range: annotation.range)
            let annotationString = EntityViewController.stringFromAnnotation(annotation: annotation)
            let annotationAttributesString = NSMutableAttributedString.init(
              string: annotationString, attributes: outputAttributes)
            annotationAttributesString.addAttributes(
              [NSAttributedString.Key.backgroundColor: color],
              range: NSMakeRange(0, annotationString.count))
            output.append(annotationAttributesString)
          }
        }
        self.outputTextView.attributedText = output
        self.inputTextView.attributedText = input
      })
  }

  func numberOfComponents(in pickerView: UIPickerView) -> Int {
    return 1
  }

  func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
    return languages.count
  }

  func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int)
    -> String?
  {
    let code = languages[row].toLanguageTag()
    return Locale.current.localizedString(forLanguageCode: code)
  }

  func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
    downloadModelAndAnnotate()
  }
}
