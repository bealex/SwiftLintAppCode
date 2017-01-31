class TestOpeningBrace {
    var aaa: Int = 1
    var ttt: TestOpeningBrace

    func abc(ttt: Any? = nil) -> Bool
    {
        let abc = self.abc()

        let b = self.ttt

        let a = self.abc

        abc(ttt: self)

        let a = {
            self.abc()
        }

        self.abc()

        return true
    }

    init(aaa: Int) {
        self.aaa = aaa
        ttt = self
    }
}
