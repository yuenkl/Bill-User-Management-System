import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        let apiToken = configuredApiToken()
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

private func configuredApiToken() -> String {
    let infoPlistToken = (Bundle.main.object(forInfoDictionaryKey: "GOREST_ACCESS_TOKEN") as? String ?? "")
        .trimmingCharacters(in: .whitespacesAndNewlines)
    if !infoPlistToken.isEmpty,
        !infoPlistToken.hasPrefix("$("),
        infoPlistToken != "YOUR_GOREST_ACCESS_TOKEN" {
        return infoPlistToken
    }

    guard
        let url = Bundle.main.url(forResource: "GoRestToken", withExtension: "txt"),
        let token = try? String(contentsOf: url, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    else {
        return ""
    }
    return token
}
