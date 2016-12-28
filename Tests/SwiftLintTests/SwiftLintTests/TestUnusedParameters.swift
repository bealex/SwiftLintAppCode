class TestUnusedParameters {
    var closure: ((String, String) -> Void)?

    func test() {
        closure { a, b in

        }
    }
}
