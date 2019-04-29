//
// Created by Alexander Babaev on 08/02/2017.
// Copyright (c) 2017 LonelyBytes. All rights reserved.
//

import Foundation

func validate(regexp: String) -> TransformerValidator<String> {
    return { _ in
        return nil
    }
}

public enum Result<T, E> {
    case success(T)
    case failure(E)

    // MARK: - Constructors

    public init(value: T) {
        self = .success(value)
    }

    public init(error: E) {
        self = .failure(error)
    }

    public init(try foo: () throws -> T) {
        do {
            self = .success(try foo())
        } catch let error as E {
            self = .failure(error)
        } catch {
            fatalError("Error type mismatch. Expected \(E.self), but given \(type(of: error))")
        }
    }

    // MARK: - Accessors

    public var value: T? {
        return map(success: { $0 }, failure: { _ in nil })
    }

    public var error: E? {
        return map(success: { _ in nil }, failure: { $0 })
    }

    // MARK: - Map

    public func map<R>(success: (T) -> R, failure: (E) -> R) -> R {
        switch self {
        case .success(let value):
            return success(value)
        case .failure(let error):
            return failure(error)
        }
    }

    public func map<U>(_ transform: (T) -> U) -> Result<U, E> {
        return flatMap { .success(transform($0)) }
    }

    public func flatMap<U>(_ transform: (T) -> Result<U, E>) -> Result<U, E> {
        return map(success: transform, failure: Result<U, E>.failure)
    }

    public func mapError<E2>(_ transform: (E) -> E2) -> Result<T, E2> {
        return flatMapError { .failure(transform($0)) }
    }

    public func flatMapError<E2>(_ transform: (E) -> Result<T, E2>) -> Result<T, E2> {
        return map(success: Result<T, E2>.success, failure: transform)
    }

    // MARK: - Try

    public func `try`() throws -> T {
        switch self {
        case .success(let value):
            return value
        case .failure(let error as Error):
            throw error
        default:
            fatalError("\(E.self) should adopt Error.")
        }
    }

    public func tryMap<U>(_ transform: (T) throws -> U) -> Result<U, E> {
        return flatMap { value in
            Result<U, E>(try: {
                try transform(value)
            })
        }
    }

    // MARK: - Recover

    public func recover(_ value: @autoclosure () -> T) -> T {
        return self.value ?? value()
    }

    public func recover(_ result: @autoclosure () -> Result<T, E>) -> Result<T, E> {
        return map(success: { _ in self }, failure: { _ in result() })
    }

    // MARK: - Description

    public var description: String {
        return map(success: { ".success(\($0))" }, failure: { ".failure(\($0))" })
    }
}

indirect enum TransformerError: Error {
    case multiple([(String, TransformerError)])

    case badDictionary
    case transformFailed
    case requirementFailed
    case validationFailed(TransformerError)
}

typealias TransformerResult<T> = Result<T, TransformerError>
typealias TransformerValidator<T> = (T) -> TransformerError?

/// @transformer generator -> Extension
struct TestModel1 {
    var foo: String
}

extension TestModel1 {
    struct Transformer: SwiftLintTests.Transformer {
        typealias T = TestModel1

        private let fooFieldToName = "foo"
        private let fooFieldFromName = "foo"
        private let fooFieldName = "foo"

        /// @autogenerate
        func resultFrom(any value: Any) -> TransformerResult<T> {
            // swiftlint:disable line_length
            guard let dictionary: [String: Any] = CastTransformer().resultFrom(any: value).value else {
                return .failure(.badDictionary)
            }

            let fooResult: TransformerResult<String> = dictionary[fooFieldFromName]
                    .map(CastTransformer().resultFrom)
                    ?? .failure(.requirementFailed)

            var errors: [(String, TransformerError)] = []
            if let error = fooResult.error {
                errors.append(("foo", error))
            }

            guard
                    errors.isEmpty,
                    let foo = fooResult.value
                    else {
                return .failure(.multiple(errors))
            }

            guard errors.isEmpty else {
                return .failure(.multiple(errors))
            }

            return .success(T(
                    foo: foo
            ))
            // swiftlint:enable line_length
        }

        /// @autogenerate
        func resultTo(any value: T) -> Result<Any, TransformerError> {
            var dictionary: [String: Any] = [:]

            let fooResult: TransformerResult<Any> = CastTransformer().resultTo(any: value.foo)

            var errors: [(String, TransformerError)] = []
            if let error = fooResult.error { errors.append(("foo", error)) }

            guard
                    errors.isEmpty,
                    let foo = fooResult.value
            else {
                return .failure(.multiple(errors))
            }

            dictionary[fooFieldToName] = foo

            return .success(dictionary)
        }
    }
}

protocol Transformer {
    associatedtype T

    func resultFrom(any value: Any) -> TransformerResult<T>
    func resultTo(any value: T) -> Result<Any, TransformerError>
}

struct CastTransformer<Object>: Transformer {
    public typealias T = Object

    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}
struct NumberTransformer<Number: Int>: Transformer {
    public typealias T = Number

    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}
struct CustomTransformer: Transformer {
    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}
struct ArrayTransformer<ElementTransformer: Transformer>: Transformer {
    public typealias T = [ElementTransformer.T]

    public let transformer: ElementTransformer

    public init(transformer: ElementTransformer) {
        self.transformer = transformer
    }

    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}
struct DictionaryTransformer
        <KeyTransformer: Transformer, ValueTransformer: Transformer>: Transformer where KeyTransformer.T: Hashable {
    public typealias T = [KeyTransformer.T: ValueTransformer.T]

    private let keyTransformer: KeyTransformer
    private let valueTransformer: ValueTransformer

    public init(keyTransformer: KeyTransformer, valueTransformer: ValueTransformer) {
        self.keyTransformer = keyTransformer
        self.valueTransformer = valueTransformer
    }

    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}
struct DictionaryEnumTransformer<Enum: Hashable, ValueTransformer: Transformer>: Transformer where ValueTransformer.T: Hashable {
    public typealias T = Enum
    public typealias Value = ValueTransformer.T

    public let transformer: ValueTransformer
    public let enumValueDictionary: [Enum: Value]
    public let valueEnumDictionary: [Value: Enum]

    public init(transformer: ValueTransformer, dictionary: [Enum: Value]) {
        self.transformer = transformer

        enumValueDictionary = dictionary

        valueEnumDictionary = dictionary.reduce([:]) { result, keyValue in
            var result = result
            result[keyValue.1] = keyValue.0
            return result
        }
    }

    public func from(any value: Any?) -> T? {
        return transformer.from(any: value).flatMap { valueEnumDictionary[$0] }
    }

    public func to(any value: T?) -> Any? {
        return value.flatMap { enumValueDictionary[$0] }.flatMap(transformer.to(any:))
    }

    func resultFrom(any value: Any) -> TransformerResult<T> {
        return .failure(.transformFailed)
    }

    func resultTo(any value: T) -> Result<Any, TransformerError> {
        return .failure(.transformFailed)
    }
}

/// @transformer generator -> Extension
struct TestModel {
    /// @transformer -> CastTransformer<String>
    enum TestEnum {
        case variant1
        case variant2
    }

    /// @transformer transformer -> CastTransformer()
    /// @transformer JSONTransformer.name -> stringJSON
    /// @transformer DBTransformer.name -> stringDB
    /// @transformer name -> stringSimple
    /// @transformer validator -> validate(regexp: "\\d+")
    var string: String

    var stringOptional: String?
    let int: Int

    let int111111111: Int

    let ints1: [Int]

    var object: TestModel1?
    var objectArray: [TestModel1]
    var objectMap: [String: TestModel1]

    var enumValue: TestEnum

    /// @transformer excluded
    var enumValue2: TestEnum
}

extension TestModel {
    struct JSONTransformer: SwiftLintTests.Transformer {
        typealias T = TestModel

        private let enumValueFieldToName = "enumValue"
        static let TestEnumTransformer = {
            return DictionaryEnumTransformer<TestEnum, CastTransformer<String>>(
                    transformer: CastTransformer(),
                    dictionary: [
                            TestEnum.variant1: "variant1",
                            TestEnum.variant2: "variant2"
                    ]
            )
        }

        private let enumValueFieldFromName = "enumValue"
        private let objectArrayFieldToName = "objectArray"
        private let objectMapFieldToName = "objectMap"
        private let objectArrayFieldFromName = "objectArray"
        private let objectMapFieldFromName = "objectMap"
        private let stringFieldToName = "stringJSON"
        private let stringOptionalFieldToName = "stringOptional"
        private let intFieldToName = "int"
        private let int111111111FieldToName = "int111111111"
        private let ints1FieldToName = "ints1"
        private let objectFieldToName = "object"
        private let stringFieldFromName = "stringJSON"
        private let stringOptionalFieldFromName = "stringOptional"
        private let intFieldFromName = "int"
        private let int111111111FieldFromName = "int111111111"
        private let ints1FieldFromName = "ints1"
        private let objectFieldFromName = "object"

        /// @autogenerate
        func resultFrom(any value: Any) -> TransformerResult<T> {
            // swiftlint:disable line_length
            guard let dictionary: [String: Any] = CastTransformer().resultFrom(any: value).value
            else { return .failure(.badDictionary) }

            let stringResult: TransformerResult<String> = dictionary[stringFieldFromName]
                    .map(CastTransformer().resultFrom)
                    ?? .failure(.requirementFailed)
            let stringOptionalResult: TransformerResult<String?> = dictionary[stringOptionalFieldFromName]
                    .map { CastTransformer().resultFrom(any: $0).map { (value: String) -> String? in value } }
                    ?? .success(nil)
            let intResult: TransformerResult<Int> = dictionary[intFieldFromName]
                    .map(NumberTransformer().resultFrom)
                    ?? .failure(.requirementFailed)
            let int111111111Result: TransformerResult<Int> = dictionary[int111111111FieldFromName]
                    .map(NumberTransformer().resultFrom)
                    ?? .failure(.requirementFailed)
            let ints1Result: TransformerResult<[Int]> = dictionary[ints1FieldFromName]
                    .map(ArrayTransformer(transformer: NumberTransformer()).resultFrom)
                    ?? .failure(.requirementFailed)
            let objectResult: TransformerResult<TestModel1?> = dictionary[objectFieldFromName]
                    .map { TestModel1.Transformer().resultFrom(any: $0).map { (value: TestModel1) -> TestModel1? in value } }
                    ?? .success(nil)
            let objectArrayResult: TransformerResult<[TestModel1]> = dictionary[objectArrayFieldFromName]
                    .map(ArrayTransformer(transformer: TestModel1.Transformer()).resultFrom)
                    ?? .failure(.requirementFailed)
            let objectMapResult: TransformerResult<[String: TestModel1]> = dictionary[objectMapFieldFromName]
                    .map(DictionaryTransformer(keyTransformer: CastTransformer(), valueTransformer: TestModel1.Transformer()).resultFrom)
                    ?? .failure(.requirementFailed)
            let enumValueResult: TransformerResult<TestEnum> = dictionary[enumValueFieldFromName]
                    .map(JSONTransformer.TestEnumTransformer().resultFrom)
                    ?? .failure(.requirementFailed)

            var errors: [(String, TransformerError)] = []
            if let error = stringResult.error { errors.append(("string", error)) }
            if let error = stringOptionalResult.error { errors.append(("stringOptional", error)) }
            if let error = intResult.error { errors.append(("int", error)) }
            if let error = int111111111Result.error { errors.append(("int111111111", error)) }
            if let error = ints1Result.error { errors.append(("ints1", error)) }
            if let error = objectResult.error { errors.append(("object", error)) }
            if let error = objectArrayResult.error { errors.append(("objectArray", error)) }
            if let error = objectMapResult.error { errors.append(("objectMap", error)) }
            if let error = enumValueResult.error { errors.append(("enumValue", error)) }

            guard
                    errors.isEmpty,
                    let string = stringResult.value,
                    let stringOptional = stringOptionalResult.value,
                    let int = intResult.value,
                    let int111111111 = int111111111Result.value,
                    let ints1 = ints1Result.value,
                    let object = objectResult.value,
                    let objectArray = objectArrayResult.value,
                    let objectMap = objectMapResult.value,
                    let enumValue = enumValueResult.value
            else {
                return .failure(.multiple(errors))
            }

            if let error = validate(regexp: "\\d+")(string) { errors.append(("string", error)) }

            guard errors.isEmpty
            else { return .failure(.multiple(errors)) }

            return .success(T(
                    string: string,
                    stringOptional: stringOptional,
                    int: int,
                    int111111111: int111111111,
                    ints1: ints1,
                    object: object,
                    objectArray: objectArray,
                    objectMap: objectMap,
                    enumValue: enumValue
            ))
            // swiftlint:enable line_length
        }

        /// @autogenerate
        func resultTo(any value: T) -> Result<Any, TransformerError> {
            // swiftlint:disable line_length
            var dictionary: [String: Any] = [:]

            let stringResult: TransformerResult<Any> = CastTransformer().resultTo(any: value.string)
            let stringOptionalResult: TransformerResult<Any?> = value.stringOptional.map { CastTransformer().resultTo(any: $0).map { (value: Any) -> Any? in value } } ?? .success(nil)
            let intResult: TransformerResult<Any> = NumberTransformer().resultTo(any: value.int)
            let int111111111Result: TransformerResult<Any> = NumberTransformer().resultTo(any: value.int111111111)
            let ints1Result: TransformerResult<Any> = ArrayTransformer(transformer: NumberTransformer()).resultTo(any: value.ints1)
            let objectResult: TransformerResult<Any?> = value.object.map { TestModel1.Transformer().resultTo(any: $0).map { (value: Any) -> Any? in value } } ?? .success(nil)
            let objectArrayResult: TransformerResult<Any> = ArrayTransformer(transformer: TestModel1.Transformer()).resultTo(any: value.objectArray)
            let objectMapResult: TransformerResult<Any> = DictionaryTransformer(keyTransformer: CastTransformer(), valueTransformer: TestModel1.Transformer()).resultTo(any: value.objectMap)
            let enumValueResult: TransformerResult<Any> = JSONTransformer.TestEnumTransformer().resultTo(any: value.enumValue)

            var errors: [(String, TransformerError)] = []
            if let error = stringResult.error { errors.append(("string", error)) }
            if let error = stringOptionalResult.error { errors.append(("stringOptional", error)) }
            if let error = intResult.error { errors.append(("int", error)) }
            if let error = int111111111Result.error { errors.append(("int111111111", error)) }
            if let error = ints1Result.error { errors.append(("ints1", error)) }
            if let error = objectResult.error { errors.append(("object", error)) }
            if let error = objectArrayResult.error { errors.append(("objectArray", error)) }
            if let error = objectMapResult.error { errors.append(("objectMap", error)) }
            if let error = enumValueResult.error { errors.append(("enumValue", error)) }

            guard
                    errors.isEmpty,
                    let string = stringResult.value,
                    let stringOptional = stringOptionalResult.value,
                    let int = intResult.value,
                    let int111111111 = int111111111Result.value,
                    let ints1 = ints1Result.value,
                    let object = objectResult.value,
                    let objectArray = objectArrayResult.value,
                    let objectMap = objectMapResult.value,
                    let enumValue = enumValueResult.value
            else {
                return .failure(.multiple(errors))
            }

            dictionary[stringFieldToName] = string
            dictionary[stringOptionalFieldToName] = stringOptional
            dictionary[intFieldToName] = int
            dictionary[int111111111FieldToName] = int111111111
            dictionary[ints1FieldToName] = ints1
            dictionary[objectFieldToName] = object
            dictionary[objectArrayFieldToName] = objectArray
            dictionary[objectMapFieldToName] = objectMap
            dictionary[enumValueFieldToName] = enumValue

            return .success(dictionary)
            // swiftlint:enable line_length
        }
    }
}
