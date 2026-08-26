import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        let apiToken = Bundle.main.object(forInfoDictionaryKey: "GOREST_ACCESS_TOKEN") as? String ?? ""
        IosKoinInitializerKt.startKoinIos(apiToken: apiToken)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
