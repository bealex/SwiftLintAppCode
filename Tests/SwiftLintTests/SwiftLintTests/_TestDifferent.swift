import Foundation

class TestDifferent {
    // MARK: - Notifications

    override class func tableViewStyle(for decoder: NSCoder) -> Int {
        return 2
    }

    /**
        Registers notifications for the application.
        - parameter application: application instance
     */
    func registerNotifications(application: String) {
    }

    func legacy() {
        var b = NSRange(location: 10, length: 1)
    }

    /**
        Hmm. This is doc
            - returns: something
     */
    func documentOk() -> String {
        var myVar: String? = nil
        var anotherVar = myVar ?? nil

        return ""
    }

    /**
     */
    func documentBad() -> String {
        return ""
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

class _TestDifferent {

}
