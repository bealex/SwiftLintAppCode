public class TestHieararchy2 {
    func bar() {
        // This SHOULD NOT be highlighted in this directory
        foo.map({ $0 + 1 })
    }
}
