public class TestHierarchy1 {
    func bar() {
        // This SHOULD be highlighted in this directory
        foo.map({ $0 + 1 })
    }
}
