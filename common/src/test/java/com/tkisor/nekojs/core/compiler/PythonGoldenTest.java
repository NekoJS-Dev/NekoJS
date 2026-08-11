package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden end-to-end suite for {@link PythonToJsCompiler}. Each case feeds a realistic
 * (non-toy) Python snippet through the transpiler, executes the emitted JS via GraalJS, and
 * asserts the correct runtime result. Failure messages include the emitted JS so failures are
 * trivial to diagnose.
 *
 * <p>Lives in this package solely to reach the package-private
 * {@link CompilerExecutionAssertions} eval helper, mirroring {@link PythonToJsCompilerTest} and
 * the other compiler golden tests. Every snippet intentionally stays within the documented v1
 * subset (no try/except, slicing, generators, dict-comprehensions, or stdlib methods beyond the
 * listed builtins).
 */
class PythonGoldenTest {

    private final PythonToJsCompiler compiler = new PythonToJsCompiler();

    /** Transpile helper — every test needs the emitted JS for its failure message. */
    private String py(String src) throws Exception {
        return compiler.compile(Path.of("test.py"), src);
    }

    // ----------------------------------------------------------------------
    // Control flow & recursion
    // ----------------------------------------------------------------------

    @Test
    void fizzBuzzPipeline() throws Exception {
        // List comprehension whose element is a nested ternary: the canonical FizzBuzz over 1..15.
        // Indices 14/2/4 correspond to n = 15 ("FizzBuzz"), n = 3 ("Fizz"), n = 5 ("Buzz").
        String src = """
                result = ["FizzBuzz" if n % 15 == 0 else ("Fizz" if n % 3 == 0 else ("Buzz" if n % 5 == 0 else str(n))) for n in range(1, 16)]
                result[14] + "|" + result[2] + "|" + result[4]
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals("FizzBuzz|Fizz|Buzz", eval.value().asString(),
                "FizzBuzz comprehension must classify 15/3/5 correctly: " + js);
        }
    }

    @Test
    void factorialRecursive() throws Exception {
        String src = """
                def factorial(n):
                    if n <= 1:
                        return 1
                    return n * factorial(n - 1)
                factorial(6)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(720, eval.value().asLong(),
                "factorial(6) must be 720 via recursion: " + js);
        }
    }

    @Test
    void fibonacciRecursive() throws Exception {
        String src = """
                def fib(n):
                    if n < 2:
                        return n
                    return fib(n - 1) + fib(n - 2)
                fib(10)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(55, eval.value().asLong(),
                "fib(10) must be 55 via naive recursion: " + js);
        }
    }

    @Test
    void gradeClassifierIfElifElse() throws Exception {
        // Exercises a full if/elif/elif/elif/else chain (lowered to nested else-if).
        String src = """
                def grade(score):
                    if score >= 90:
                        return 'A'
                    elif score >= 80:
                        return 'B'
                    elif score >= 70:
                        return 'C'
                    elif score >= 60:
                        return 'D'
                    else:
                        return 'F'
                grade(95) + grade(85) + grade(72) + grade(61) + grade(40)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals("ABCDF", eval.value().asString(),
                "grade() elif chain must classify all five bands: " + js);
        }
    }

    @Test
    void gcdEuclideanWhileLoop() throws Exception {
        // while-loop with tuple-unpack swap: gcd(48, 18) = 6.
        String src = """
                def gcd(a, b):
                    while b != 0:
                        a, b = b, a % b
                    return a
                gcd(48, 18)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(6, eval.value().asLong(),
                "gcd(48, 18) must be 6 via Euclid's algorithm: " + js);
        }
    }

    @Test
    void vowelCounterInString() throws Exception {
        // Iterating a string char-by-char (for-of over a JS string yields characters).
        String src = """
                text = "the quick brown fox jumps over the lazy dog"
                def count_vowels(s):
                    count = 0
                    for ch in s:
                        if ch == 'a' or ch == 'e' or ch == 'i' or ch == 'o' or ch == 'u':
                            count += 1
                    return count
                count_vowels(text)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(11, eval.value().asLong(),
                "the pangram must contain 11 lowercase vowels: " + js);
        }
    }

    @Test
    void primeFilterComprehension() throws Exception {
        // while-loop primality test composed with a list-comprehension filter that calls it.
        // Primes below 30 start 2,3,5,7 -> sum of the first four = 17.
        String src = """
                def is_prime(n):
                    if n < 2:
                        return False
                    i = 2
                    while i * i <= n:
                        if n % i == 0:
                            return False
                        i += 1
                    return True
                primes = [n for n in range(2, 30) if is_prime(n)]
                primes[0] + primes[1] + primes[2] + primes[3]
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(17, eval.value().asLong(),
                "first four primes below 30 must sum to 17: " + js);
        }
    }

    @Test
    void coinChangeWithIntegerDivision() throws Exception {
        // Greedy coin breakdown using // and % with parameter reassignment.
        // 99c = 3 quarters + 2 dimes + 0 nickels + 4 pennies -> 3*1000 + 2*100 + 0*10 + 4 = 3204.
        String src = """
                def make_change(amount):
                    quarters = amount // 25
                    amount = amount % 25
                    dimes = amount // 10
                    amount = amount % 10
                    nickels = amount // 5
                    pennies = amount % 5
                    return quarters * 1000 + dimes * 100 + nickels * 10 + pennies
                make_change(99)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(3204, eval.value().asLong(),
                "make_change(99) must encode 3q+2d+0n+4p as 3204: " + js);
        }
    }

    // ----------------------------------------------------------------------
    // Closures & lambdas
    // ----------------------------------------------------------------------

    @Test
    void closureCounterWithSharedState() throws Exception {
        // A closure that mutates a shared enclosing list — three calls yield 1, 2, 3 (sum 6).
        String src = """
                def make_counter():
                    state = [0]
                    def inc():
                        state[0] = state[0] + 1
                        return state[0]
                    return inc
                c = make_counter()
                first = c()
                second = c()
                third = c()
                first + second + third
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(6, eval.value().asLong(),
                "closure counter must share state across three calls: " + js);
        }
    }

    @Test
    void lambdaMultiplierFactory() throws Exception {
        // lambda capturing its enclosing parameter -> arrow function closure.
        String src = """
                def multiplier(factor):
                    return lambda x: x * factor
                double = multiplier(2)
                triple = multiplier(3)
                double(5) + triple(5)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(25, eval.value().asLong(),
                "double(5) + triple(5) must be 10 + 15 = 25: " + js);
        }
    }

    // ----------------------------------------------------------------------
    // List / data-structure manipulation
    // ----------------------------------------------------------------------

    @Test
    void sumOfEvenSquaresPipeline() throws Exception {
        // Chained list-comprehension pipeline: range -> filter evens -> square -> sum.
        // 2,4,6,8,10 squared = 4,16,36,64,100 -> sum 220.
        String src = """
                nums = [x for x in range(1, 11)]
                evens = [n for n in nums if n % 2 == 0]
                squares = [n * n for n in evens]
                sum(squares)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(220, eval.value().asLong(),
                "sum of squares of evens in 1..10 must be 220: " + js);
        }
    }

    @Test
    void selectionSortIndexAssignment() throws Exception {
        // Nested loops + index assignment into a comprehension-copied array; result digits "123589".
        String src = """
                def selection_sort(items):
                    arr = [x for x in items]
                    n = len(arr)
                    for i in range(n):
                        min_idx = i
                        for j in range(i + 1, n):
                            if arr[j] < arr[min_idx]:
                                min_idx = j
                        if min_idx != i:
                            temp = arr[i]
                            arr[i] = arr[min_idx]
                            arr[min_idx] = temp
                    return arr
                s = selection_sort([5, 2, 8, 1, 9, 3])
                str(s[0]) + str(s[1]) + str(s[2]) + str(s[3]) + str(s[4]) + str(s[5])
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals("123589", eval.value().asString(),
                "selection_sort must fully order the input ascending: " + js);
        }
    }

    @Test
    void digitFrequencyDictMutation() throws Exception {
        // Mutating a pre-keyed dict while iterating a numeric string.
        // "1223334444" -> freq[1..4] = 1,2,3,4 -> "1234".
        String src = """
                def digit_freq(n):
                    freq = {0: 0, 1: 0, 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0, 8: 0, 9: 0}
                    for d in n:
                        freq[int(d)] = freq[int(d)] + 1
                    return freq
                f = digit_freq("1223334444")
                str(f[1]) + str(f[2]) + str(f[3]) + str(f[4])
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals("1234", eval.value().asString(),
                "digit frequencies of '1223334444' must be 1,2,3,4: " + js);
        }
    }

    @Test
    void listOfDictsAgeAndMaxMultiTarget() throws Exception {
        // Nested data-structure (list of dicts) + multi-target assignment (total = oldest = 0).
        // Ages 30/25/35 -> total 90, oldest 35 -> 35*1000 + 90 = 35090.
        String src = """
                people = [
                    {"name": "alice", "age": 30},
                    {"name": "bob", "age": 25},
                    {"name": "carol", "age": 35},
                ]
                ages = [p["age"] for p in people]
                total = oldest = 0
                for a in ages:
                    total += a
                    if a > oldest:
                        oldest = a
                oldest * 1000 + total
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(35090, eval.value().asLong(),
                "list-of-dicts must total 90 with max 35 -> 35090: " + js);
        }
    }

    @Test
    void nestedListComprehensionTimesTable() throws Exception {
        // A list comprehension nested inside another (each is single-for) builds an n x n table.
        // t[2][3]=12 (3*4), t[0][0]=1, t[3][3]=16 -> 29.
        String src = """
                def times_table(n):
                    return [[(i + 1) * (j + 1) for j in range(n)] for i in range(n)]
                t = times_table(4)
                t[2][3] + t[0][0] + t[3][3]
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(29, eval.value().asLong(),
                "4x4 times table cells (2,3)+(0,0)+(3,3) must be 12+1+16 = 29: " + js);
        }
    }

    @Test
    void variadicSumOfSquaresArgs() throws Exception {
        // *args collected into a rest parameter and reduced in a for-loop.
        String src = """
                def sum_of_squares(*vals):
                    total = 0
                    for v in vals:
                        total += v * v
                    return total
                sum_of_squares(1, 2, 3, 4)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(30, eval.value().asLong(),
                "sum_of_squares(1,2,3,4) must be 1+4+9+16 = 30: " + js);
        }
    }

    @Test
    void enumerateFindIndexTupleUnpacking() throws Exception {
        // enumerate() + tuple-unpacking in the for-target to locate an item's index.
        String src = """
                def find_index(items, target):
                    for i, x in enumerate(items):
                        if x == target:
                            return i
                    return -1
                find_index(["apple", "banana", "cherry"], "banana")
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(1, eval.value().asLong(),
                "find_index must return 1 for 'banana' in the list: " + js);
        }
    }

    // ----------------------------------------------------------------------
    // Classes
    // ----------------------------------------------------------------------

    @Test
    void shapeClassHierarchyArea() throws Exception {
        // Multi-level polymorphism: Shape base + Circle/Rectangle overrides with super().__init__.
        // Circle(2)=12, Rectangle(3,4)=12, Circle(5)=75 (pi approximated as 3) -> total 99.
        String src = """
                class Shape:
                    def __init__(self, name):
                        self.name = name
                    def area(self):
                        return 0
                class Circle(Shape):
                    def __init__(self, r):
                        super().__init__("circle")
                        self.r = r
                    def area(self):
                        return 3 * self.r * self.r
                class Rectangle(Shape):
                    def __init__(self, w, h):
                        super().__init__("rectangle")
                        self.w = w
                        self.h = h
                    def area(self):
                        return self.w * self.h
                shapes = [Circle(2), Rectangle(3, 4), Circle(5)]
                total = 0
                for s in shapes:
                    total += s.area()
                total
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(99, eval.value().asLong(),
                "Shape hierarchy total area must be 12 + 12 + 75 = 99: " + js);
        }
    }

    @Test
    void bankAccountDepositWithdrawStr() throws Exception {
        // Class with a default param, augmented assignment, an __str__ -> toString via f-string.
        // 100 + 50 - 30 = 120 -> str(acc) = "neko:120".
        String src = """
                class Account:
                    def __init__(self, owner, balance=0):
                        self.owner = owner
                        self.balance = balance
                    def deposit(self, amount):
                        self.balance += amount
                        return self.balance
                    def withdraw(self, amount):
                        if amount > self.balance:
                            return -1
                        self.balance -= amount
                        return self.balance
                    def __str__(self):
                        return f"{self.owner}:{self.balance}"
                acc = Account("neko", 100)
                acc.deposit(50)
                acc.withdraw(30)
                str(acc)
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals("neko:120", eval.value().asString(),
                "Account must end at balance 120 and stringify to 'neko:120': " + js);
        }
    }

    @Test
    void vectorStaticAndInstanceMethods() throws Exception {
        // @staticmethod (lowered to a static class method) alongside instance methods.
        // v.magnitude_squared() = 25; Vector.dot([1,2],[3,4]) = 11 -> 36.
        String src = """
                class Vector:
                    @staticmethod
                    def dot(a, b):
                        return a[0] * b[0] + a[1] * b[1]
                    def __init__(self, x, y):
                        self.x = x
                        self.y = y
                    def magnitude_squared(self):
                        return self.x * self.x + self.y * self.y
                v = Vector(3, 4)
                v.magnitude_squared() + Vector.dot([1, 2], [3, 4])
                """;
        String js = py(src);
        try (var eval = CompilerExecutionAssertions.eval(js)) {
            assertEquals(36, eval.value().asLong(),
                "Vector must yield |(3,4)|^2 + dot = 25 + 11 = 36: " + js);
        }
    }
}
