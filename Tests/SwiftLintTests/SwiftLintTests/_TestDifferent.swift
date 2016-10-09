class _TestDifferent {

}


class TestDifferent {
    func legacy() {
        var b = NSRange(location: 10, length: 1)
    }

    /**
        Hmm. This is doc
            - returns: something
     */
    func documentOk() -> String {
        var myVar = nil
        var anotherVar = myVar ?? nil
    }

    /**
     */
    func documentBad() -> String {
    }

    func statements() {
        if (true) {
        }else if true {
        } else{
        }
    }

    func nesting() {
        func nesting2() {
            func nesting3() {
            }
        }
    }

    func forceCasting() {
        var a = 1
        var b = a as! Int

        try! nesting()
    }
}
