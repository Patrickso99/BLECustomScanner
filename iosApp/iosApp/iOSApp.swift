import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        IosAppInit.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
