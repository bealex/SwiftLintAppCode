import AAA
import ZZZ
import BBB
import CCC

public class TestDifferent3 {
    class VCCC: UIViewController {
        override func viewWillAppear(_ animated: Bool) {
            self.method()
        }
    }

    enum Numbers: String {
        case one = "one"
        case two = "two"
    }

    func foo() -> Void {}

    func abc()-> Int {}

    var myVar: Int? = nil

    func aaaaa() {
        let image = UIImage(named: "foo")

        foo = foo - 1
        let foo = 1+2

        if _ = foo() { let _ = bar()
        }else if true {}

        var myVar: Int? = nil; myVar ?? nil

        myList.sorted().first
    }

    func abc(){
    }

    func <|(lhs: Int, rhs: Int) -> Int {}
}

extension Person {
    override var age: Int { return 42 }
}

switch foo {
case (let x, let y): break
}

public class Foo {
    @IBOutlet var label: UILabel?
}

fileprivate class MyClass {
    fileprivate(set) var myInt: Int = 4
}

private class VDDC: UIViewController {
    override func loadView() {
        super.loadView()
    }
}

public protocol Foo {
    var bar: String { set get }
}

private class TotoTests {
    override func spec() {
        describe("foo") {
            let foo = Foo()
        }
    }
}
private class TotoTests: QuickSpec {
    override func spec() {
        describe("foo") {
            let foo = Foo()
        }
    }
}
private class TotoTests: QuickSpec {
    override func spec() {
        fdescribe("foo") { }
    }
}
public class TotoTests: QuickSpec {
    override func spec() {
        xdescribe("foo") { }
    }
}
