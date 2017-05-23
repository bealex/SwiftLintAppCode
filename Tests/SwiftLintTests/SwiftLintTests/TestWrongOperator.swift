prefix operator ==

class TestWrongOperator1 {
    static prefix func ==(another: TestWrongOperator1) -> Bool {
        return false
    }
}
