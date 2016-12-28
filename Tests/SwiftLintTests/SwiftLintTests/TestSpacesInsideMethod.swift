protocol TestProtocol {
    func foofoo() -> Int
}

class TestClass: TestProtocol {
    func foofoo() -> Int {
        return 0

    }
}

class TestChildClass: TestClass {
    override func foofoo() -> Int {

        return 0
    }
}

class TestSpacesInsideMethod {
    private func foo() {

    }

    private func foo1() {



    }
}

class TestSpacesInsideClass1 {
}

struct TestStruct {
    private func foo(param: String) -> String {
        return ""
    }

    private func foo1() -> String {
        return ""
    }
}

enum TestEnum {

}
