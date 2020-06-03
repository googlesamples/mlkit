//
//  Copyright (c) 2019 Google Inc.
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

import MLKitCommon
import MLKitImageClassificationAutoML

class ModelManagementSnippets {
    func setupRemoteModel() {
        // [START setup_remote_model]
        let downloadConditions = ModelDownloadConditions(allowsCellularAccess: true,
                                                         allowsBackgroundDownloading: true)
        // Instantiate a concrete subclass of RemoteModel.
        let remoteModel = AutoMLRemoteModel(name: "your_remote_model" // The name you assigned in the console.
        )
        // [END setup_remote_model]

        // [START start_download]
        let downloadProgress = ModelManager.modelManager().download(remoteModel, conditions: downloadConditions)

        // ...

        if downloadProgress.isFinished {
            // The model is available on the device
        }
        // [END start_download]
    }

    func setupModelDownloadNotifications() {
        // [START setup_notifications]
        NotificationCenter.default.addObserver(
            forName: .mlkitModelDownloadDidSucceed,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let strongSelf = self,
                let userInfo = notification.userInfo,
                let model = userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue]
                    as? RemoteModel,
                model.name == "your_remote_model"
                else { return }
            // The model was downloaded and is available on the device
        }

        NotificationCenter.default.addObserver(
            forName: .mlkitModelDownloadDidFail,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let strongSelf = self,
                let userInfo = notification.userInfo,
                let model = userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue]
                    as? RemoteModel
                else { return }
            let error = userInfo[ModelDownloadUserInfoKey.error.rawValue]
            // ...
        }
        // [END setup_notifications]
    }

    func setupLocalModel() {
        // [START setup_local_model]
        guard let manifestPath = Bundle.main.path(forResource: "manifest",
                                                  ofType: "json",
                                                  inDirectory: "my_model") else { return }
        // Instantiate a concrete subclass of LocalModel.
        let localModel = AutoMLLocalModel(manifestPath: manifestPath)
        // [END setup_local_model]
    }
}
