//
// Created by Alexander Babaev on 08/02/2017.
// Copyright (c) 2017 LonelyBytes. All rights reserved.
//

import Foundation

class TextTest {
    var text: String?
}

class TestSelfAware: TextTest {
    var aaa: String = ""
    let uuu: URL
    var ttt: TestSelfAware?
    var qqq: TestSelfAware?

    var fff1: (() -> Void)?
    var fff2: (() -> Void)?
    var fff3: (() -> Void)?
    var fff4: (() -> Void)?
    var fff5: (() -> Void)?
    var fff6: (() -> Void)?

    var array: [Int] = []

    var anotherText: TextTest

    func abc(q ttt: Any? = nil) -> Bool
    {
        var array: [Int] = []
        if zip(array, self.array).isEmpty { // error (!)
        }

        let text = anotherText.text ?? ""
        guard !text.isEmpty else { return }
        if text == "" {
        }

        self.text = ""

        if let fff6 = fff6 {
            self.fff6?()
        }

        for fff5 in [ fff5 ] {
            self.fff5?()
        }

        let s: String = "as"

        var aaa = self.ttt?.aaa ?? ""
        while aaa !== self.aaa {
            print("")
        }

        switch (aaa, fff4) {
            case (let s, let fff4):
                self.fff4?()
            case (let s, let ss):
                guard let fff1 = fff1 else { return false }

                self.fff1?()
            default:
                break
        }

        guard let fff3 = fff3 else { return false }

        self.fff3?()

        var fff2 = { print("") }
        self.fff2?()

        let qqq: TestSelfAware
        if self.qqq?.aaa != qqq.aaa {
            print("")
        }

        let bbb = self.ttt.aaa

        let abc1 = self.abc()

        let b = self.ttt

        let a = self.abc // do not need it here

        abc(ttt: self)

        self.abc() // do not need it here

        let aa = {
            self.abc()
        }

        return true
    }

    init(a1a1 uuu: String) {
        self.uuu = URL(string: uuu)!
        let aaa = self.aaa
    }

    init(aaa: Int) {
        self.init()
    }
}
