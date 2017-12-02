private class TestDifferent5 {
    func aaaaa() {
        foo.map({ $0 + 1 })

        let foo = [
            1, 2, 3,
        ]

        let a = 0;

        switch foo {
        case .bar:
            something()
            break
        }

        call { (bar) in bar + 1 }

        for (_, foo) in bar.enumerated() { }

        if let _ = Foo.optionalValue {
        }
    }

    let abc: () -> () = {}

    class Foso {
        var delegate: SomeProtocol?
    }

    func validateFunction(_ file: File, kind: SwiftDeclarationKind,
            dictionary: [String: SourceKitRepresentable]) { }

    class Foso {
        @IBInspectable private let count: Int
    }

    func testFoo() {
        XCTFail()
    }
}
