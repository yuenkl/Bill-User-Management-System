import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        let apiToken = Bundle.main.object(forInfoDictionaryKey: "GOREST_ACCESS_TOKEN") as? String ?? ""
        #if DEBUG
        let enableApiLogging = true
        #else
        let enableApiLogging = false
        #endif
        IosKoinInitializerKt.startKoinIos(
            apiToken: apiToken,
            enableApiLogging: enableApiLogging
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
