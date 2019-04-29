//
// AppErrorTransformer
// ToYou
//
// Created by Eugene Egorov on 05 September 2017.
// Copyright (c) 2017 Aram Meem Company Limited. All rights reserved.
//

/// Application server error transformer.
struct AppErrorTransformer: Transformer {
    typealias T = Error

    func from(any value: Any?) -> T? {
        guard let dictionary: [String: Any] = CastTransformer().from(any: value) else { return nil }

        guard let code: String = CastTransformer().from(any: dictionary["code"]) else { return nil }

        return AppError(rawValue: code) ?? AppUnknownError.unknown(code: code)
    }

    func to(any value: T?) -> Any? {
        fatalError("For receiving only")
    }
}
