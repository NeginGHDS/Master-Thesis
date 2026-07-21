import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeViewControllerRepresentable()
            .ignoresSafeArea(.keyboard) // Compose keyboard handling manages its own insets
    }
}

private struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#Preview {
    ContentView()
}
