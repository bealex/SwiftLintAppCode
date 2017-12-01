class TestUnusedParameters {
    var closure: ((String, String) -> Void)?

    func test() {
        closure = { aaa, bbb in

        }
    }
}
