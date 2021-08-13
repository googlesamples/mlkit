# ML Kit Entity Extraction Quickstart

* [Read more about ML Kit Entity Extraction API](https://developers.google.com/ml-kit/language/entity-extraction)

## Introduction

The ML Kit Entity Extraction iOS Quickstart app demonstrates how to use the ML
Kit Entity Extraction feature to recognize structured data in text.

This app requires network connectivity in order to download the corresponding
language model.

## Getting Started

-   Run the sample on your iOS device or simulator.
-   Type input text and change languages to see entity extraction in action.

## How to use the app

-   Enter text in the input box and hit "done" on your keyboard.
-   If any entities are found in the text, they will show up on the bottom half
of the screen.
-   Click the "Models" button in the bottom right corner to ensure the correct
language model has been downloaded.

### Examples

<table>
<tr><th>Input text</th><th>Detected entities</th></tr>

<tr><td>You can contact the test team <b>tomorrow</b> at <b>info@google.com</b>
to determine the best timeline.</td>
<td>Entity 1 type: Date-Time<br>
Entity 1 text: = "2020-12-09 00:00:00"<br><br>

Entity 2 type: Email address<br>
Entity 2 text: info@google.com</td></tr>

<tr><td>Your order has shipped from Google. To follow the progress of your
delivery please use this tracking number: <b>9612804152073070474837</b></td>
<td>Entity type: Tracking number<br>
Entity text: "FedEx 9612804152073070474837"</td></tr>

<tr><td>Call the restaurant at <b>555-555-1234</b> to pay for dinner. My card
number is <b>4111 1111 1111 1111</b>.</td>
<td>Entity 1 type: Phone number<br>
Entity 1 text: "555-555-1234"<br><br>

Entity 2 type: Payment card<br>
Entity 2 text: "Visa 4111 1111 1111 1111"</td></tr>

</table>

## Support

-   [Stack Overflow](https://stackoverflow.com/questions/tagged/google-mlkit)

## License

Copyright 2020 Google LLC.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
