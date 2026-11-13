package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.NekoCompileException;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode.Param;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits JavaScript source from a {@link PythonNode} AST. Compound expressions self-parenthesize
 * so the emitted text re-parses with correct precedence without a minimal-parenthesis pass.
 *
 * <p>v1 mappings: Python functions use hoisted {@code function}; names use {@code var} (always
 * redeclarable); {@code range/len/print} map to JS idioms; {@code //} → {@code Math.floor(a/b)};
 * dict → object literal; tuple/list → array; set → {@code new Set([...])}; f-string → template
 * literal.
 */
public final class PythonEmitter {

    /**
     * The runtime helper that implements the common Python format-spec mini-language for
     * {@code f"{x:spec}"}. Emitted at the top of a module only when it actually uses a format spec
     * or conversion (detected via {@link #scanHelpers}). Handles: {@code .Nf}, width, alignment
     * ({@code < > ^ =}), zero-fill ({@code 0}), thousands ({@code ,}), precision (incl. string
     * truncation), and types {@code f e E x X o b % d c}.
     */
    private static final String[] FMT_HELPER = {
            "const __nekoFmt = function (__v, __spec, __conv) {",
            "  function rep(c, n) { var r = ''; for (var k = 0; k < n; k++) r += c; return r; }",
            "  function num(x) { return typeof x === 'number' ? x : parseFloat(x); }",
            "  if (__conv === 'r' || __conv === 'a') __v = JSON.stringify(__v);",
            "  else if (__conv === 's') __v = (__v === null) ? 'None' : String(__v);",
            "  else __v = (__v === null || __v === undefined) ? 'None' : String(__v);",
            "  if (!__spec) return __v;",
            "  var s = __spec, fill = ' ', align = null;",
            "  if (s.length >= 2 && (s.charAt(1) === '<' || s.charAt(1) === '>' || s.charAt(1) === '^' || s.charAt(1) === '=')) { fill = s.charAt(0); align = s.charAt(1); s = s.substring(2); }",
            "  else if (s.length >= 1 && (s.charAt(0) === '<' || s.charAt(0) === '>' || s.charAt(0) === '^' || s.charAt(0) === '=')) { align = s.charAt(0); s = s.substring(1); }",
            "  if (s.length >= 1 && s.charAt(0) === '0') { if (align === null) align = '='; if (fill === ' ') fill = '0'; s = s.substring(1); }",
            "  var width = 0, j = 0;",
            "  while (j < s.length && s.charAt(j) >= '0' && s.charAt(j) <= '9') { width = width * 10 + (s.charCodeAt(j) - 48); j++; }",
            "  s = s.substring(j);",
            "  var comma = s.length >= 1 && s.charAt(0) === ','; if (comma) s = s.substring(1);",
            "  var prec = -1;",
            "  if (s.length >= 1 && s.charAt(0) === '.') { s = s.substring(1); var p = ''; while (s.length >= 1 && s.charAt(0) >= '0' && s.charAt(0) <= '9') { p += s.charAt(0); s = s.substring(1); } prec = p ? parseInt(p, 10) : 0; }",
            "  var type = s.length >= 1 ? s.charAt(0) : '';",
            "  var isNum = (typeof __v === 'number'), out;",
            "  if (type === 'f' || type === 'F') out = num(__v).toFixed(prec < 0 ? 6 : prec);",
            "  else if (type === 'e' || type === 'E') { out = num(__v).toExponential(prec < 0 ? 6 : prec); if (type === 'E') out = out.toUpperCase(); }",
            "  else if (type === 'x' || type === 'X' || type === 'o' || type === 'b') { var iv = Math.trunc(num(__v)); out = (iv < 0 ? '-' : '') + Math.abs(iv).toString(type === 'x' || type === 'X' ? 16 : type === 'o' ? 8 : 2); if (type === 'X') out = out.toUpperCase(); }",
            "  else if (type === '%') out = (num(__v) * 100).toFixed(prec < 0 ? 6 : prec) + '%';",
            "  else if (type === 'd') out = String(Math.trunc(num(__v)));",
            "  else if (type === 'c') out = String.fromCodePoint(Math.trunc(num(__v)));",
            "  else if (prec >= 0 && typeof __v === 'string') out = __v.substring(0, prec);",
            "  else out = String(__v);",
            "  if (comma) { var d = out.indexOf('.'); var ip = d < 0 ? out : out.substring(0, d); var dp = d < 0 ? '' : out.substring(d); var tmp = '', nn = ip.length; for (var k = 0; k < nn; k++) { if (k > 0 && (nn - k) % 3 === 0 && ip.charAt(k) >= '0' && ip.charAt(k) <= '9') tmp += ','; tmp += ip.charAt(k); } out = tmp + dp; }",
            "  if (out.length < width) {",
            "    var pad = width - out.length;",
            "    if (align === '<') out = out + rep(fill, pad);",
            "    else if (align === '^') { var h = Math.floor(pad / 2); out = rep(fill, h) + out + rep(fill, pad - h); }",
            "    else if (align === '=') { var sg = (out.charAt(0) === '-' || out.charAt(0) === '+') ? out.charAt(0) : ''; out = sg + rep(fill, pad) + (sg ? out.substring(1) : out); }",
            "    else if (align === '>' || ((align === null) && (isNum || type !== ''))) out = rep(fill, pad) + out;",
            "    else out = out + rep(fill, pad);",
            "  }",
            "  return out;",
            "};"
    };

    /**
     * Python truthiness helper: Empty arrays/objects (literals), empty Maps/Sets are false (JS objects/arrays are always true).
     * User instances (constructor !== Object) are always true, corresponding to default Python semantics when {@code __bool__}/
     * {@code __len__} are undefined. Emitted only when the module uses truthiness checks (see {@link #scanHelpers}).
     */
    private static final String TRUTHY_HELPER =
            "const __nekoTruthy = function (__v) {"
            + " if (Array.isArray(__v)) return __v.length > 0;"
            + " if (__v instanceof Map || __v instanceof Set) return __v.size > 0;"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v).length > 0;"
            + " return Boolean(__v); };";

    /**
     * Python and/or use value semantics (return the operand, not boolean) and short-circuit based on Python truthiness.
     * The right operand is wrapped in a thunk for laziness; the left operand is evaluated once.
     */
    private static final String AND_HELPER =
            "const __nekoAnd = function (__a, __b) { return __nekoTruthy(__a) ? __b() : __a; };";
    private static final String OR_HELPER =
            "const __nekoOr = function (__a, __b) { return __nekoTruthy(__a) ? __a : __b(); };";

    /**
     * Python modulo: Result sign follows the divisor (JS {@code %} follows the dividend, {@code -7 % 2} is -1).
     * Division by zero throws {@code ZeroDivisionError} (JS silently results in NaN).
     * Note {@code __b === 0} also holds for {@code -0} (JS treats -0 as 0), consistent with Python's
     * {@code 1 / -0.0} throwing ZeroDivisionError.
     * When the left operand is a string, it acts as Python %-formatting ({@code "%s=%d" % (k, v)}), routed to
     * {@link #PERCENT_HELPER}; previously, the string branch was missing, silently resulting in {@code NaN}.
     */
    private static final String MOD_HELPER =
            "const __nekoMod = function (__a, __b) {"
            + " if (typeof __a === 'string') return __nekoPyPercent(__a, Array.isArray(__b) ? __b : [__b]);"
            + " if (__b === 0) throw new ZeroDivisionError(\"integer division or modulo by zero\");"
            + " return ((__a % __b) + __b) % __b; };";

    /**
     * The supported Python %-formatting subset: {@code %s %r %d %i %f %g %x %X %o %%}, with width
     * (including {@code -} for left alignment) and precision. Number and string conversion keeps the
     * Python spelling of {@code __nekoPyStr}.
     */
    private static final String PERCENT_HELPER =
            "const __nekoPyPercent = function (fmt, args) {"
            + " var ai = 0;"
            + " return String(fmt).replace(/%(-?\\d+)?(?:\\.(\\d+))?([srdifgxXo%])/g, function (_, w, p, t) {"
            + "  if (t === '%') return '%';"
            + "  var v = args[ai++];"
            + "  var s;"
            + "  if (t === 's' || t === 'r') { s = (v === null || v === undefined) ? 'None' : (t === 'r' ? JSON.stringify(v) : __nekoPyStr(v)); }"
            + "  else if (t === 'd' || t === 'i') s = String(Math.trunc(Number(v)));"
            + "  else if (t === 'f') s = Number(v).toFixed(p === undefined ? 6 : parseInt(p, 10));"
            + "  else if (t === 'x' || t === 'X' || t === 'o') { var iv = Math.trunc(Number(v)); s = Math.abs(iv).toString(t === 'o' ? 8 : 16); if (t === 'X') s = s.toUpperCase(); if (iv < 0) s = '-' + s; }"
            + "  else s = String(Number(v));"
            + "  if (w !== undefined) { var width = parseInt(w, 10); var left = width < 0; if (left) width = -width; while (s.length < width) s = left ? s + ' ' : ' ' + s; }"
            + "  return s;"
            + " });"
            + " };";

    /**
     * String conversion for Python {@code str()}, print and {@code %s}: {@code None}/{@code True}/
     * {@code False} are spelled out, arrays are converted element by element with PyStr and joined
     * with {@code ', '} (JS String(arr) has the same shape but spells the elements the JS way), and
     * plain objects render as {@code {k: v}}. A custom object falls back to the JS spelling because
     * {@code __str__} is not implemented.
     */
    private static final String PYSTR_HELPER =
            "const __nekoPyStr = function (v) {"
            + " if (v === null || v === undefined) return 'None';"
            + " if (v === true) return 'True';"
            + " if (v === false) return 'False';"
            + " if (Array.isArray(v)) return '[' + v.map(__nekoPyStr).join(', ') + ']';"
            + " if (typeof v === 'object' && v !== null && v.constructor === Object) {"
            + "  return '{' + Object.entries(v).map(function (kv) { return \"'\" + String(kv[0]).replace(/'/g, \"\\\\'\") + \"': \" + __nekoPyStr(kv[1]); }).join(', ') + '}';"
            + " }"
            + " return String(v);"
            + " };";

    /**
     * Python true division: a zero divisor throws {@code ZeroDivisionError} (JS silently yields +/-Infinity).
     * The message matches CPython's {@code "division by zero"}.
     */
    private static final String DIV_HELPER =
            "const __nekoDiv = function (__a, __b) {"
            + " if (__b === 0) throw new ZeroDivisionError(\"division by zero\");"
            + " return __a / __b; };";

    /** Python floor division: the same zero-divisor check first, then {@code Math.floor}. */
    private static final String FLOORDIV_HELPER =
            "const __nekoFloorDiv = function (__a, __b) {"
            + " if (__b === 0) throw new ZeroDivisionError(\"integer division or modulo by zero\");"
            + " return Math.floor(__a / __b); };";

    /**
     * Sequence repetition: {@code [0] * 4} / {@code 'ab' * 3} in either direction; a negative or zero count is
     * empty, anything else is numeric multiplication. JS {@code *} gives NaN/0 silently for a string/array,
     * so every repetition goes through this helper.
     */
    private static final String MUL_HELPER =
            "const __nekoMul = function (__a, __b) {"
            + " if (Array.isArray(__a) && typeof __b === 'number') { var __r = []; var __n = Math.max(0, Math.trunc(__b)); for (var __i = 0; __i < __n; __i++) __r = __r.concat(__a); return __r; }"
            + " if (Array.isArray(__b) && typeof __a === 'number') { var __r2 = []; var __n2 = Math.max(0, Math.trunc(__a)); for (var __j = 0; __j < __n2; __j++) __r2 = __r2.concat(__b); return __r2; }"
            + " if (typeof __a === 'string' && typeof __b === 'number') return __b > 0 ? __a.repeat(Math.trunc(__b)) : '';"
            + " if (typeof __b === 'string' && typeof __a === 'number') return __a > 0 ? __b.repeat(Math.trunc(__a)) : '';"
            + " return __a * __b; };";

    /**
     * Python {@code +}/{@code +=}: array concatenation ({@code [1,2] + [3]} -> {@code [1,2,3]}; JS {@code +} would
     * stringify the two arrays into {@code "1,23"}), str+str concatenation and numeric addition. Mixing str with a
     * non-str (such as {@code 'a' + 5}) throws {@code TypeError} as Python does (JS silently coerces it to {@code 'a5'});
     * the thrown class is the TypeError of the exception prelude (needsAdd pulls the prelude in, following the
     * needsMod -> ZeroDivisionError precedent). Other non-str/non-array combinations fall back to JS {@code +}
     * semantics. f-string and adjacent string literal concatenation happens in the parser (one STRING token) and skips this helper.
     */
    private static final String ADD_HELPER =
            "const __nekoAdd = function (__a, __b) {"
            + " if (Array.isArray(__a) && Array.isArray(__b)) return __a.concat(__b);"
            + " var __sa = (typeof __a === 'string'), __sb = (typeof __b === 'string');"
            + " if (__sa && __sb) return __a + __b;"
            + " if (__sa || __sb) throw new TypeError(\"can only concatenate str (not \\\"\""
            + " + (__sa ? typeof __b : typeof __a) + \"\\\") to str\");"
            + " return __a + __b; };";

    /**
     * Python {@code str.replace(old, new[, count])}: JS {@code replaceAll} reads old as a regex and {@code replace}
     * reads a {@code $} in new as a replacement pattern, both silently wrong. This helper uses {@code split(old).join(rep)}
     * for literal replacement (a string separator stays literal in split). count&lt;0 = no limit; count==0 = returned
     * unchanged; count&gt;0 = the first count occurrences. An empty old inserts between characters and at both ends
     * (len+1 places), and count limits the insertions.
     */
    private static final String REPLACE_HELPER =
            "const __nekoPyStrReplace = function (__s, __old, __rep, __count) {"
            + " if (__count === 0) return __s;"
            + " if (__old === '') {"
            + "   var __cp = Array.from(__s);"
            + "   var __ins = __cp.length + 1;"
            + "   var __k = (__count < 0 || __count >= __ins) ? __ins : Math.trunc(__count);"
            + "   if (__k <= 0) return __s;"
            + "   if (__cp.length === 0) return __rep;"
            + "   if (__k >= __ins) return __rep + __cp.join(__rep) + __rep;"
            + "   return __rep + __cp.slice(0, __k).join(__rep) + __cp.slice(__k).join('');"
            + " }"
            + " var __parts = __s.split(__old);"
            + " var __nsep = __parts.length - 1;"
            + " if (__count < 0 || __count >= __nsep) return __parts.join(__rep);"
            + " var __k2 = Math.trunc(__count);"
            + " if (__k2 <= 0) return __s;"
            + " return __parts.slice(0, __k2 + 1).join(__rep) + __old + __parts.slice(__k2 + 1).join(__old);"
            + "};";

    /**
     * Python {@code str.count(sub[, start[, end]])}: start/end clamp as Python slices do (a negative value counts
     * from the end), counting is non-overlapping, and an empty substring counts as the slice length + 1 (as CPython:
     * {@code ''.count('') == 1}).
     */
    private static final String STR_COUNT_HELPER =
            "const __nekoPyStrCount = function (__s, __sub, __start, __end) {"
            + " var __cp = Array.from(__s);"
            + " var __n = __cp.length;"
            + " var __lo = (__start === null || __start === undefined) ? 0 : Math.trunc(__start);"
            + " var __hi = (__end === null || __end === undefined) ? __n : Math.trunc(__end);"
            + " if (__lo < 0) { __lo += __n; if (__lo < 0) __lo = 0; }"
            + " if (__hi < 0) { __hi += __n; if (__hi < 0) __hi = 0; }"
            + " if (__hi > __n) __hi = __n;"
            + " if (__lo > __hi) return 0;"
            + " if (__sub === '') return (__hi - __lo) + 1;"
            + " var __slice = __cp.slice(__lo, __hi).join('');"
            + " var __count = 0, __idx = 0, __subLen = __sub.length;"
            + " while (true) {"
            + "   var __i = __slice.indexOf(__sub, __idx);"
            + "   if (__i < 0) break;"
            + "   __count++;"
            + "   __idx = __i + __subLen;"
            + " }"
            + " return __count;"
            + "};";

    /**
     * Python {@code str.zfill(width)}: the sign (-/+) stays in front and the zeros go between sign and digits;
     * when width &lt;= len the input is returned unchanged. JS {@code padStart} would pad the minus sign as content ({@code 00-42}).
     */
    private static final String ZFILL_HELPER =
            "const __nekoPyZfill = function (__s, __w) {"
            + " __s = String(__s);"
            + " __w = Math.trunc(__w);"
            + " var __len = Array.from(__s).length;"
            + " if (__len >= __w) return __s;"
            + " var __sign = (__s.charAt(0) === '-' || __s.charAt(0) === '+') ? __s.charAt(0) : '';"
            + " var __digits = (__sign === '') ? __s : __s.substring(1);"
            + " return __sign + \"0\".repeat(__w - __len) + __digits;"
            + "};";

    /**
     * {@code in} membership test: dict (an object literal) by own keys, Map/Set by {@code .has}, array/string by
     * {@code .includes}; using {@code .includes} for dict/set would be a runtime TypeError.
     */
    private static final String IN_HELPER =
            "const __nekoIn = function (__v, __c) {"
            + " if (__c instanceof Map || __c instanceof Set) return __c.has(__v);"
            + " if (Array.isArray(__c) || typeof __c === 'string') return __c.includes(__v);"
            + " if (typeof __c === 'object' && __c !== null) return Object.prototype.hasOwnProperty.call(__c, __v);"
            + " return false; };";

    /**
     * Iterable normalization: dict (an object literal) and Map iterate their keys, everything else (array/string/
     * generator/Set) passes through unchanged. A bare {@code for (var k of d)} over a plain object is a runtime TypeError.
     */
    private static final String ITER_HELPER =
            "const __nekoIter = function (__v) {"
            + " if (__v instanceof Map) return Array.from(__v.keys());"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v);"
            + " return __v; };";

    /** {@code len()}: length for an array/string, size for Map/Set, the key count for a dict (an object literal). */
    private static final String LEN_HELPER =
            "const __nekoLen = function (__v) {"
            + " if (__v instanceof Map || __v instanceof Set) return __v.size;"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v).length;"
            + " return __v.length; };";

    /**
     * Built-in exception match helper: a class match first (the prelude class hierarchy), then a {@code Error.name}
     * fallback, so a native JS error (the native TypeError from {@code null.foo}, say) is still caught by {@code except TypeError}.
     */
    private static final String EXCIS_HELPER =
            "const __nekoExcIs = function (__e, __t) {"
            + " return (__e instanceof __t) || (__e instanceof Error && __e.name === __t.name); };";

    /**
     * Built-in exception prelude: the {@code class X extends Error} family, so {@code raise ValueError('boom')}
     * emits {@code throw new ValueError("boom")} (a bare {@code ValueError} reference would be a runtime ReferenceError)
     * and {@code except ValueError} matches exactly through {@code instanceof ValueError}. Base classes come before
     * derived ones; emitted only when the module references a built-in exception name (or contains an assert).
     */
    private static final String[] EXC_PRELUDE = {
            "class Exception extends Error {}",
            "class ArithmeticError extends Exception {}",
            "class LookupError extends Exception {}",
            "class RuntimeError extends Exception {}",
            "class ValueError extends Exception {}",
            "class TypeError extends Exception {}",
            "class AttributeError extends Exception {}",
            "class NameError extends Exception {}",
            "class AssertionError extends Exception {}",
            "class StopIteration extends Exception {}",
            "class ImportError extends Exception {}",
            "class OSError extends Exception {}",
            "class EOFError extends Exception {}",
            "class MemoryError extends Exception {}",
            "class OverflowError extends ArithmeticError {}",
            "class ZeroDivisionError extends ArithmeticError {}",
            "class KeyError extends LookupError {}",
            "class IndexError extends LookupError {}",
            "class NotImplementedError extends RuntimeError {}",
            "class RecursionError extends RuntimeError {}",
            // A JS subclass instance inherits Error.prototype.name ("Error") for .name; reset it to the class name so
            // e.name / e.message match Python's str(e) convention and __nekoExcIs can fall back to matching a native
            // error's name.
            "[Exception, ValueError, TypeError, AttributeError, NameError, AssertionError, StopIteration,"
            + " ImportError, OSError, EOFError, MemoryError, OverflowError, ZeroDivisionError, ArithmeticError,"
            + " LookupError, KeyError, IndexError, NotImplementedError, RecursionError]"
            + " .forEach(function (C) { C.prototype.name = C.name; })",
    };

    /**
     * Built-in type probe prelude: when the second argument of {@code isinstance(x, int/str/list/...)} is a built-in
     * type name, the check uses probe functions (routed by {@link #exceptionCond}); {@code x instanceof str} would
     * be a runtime ReferenceError since JS has no such globals. Deliberate semantic choices (see each probe):
     * int accepts a boolean (Python's bool is an int subclass; {@code isinstance(True, int)} is True); float accepts
     * any number (int and float are both a JS number here, so 5 and 5.0 are indistinguishable); list and tuple are
     * both a JS array (indistinguishable); dict means only an object literal (constructor === Object, matching
     * {@link #TRUTHY_HELPER}/{@link #ITER_HELPER} and excluding arrays/Map/Set/user class instances); set and frozenset
     * are both a Set. Exception class names still take the exact instanceof path of {@link #EXCIS_HELPER} (in a mixed
     * tuple such as {@code isinstance(e, (ValueError, str))} both routes coexist in the same disjunction).
     * Emitted only when an isinstance/except type position references these names (see {@link #scanHelpers}).
     */
    private static final String[] TYPE_PRELUDE = {
            "const __nekoPyInt = function (v) { return typeof v === 'boolean'"
                    + " || (typeof v === 'number' && Number.isInteger(v)); };",
            "const __nekoPyFloat = function (v) { return typeof v === 'number'; };",
            "const __nekoPyBool = function (v) { return typeof v === 'boolean'; };",
            "const __nekoPyStr = function (v) { return typeof v === 'string'; };",
            "const __nekoPyList = function (v) { return Array.isArray(v); };",
            "const __nekoPyDict = function (v) { return v !== null && typeof v === 'object'"
                    + " && v.constructor === Object; };",
            "const __nekoPySet = function (v) { return v instanceof Set; };",
            "const __nekoPyFrozenset = function (v) { return v instanceof Set; };",
            "const __nekoPyTuple = function (v) { return Array.isArray(v); };",
    };

    /** Built-in type names an isinstance/except type position can reference -> a {@link #TYPE_PRELUDE} probe. */
    private static final java.util.Set<String> BUILTIN_TYPES = java.util.Set.of(
            "int", "float", "str", "bool", "list", "dict", "set", "tuple", "frozenset");

    /**
     * JavaScript reserved words and strict-mode/module-unsafe identifiers. Python allows several of
     * these as binding names (e.g. {@code def f(new):}), so any binding whose name appears here is
     * renamed to a deterministic safe JS identifier by {@link #collectJsRenames}. Property names
     * after {@code .} are intentionally NOT renamed; only variable-like identifiers route through
     * {@link #jsName(String)}.
     */
    private static final java.util.Set<String> JS_RESERVED_IDENTIFIERS = java.util.Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "yield", "let", "static", "implements", "interface", "package", "private", "protected",
            "public", "await", "async", "arguments", "eval", "undefined", "NaN", "Infinity",
            "constructor", "prototype");

    /**
     * Worst offenders for dict method hijacking: these names are both dict methods and common user method
     * names, so they need extra receiver guarding (see {@link #rtDispatch}).
     */
    private static final java.util.Set<String> DICT_METHODS = java.util.Set.of(
            "get", "keys", "values", "items", "update", "pop");

    private final StringBuilder out = new StringBuilder();
    private int indent = 0;
    private int tempCounter = 0;
    private int jsLine = 0;   // 0-based number of the next line to be emitted
    private final List<int[]> mappings = new ArrayList<>();   // {generatedJsLine, originalPythonLine0}
    private final IdentityHashMap<PythonNode, Integer> srcLines;
    private boolean rewriteSelf = false;   // true inside a class method body (self → this)
    private final java.util.Set<String> classNames = new java.util.HashSet<>();   // for `new` on calls
    /** An alias from an assignment of an exception/user class name ({@code VE = ValueError}); raise VE / raise VE(...) needs {@code new} as well. */
    private final java.util.Set<String> excClassAliases = new java.util.HashSet<>();
    /** Top-level names defined by this module (def/class/assign), re-exported so .py files are importable. */
    private final java.util.Set<String> definedNames = new java.util.LinkedHashSet<>();
    /** Python name → safe JS name for bindings whose Python name is a JS reserved/unsafe identifier. */
    private final Map<String, String> jsRenames = new LinkedHashMap<>();
    /** Functions/methods/classes that declare {@code **kwargs} → accept a tagged trailing object at call sites. */
    private final java.util.Set<String> kwFunctions = new java.util.HashSet<>();
    private final java.util.Set<String> kwMethods = new java.util.HashSet<>();
    private final java.util.Set<String> kwClassNames = new java.util.HashSet<>();
    /** True when any f-string format spec / conversion is used → the __nekoFmt helper is emitted. */
    private boolean needsFmt = false;
    /** Switches for the helpers emitted on demand (collected by one AST prescan in {@link #scanHelpers}; see {@link #emitRuntimeHelpers}). */
    private boolean needsTruthy = false;   // if/while/not/and/or/bool/any/all/comprehension filter/ternary/assert/match guard
    private boolean needsMod = false;      // %、%=、divmod
    private boolean needsDiv = false;      // /, //, /=, //=, divmod (divide by zero throws ZeroDivisionError)
    private boolean needsMul = false;      // *、*=
    private boolean needsAdd = false;      // +, += (array/string concatenation and mixed-type TypeError)
    private boolean needsReplace = false;  // str.replace(old, new[, count]) -> literal replacement helper
    private boolean needsStrCount = false; // str.count(sub[, start[, end]]) -> Python-semantics counting helper
    private boolean needsZfill = false;    // str.zfill(width) -> zero-fill helper that keeps the sign in front
    private boolean needsIn = false;       // in / not in
    private boolean needsIter = false;     // for statements / comprehension for clauses
    private boolean needsLen = false;      // len()
    private boolean needsExc = false;      // a built-in exception name is referenced / assert -> exception prelude + __nekoExcIs
    private boolean needsTypes = false;    // an isinstance/except type position references a built-in type name -> type probe prelude
    private boolean needsPyStr = false;    // str()/print()/%-formatting -> __nekoPyStr (Python string spelling)
    /** Python source line of the statement being emitted (1-based, maintained by emitStmt from srcLines), attached to {@link #err}. */
    private int curLine = -1;
    /** The variable bound to the current exception in each enclosing except clause (top = innermost). */
    private final java.util.Deque<String> errStack = new java.util.ArrayDeque<>();
    /** For each enclosing loop: the else-flag var name (or null if the loop has no else). Top = innermost. */
    private final java.util.Deque<String> loopFlags = new java.util.LinkedList<>();

    public PythonEmitter(IdentityHashMap<PythonNode, Integer> srcLines) {
        this.srcLines = srcLines;
    }

    public String emit(PythonNode.Module module) {
        // Pass 1: collect top-level defined names so the module can re-export them (Python modules
        // expose all top-level bindings; this lets sibling .py files `from <this> import <name>`),
        // collect **kwargs-aware functions/methods/classes so call sites can route keyword args,
        // and scan for constructs that need a runtime helper (so each is emitted at most once).
        for (PythonNode stmt : module.body()) {
            collectDefinitions(stmt);
            collectKwAware(stmt);
            scanHelpers(stmt);
        }
        collectJsRenames(module);
        // Pass 2: ESM import declarations must precede all other statements; emit them first, each
        // mapped back to its Python source line. Module specifiers are relative to this file
        // (foo → ./foo, a.b.c → ./a/b/c); NekoModuleResolver probes .py / .js / index.* automatically.
        for (PythonNode stmt : module.body()) {
            Integer stmtLine = srcLines.get(stmt);
            if (stmtLine != null) curLine = stmtLine;   // import-phase errors carry the source line as well
            if (stmt instanceof PythonNode.Import imp) {
                recordMapping(stmt);
                for (PythonNode.Spec s : imp.specs()) line(esmNamespaceImport(s));
            } else if (stmt instanceof PythonNode.ImportFrom impf) {
                // Magic import: `from nekojs import *` (or a named form) is a type-stub entry for IDE/pyright and means
                // nothing at runtime, so it is stripped (no recordMapping, no line, no source-map effect).
                if ("nekojs".equals(impf.module())) continue;
                if (impf.star()) throw err("python 'from X import *' is not supported");
                recordMapping(stmt);
                line(esmNamedImport(impf));
            }
        }
        // Pass 3: runtime helpers (only the ones this module needs), then the remaining statements.
        emitRuntimeHelpers();
        for (PythonNode stmt : module.body()) {
            if (stmt instanceof PythonNode.Import || stmt instanceof PythonNode.ImportFrom) continue;
            emitStmt(stmt);
        }
        // Pass 4: emit the export block. Has no Python source line → no source-map entry. Skipped when
        // the module defines no names (e.g. a bare expression script stays plain JS, not ESM).
        if (!definedNames.isEmpty()) emitExportBlock();
        return out.toString();
    }

    /**
     * Emits the runtime helpers and the exception prelude on demand: each at most once, before every statement
     * (no source-map line, the same injection as {@link #FMT_HELPER}). Switches come from the {@link #scanHelpers} prescan.
     */
    private void emitRuntimeHelpers() {
        if (needsFmt) { for (String l : FMT_HELPER) line0(l); }
        if (needsTruthy) { line0(TRUTHY_HELPER); line0(AND_HELPER); line0(OR_HELPER); }
        if (needsMod || needsPyStr) line0(PYSTR_HELPER);
        if (needsMod) { line0(PERCENT_HELPER); line0(MOD_HELPER); }
        if (needsDiv) { line0(DIV_HELPER); line0(FLOORDIV_HELPER); }
        if (needsMul) line0(MUL_HELPER);
        if (needsAdd) line0(ADD_HELPER);
        if (needsReplace) line0(REPLACE_HELPER);
        if (needsStrCount) line0(STR_COUNT_HELPER);
        if (needsZfill) line0(ZFILL_HELPER);
        if (needsIn) line0(IN_HELPER);
        if (needsIter) line0(ITER_HELPER);
        if (needsLen) line0(LEN_HELPER);
        if (needsTypes) { for (String l : TYPE_PRELUDE) line0(l); }
        // The division/modulo/addition helpers throw new ZeroDivisionError / new TypeError at runtime, so the prelude
        // classes must ship with them (the TypeError from __nekoAdd comes from the prelude, not the native JS TypeError).
        if (needsExc || needsMod || needsDiv || needsAdd) { for (String l : EXC_PRELUDE) line0(l); line0(EXCIS_HELPER); }
    }

    /** (generatedJsLine, originalPythonLine0Based) pairs, one per statement's first emitted line. */
    public List<int[]> mappings() {
        return mappings;
    }

    // ---- statements ----

    private void emitStmt(PythonNode node) {
        // Record a statement-level mapping (the line about to be emitted ← its Python source line).
        if (!(node instanceof PythonNode.Pass)) recordMapping(node);
        // Record the source line of the current statement (err() attaches it); nested statements save and restore it in the recursive emitStmt.
        Integer ln = srcLines.get(node);
        int prevLine = curLine;
        if (ln != null) curLine = ln;
        switch (node) {
            case PythonNode.Module m -> throw err("nested module");
            case PythonNode.FunctionDef f -> {
                if (hasKwargs(f.params())) {
                    // **kwargs → empty signature; a prologue reconstructs binding from `arguments`.
                    line("function" + (f.isGenerator() ? "* " : " ") + jsName(f.name()) + "() {");
                    emitKwPrologue(f.params(), false);
                    block(f.body());
                    line("}");
                } else if (rewriteSelf && !f.isGenerator()) {
                    // Nested def inside a method body: a Python nested function reaches the outer self through the closure,
                    // so it becomes an arrow function (lexical this = the method's this) and the self -> this rewrite reaches
                    // inside it. A plain function would rebind this and make self undefined in the nested body;
                    // generators (an arrow cannot be a generator) and **kwargs (the prologue relies on arguments) stay function.
                    line("var " + jsName(f.name()) + " = (" + emitParams(f.params()) + ") => {");
                    block(f.body());
                    line("};");
                } else {
                    line("function" + (f.isGenerator() ? "* " : " ") + jsName(f.name()) + "(" + emitParams(f.params()) + ") {");
                    block(f.body());
                    line("}");
                }
                applyDecorators(jsName(f.name()), f.decorators());
            }
            case PythonNode.ClassDef c -> emitClass(c);
            case PythonNode.With w -> emitWith(w, 0);
            case PythonNode.Match m -> emitMatch(m);
            case PythonNode.Try t -> {
                boolean hasElse = !t.elseBody().isEmpty();
                boolean hasFinally = !t.finallyBody().isEmpty();
                // else runs only if the try body raised nothing, so track success with a flag; it must
                // also run BEFORE finally and outside the except handlers (an else-body exception is not
                // caught by the same excepts). When both else and finally are present, wrap so finally
                // still runs after the else.
                String elseFlag = hasElse ? ("__nekoOk" + (tempCounter++)) : null;
                if (hasElse) line("var " + elseFlag + " = true;");
                boolean outerWrap = hasElse && hasFinally;
                if (outerWrap) { line("try {"); indent++; }
                line("try {");
                block(t.body());
                List<PythonNode.ExceptClause> excepts = t.excepts();
                if (!excepts.isEmpty()) {
                    boolean typed = excepts.size() > 1 || !excepts.get(0).types().isEmpty();
                    if (!typed) {
                        // bare single except → plain catch (no type check)
                        PythonNode.ExceptClause only = excepts.get(0);
                        String bound = only.name() != null ? jsName(only.name()) : "__nekoErr";
                        line("} catch (" + bound + ") {");
                        if (elseFlag != null) line(elseFlag + " = false;");
                        errStack.push(bound);
                        block(only.body());
                        errStack.pop();
                    } else {
                        // typed excepts → one catch + instanceof chain; unmatched errors rethrow.
                        line("} catch (__nekoErr) {");
                        indent++;
                        if (elseFlag != null) line(elseFlag + " = false;");
                        errStack.push("__nekoErr");
                        boolean isFirst = true;
                        boolean hasBare = false;
                        for (PythonNode.ExceptClause c : excepts) {
                            if (c.types().isEmpty()) {   // bare except — parser enforces it is last
                                hasBare = true;
                                line(isFirst ? "if (true) {" : "} else {");
                            } else {
                                line((isFirst ? "if (" : "} else if (")
                                        + instanceOfCond(c.types(), "__nekoErr") + ") {");
                            }
                            indent++;
                            if (c.name() != null && !c.name().equals("__nekoErr")) {
                                line("var " + jsName(c.name()) + " = __nekoErr;");
                            }
                            block(c.body());
                            indent--;
                            isFirst = false;
                        }
                        if (hasBare) {
                            line("}");
                        } else {
                            line("} else {");
                            indent++;
                            line("throw __nekoErr;");
                            indent--;
                            line("}");
                        }
                        indent--;
                        errStack.pop();
                    }
                }
                if (hasFinally && !outerWrap) {
                    line("} finally {");
                    block(t.finallyBody());
                }
                line("}");
                if (hasElse) {
                    line("if (" + elseFlag + ") {");
                    block(t.elseBody());
                    line("}");
                }
                if (outerWrap) {
                    indent--;
                    line("} finally {");
                    indent++;
                    block(t.finallyBody());
                    indent--;
                    line("}");
                }
            }
            case PythonNode.If i -> writeIf(i, true);
            case PythonNode.For f -> {
                String flag = f.elseBody().isEmpty() ? null : ("__nekoBrk" + (tempCounter++));
                if (flag != null) line("var " + flag + " = true;");   // stays true unless the body `break`s
                // dict/Map are normalized by __nekoIter to key iteration (other iterables pass through unchanged)
                line("for (var " + emitTarget(f.target()) + " of __nekoIter(" + emitExpr(f.iter()) + ")) {");
                loopFlags.push(flag);
                block(f.body());
                loopFlags.pop();
                line("}");
                if (flag != null) {
                    line("if (" + flag + ") {");
                    block(f.elseBody());
                    line("}");
                }
            }
            case PythonNode.While w -> {
                String flag = w.elseBody().isEmpty() ? null : ("__nekoBrk" + (tempCounter++));
                if (flag != null) line("var " + flag + " = true;");
                line("while (__nekoTruthy(" + emitExpr(w.cond()) + ")) {");
                loopFlags.push(flag);
                block(w.body());
                loopFlags.pop();
                line("}");
                if (flag != null) {
                    line("if (" + flag + ") {");
                    block(w.elseBody());
                    line("}");
                }
            }
            case PythonNode.Return r -> line(r.value() == null ? "return;" : "return " + emitExpr(r.value()) + ";");
            case PythonNode.Raise r -> {
                if (r.exc() == null) {
                    if (errStack.isEmpty()) {
                        throw err("python bare 'raise' is only valid inside an except clause");
                    }
                    line("throw " + errStack.peek() + ";");   // re-raise the current exception
                } else if (r.exc() instanceof PythonNode.Name rn && isThrowableClassName(rn.id())) {
                    // raise Cls == raise Cls() (Python semantics): a bare class name (built-in exception, user class, or
                    // exception class alias) must be instantiated before throwing; a user class used to be thrown as the
                    // class object itself, so `except Cls` never matched through instanceof and the crash went uncaught.
                    line("throw new " + jsName(rn.id()) + "();");
                } else {
                    // raise ValueError('x') -> throw new ValueError("x") (the built-in exception classes come from the prelude)
                    line("throw " + emitExpr(r.exc()) + ";");
                }
            }
            case PythonNode.Assert a -> {
                String thrown = a.msg() != null ? emitExpr(a.msg()) : "\"AssertionError\"";
                line("if (!(__nekoTruthy(" + emitExpr(a.cond()) + "))) throw new AssertionError(" + thrown + ");");
            }
            case PythonNode.Del d -> {
                for (PythonNode t : d.targets()) {
                    if (t instanceof PythonNode.Name n) {
                        // `delete x;` is a syntax error in ESM strict mode and JS cannot unbind a var, which is what Python's
                        // del x means, so it is a compile error; only del d[k] / del obj.attr are supported.
                        throw err("python 'del " + n.id()
                                + "' is not supported (JS cannot unbind a name; use del d[key] / del obj.attr)");
                    }
                    line("delete " + emitExpr(t) + ";");
                }
            }
            case PythonNode.Yield y -> line(y.from()
                    ? "yield* " + emitExpr(y.value()) + ";"
                    : "yield " + (y.value() != null ? emitExpr(y.value()) : "") + ";");
            case PythonNode.Break b -> {
                String f = loopFlags.peek();
                if (f != null) line(f + " = false;");   // mark the nearest loop as broken (skips its else)
                line("break;");
            }
            case PythonNode.Continue c -> line("continue;");
            case PythonNode.Pass p -> { /* emit nothing */ }
            case PythonNode.Assign a -> emitAssign(a);
            case PythonNode.AugAssign a -> emitAugAssign(a);
            case PythonNode.ExprStmt e -> line(emitExpr(e.expr()) + ";");
            case PythonNode.Import imp -> throw err(
                    "python 'import' is only supported at module top level");
            case PythonNode.ImportFrom imp -> throw err(
                    "python 'from ... import' is only supported at module top level");
            default -> throw err("unsupported statement: " + node.getClass().getSimpleName());
        }
        curLine = prevLine;
    }

    private void recordMapping(PythonNode node) {
        Integer py = srcLines.get(node);
        if (py != null) mappings.add(new int[]{jsLine, py - 1});
    }

    /** Collects top-level binding names (def/class/assign targets) for the module export block. */
    private void collectDefinitions(PythonNode stmt) {
        switch (stmt) {
            case PythonNode.FunctionDef f -> definedNames.add(f.name());
            case PythonNode.ClassDef c -> {
                definedNames.add(c.name());
                // Register the class name during the prescan (emitClass adds it too; the Set dedupes): raise Cls / Cls(...) need
                // new whether or not a raise/alias assignment is emitted first (a raise in a function body can precede the class definition).
                classNames.add(c.name());
            }
            case PythonNode.Assign a -> {
                for (PythonNode t : a.targets()) collectTargetNames(t);
                collectExcAlias(a);
            }
            default -> { }
        }
    }

    /**
     * Records an exception class alias: for {@code VE = ValueError} (the value being a built-in exception name or
     * a user class name), {@code raise VE} / {@code raise VE('x')} afterwards need {@code new} just like the class
     * name itself; otherwise {@code throw VE("x")} calls a class without new, a runtime TypeError. Only top-level
     * alias assignments are covered (an alias inside a function body is not tracked).
     */
    private void collectExcAlias(PythonNode.Assign a) {
        if (a.value() instanceof PythonNode.Name vn && isThrowableClassName(vn.id())) {
            for (PythonNode t : a.targets()) {
                if (t instanceof PythonNode.Name tn) excClassAliases.add(tn.id());
            }
        }
    }

    /** Whether the raise target is a class name (built-in exception, user class, or exception class alias) and so needs {@code new}. */
    private boolean isThrowableClassName(String id) {
        return BUILTIN_EXCEPTIONS.contains(id) || classNames.contains(id) || excClassAliases.contains(id);
    }

    private void collectTargetNames(PythonNode target) {
        switch (target) {
            case PythonNode.Name n -> definedNames.add(n.id());
            case PythonNode.TupleLit t -> { for (PythonNode e : t.elements()) collectTargetNames(e); }
            default -> { }   // Attribute / Index targets mutate, they don't create new bindings
        }
    }

    /** Returns the JS identifier to emit for a Python name; non-reserved names are unchanged. */
    private String jsName(String pythonName) {
        return jsRenames.getOrDefault(pythonName, pythonName);
    }

    /**
     * Pre-scan that builds {@link #jsRenames}: every binding position whose Python name is a JS
     * reserved/unsafe identifier is renamed to {@code __neko$<name>}; if that candidate collides
     * with any identifier already used in the module (binding or reference), a numeric suffix is
     * appended until it is unique and deterministic.
     */
    private void collectJsRenames(PythonNode.Module module) {
        java.util.Set<String> userNames = new java.util.HashSet<>();
        java.util.List<String> bindingNames = new java.util.ArrayList<>();
        collectUserNames(module, userNames);
        collectBindingNames(module, bindingNames);
        java.util.Set<String> usedRenames = new java.util.HashSet<>();
        for (String name : bindingNames) {
            if (!JS_RESERVED_IDENTIFIERS.contains(name) || jsRenames.containsKey(name)) continue;
            String candidate = "__neko$" + name;
            int counter = 1;
            while (userNames.contains(candidate) || usedRenames.contains(candidate)) {
                candidate = "__neko$" + name + "_" + counter++;
            }
            jsRenames.put(name, candidate);
            usedRenames.add(candidate);
        }
    }

    /** Collects every variable-like identifier used anywhere in the module (for collision avoidance). */
    private void collectUserNames(Object o, java.util.Set<String> userNames) {
        if (o == null) return;
        if (o instanceof PythonNode.Name n) {
            userNames.add(n.id());
            return;
        }
        if (o instanceof PythonNode.FunctionDef f) {
            userNames.add(f.name());
            for (String d : f.decorators()) userNames.add(decoratorRoot(d));
            for (PythonNode.Param p : f.params()) collectUserNames(p, userNames);
            for (PythonNode s : f.body()) collectUserNames(s, userNames);
            return;
        }
        if (o instanceof PythonNode.ClassDef c) {
            userNames.add(c.name());
            for (String d : c.decorators()) userNames.add(decoratorRoot(d));
            collectUserNames(c.base(), userNames);
            for (PythonNode s : c.body()) collectUserNames(s, userNames);
            return;
        }
        if (o instanceof PythonNode.Lambda l) {
            for (PythonNode.Param p : l.params()) collectUserNames(p, userNames);
            collectUserNames(l.body(), userNames);
            return;
        }
        if (o instanceof PythonNode.Walrus w) {
            userNames.add(w.name());
            collectUserNames(w.value(), userNames);
            return;
        }
        if (o instanceof PythonNode.Param p) {
            userNames.add(p.name());
            collectUserNames(p.defaultValue(), userNames);
            return;
        }
        if (o instanceof PythonNode.Spec s) {
            // import a.b.c → local binding is the leaf; from m import x [as y] → x or y.
            userNames.add(s.alias() != null ? s.alias() : lastSegment(s.name()));
            return;
        }
        if (o instanceof PythonNode.ExceptClause ec) {
            if (ec.name() != null) userNames.add(ec.name());
            for (PythonNode t : ec.types()) collectUserNames(t, userNames);
            for (PythonNode s : ec.body()) collectUserNames(s, userNames);
            return;
        }
        if (o instanceof PythonNode.WithItem wi) {
            collectUserNames(wi.context(), userNames);
            collectUserNames(wi.target(), userNames);
            return;
        }
        if (o instanceof PythonNode.ForComp fc) {
            collectUserNames(fc.target(), userNames);
            collectUserNames(fc.iter(), userNames);
            return;
        }
        if (o instanceof PythonNode.IfComp ic) {
            collectUserNames(ic.cond(), userNames);
            return;
        }
        if (o instanceof PythonNode.MatchCase mc) {
            collectPatternUserNames(mc.pattern(), userNames);
            collectUserNames(mc.guard(), userNames);
            for (PythonNode s : mc.body()) collectUserNames(s, userNames);
            return;
        }
        if (o instanceof PythonNode.Pattern p) {
            collectPatternUserNames(p, userNames);
            return;
        }
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            for (var rc : c.getRecordComponents()) {
                try {
                    collectUserNames(rc.getAccessor().invoke(o), userNames);
                } catch (ReflectiveOperationException ignored) { }
            }
            return;
        }
        if (o instanceof java.util.List<?> list) {
            for (Object e : list) collectUserNames(e, userNames);
        }
    }

    private void collectPatternUserNames(PythonNode.Pattern p, java.util.Set<String> userNames) {
        if (p instanceof PythonNode.LiteralPat lp) {
            collectUserNames(lp.value(), userNames);
        } else if (p instanceof PythonNode.CapturePat cp) {
            if (!"_".equals(cp.name())) userNames.add(cp.name());
        } else if (p instanceof PythonNode.OrPat op) {
            for (PythonNode.Pattern alt : op.alts()) collectPatternUserNames(alt, userNames);
        } else if (p instanceof PythonNode.SequencePat sp) {
            if (sp.starName() != null) userNames.add(sp.starName());
            for (PythonNode.Pattern e : sp.elements()) collectPatternUserNames(e, userNames);
        } else if (p instanceof PythonNode.MappingPat mp) {
            for (PythonNode k : mp.keys()) collectUserNames(k, userNames);
            for (String vn : mp.valueNames()) userNames.add(vn);
            if (mp.restName() != null) userNames.add(mp.restName());
        } else if (p instanceof PythonNode.ClassPat cp) {
            userNames.add(cp.className());
            for (PythonNode.Pattern e : cp.keyword().values()) collectPatternUserNames(e, userNames);
        }
    }

    /** Registers every binding position in source order (only names that are reserved/unsafe get mapped). */
    private void collectBindingNames(Object o, java.util.List<String> bindingNames) {
        if (o == null) return;
        if (o instanceof PythonNode.FunctionDef f) {
            bindingNames.add(f.name());
            for (PythonNode.Param p : f.params()) collectBindingNames(p, bindingNames);
            for (PythonNode s : f.body()) collectBindingNames(s, bindingNames);
            return;
        }
        if (o instanceof PythonNode.ClassDef c) {
            bindingNames.add(c.name());
            collectBindingNames(c.base(), bindingNames);
            // Class-body members are PROPERTY positions (static fields / methods), not variable
            // bindings. `static new = 5;` and `new() {}` are valid JS class-body syntax, and
            // C.new / self.new / C().new() access them as raw property names. Only walk inside
            // method params/bodies and field value expressions, where real bindings may live.
            for (PythonNode s : c.body()) collectClassBodyBindingNames(s, bindingNames);
            return;
        }
        if (o instanceof PythonNode.Lambda l) {
            for (PythonNode.Param p : l.params()) collectBindingNames(p, bindingNames);
            collectBindingNames(l.body(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.Param p) {
            bindingNames.add(p.name());
            collectBindingNames(p.defaultValue(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.Assign a) {
            for (PythonNode t : a.targets()) collectTargetBindingNames(t, bindingNames);
            collectBindingNames(a.value(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.For f) {
            collectTargetBindingNames(f.target(), bindingNames);
            collectBindingNames(f.iter(), bindingNames);
            for (PythonNode s : f.body()) collectBindingNames(s, bindingNames);
            for (PythonNode s : f.elseBody()) collectBindingNames(s, bindingNames);
            return;
        }
        if (o instanceof PythonNode.Walrus w) {
            bindingNames.add(w.name());
            collectBindingNames(w.value(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.Import imp) {
            for (PythonNode.Spec s : imp.specs()) {
                bindingNames.add(s.alias() != null ? s.alias() : lastSegment(s.name()));
            }
            return;
        }
        if (o instanceof PythonNode.ImportFrom impf) {
            for (PythonNode.Spec s : impf.specs()) {
                bindingNames.add(s.alias() != null ? s.alias() : s.name());
            }
            return;
        }
        if (o instanceof PythonNode.ExceptClause ec) {
            if (ec.name() != null) bindingNames.add(ec.name());
            for (PythonNode t : ec.types()) collectBindingNames(t, bindingNames);
            for (PythonNode s : ec.body()) collectBindingNames(s, bindingNames);
            return;
        }
        if (o instanceof PythonNode.WithItem wi) {
            collectTargetBindingNames(wi.target(), bindingNames);
            collectBindingNames(wi.context(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.ForComp fc) {
            collectTargetBindingNames(fc.target(), bindingNames);
            collectBindingNames(fc.iter(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.IfComp ic) {
            collectBindingNames(ic.cond(), bindingNames);
            return;
        }
        if (o instanceof PythonNode.MatchCase mc) {
            collectPatternBindingNames(mc.pattern(), bindingNames);
            collectBindingNames(mc.guard(), bindingNames);
            for (PythonNode s : mc.body()) collectBindingNames(s, bindingNames);
            return;
        }
        if (o instanceof PythonNode.Pattern p) {
            collectPatternBindingNames(p, bindingNames);
            return;
        }
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            for (var rc : c.getRecordComponents()) {
                try {
                    collectBindingNames(rc.getAccessor().invoke(o), bindingNames);
                } catch (ReflectiveOperationException ignored) { }
            }
            return;
        }
        if (o instanceof java.util.List<?> list) {
            for (Object e : list) collectBindingNames(e, bindingNames);
        }
    }

    /**
     * Class-body members are property definitions, not variable bindings: the member NAME itself
     * (a static field or method name) must never enter {@code jsRenames}. This walk only descends
     * into the executable parts — method parameters/bodies and field value expressions — where
     * ordinary binding positions (params, walrus, lambdas, etc.) still need reserved-name renames.
     */
    private void collectClassBodyBindingNames(Object o, java.util.List<String> bindingNames) {
        if (o instanceof PythonNode.FunctionDef f) {
            for (PythonNode.Param p : f.params()) collectBindingNames(p, bindingNames);
            for (PythonNode s : f.body()) collectBindingNames(s, bindingNames);
        } else if (o instanceof PythonNode.Assign a) {
            collectBindingNames(a.value(), bindingNames);
        } else {
            collectBindingNames(o, bindingNames);
        }
    }

    private void collectTargetBindingNames(PythonNode target, java.util.List<String> bindingNames) {
        if (target instanceof PythonNode.Name n) {
            bindingNames.add(n.id());
        } else if (target instanceof PythonNode.TupleLit t) {
            for (PythonNode e : t.elements()) collectTargetBindingNames(e, bindingNames);
        } else if (target != null) {
            collectBindingNames(target, bindingNames);   // Attribute/Index have no binding, but may contain expressions
        }
    }

    private void collectPatternBindingNames(PythonNode.Pattern p, java.util.List<String> bindingNames) {
        if (p instanceof PythonNode.LiteralPat lp) {
            collectBindingNames(lp.value(), bindingNames);
        } else if (p instanceof PythonNode.CapturePat cp) {
            if (!"_".equals(cp.name())) bindingNames.add(cp.name());
        } else if (p instanceof PythonNode.OrPat op) {
            // Python OR patterns must not bind names; emitMatchCond likewise discards their binds.
        } else if (p instanceof PythonNode.SequencePat sp) {
            if (sp.starName() != null) bindingNames.add(sp.starName());
            for (PythonNode.Pattern e : sp.elements()) collectPatternBindingNames(e, bindingNames);
        } else if (p instanceof PythonNode.MappingPat mp) {
            for (String vn : mp.valueNames()) bindingNames.add(vn);
            if (mp.restName() != null) bindingNames.add(mp.restName());
        } else if (p instanceof PythonNode.ClassPat cp) {
            for (PythonNode.Pattern e : cp.keyword().values()) collectPatternBindingNames(e, bindingNames);
        }
    }

    /**
     * One pre-scan walk (same reflection traversal as the old {@code containsFormatted}) that sets
     * every on-demand runtime-helper flag: a {@code Formatted} f-string field or {@code format(x, s)}
     * needs {@code __nekoFmt}; conditions/boolean operators need Python truthiness; {@code %}/{@code *}
     * need the modulo/repetition helpers; {@code in} needs membership; for-statements and comprehension
     * {@code for} clauses need iterable normalization; {@code len()} needs the len helper; any builtin
     * exception name reference (or an {@code assert}, which raises AssertionError) needs the exception
     * prelude; {@code filter} needs Python truthiness for its predicate; {@code /}//{@code /=}//
     * {@code //=}/{@code divmod} need the zero-checked division helpers (which also pull in the
     * exception prelude for ZeroDivisionError).
     */
    private void scanHelpers(Object o) {
        if (o == null) return;
        if (o instanceof PythonNode.Formatted) {
            needsFmt = true;
        } else if (o instanceof PythonNode.Name n) {
            if (BUILTIN_EXCEPTIONS.contains(n.id())) needsExc = true;
        } else if (o instanceof PythonNode.Call call && call.func() instanceof PythonNode.Name fn) {
            String id = fn.id();
            if ("format".equals(id) && call.args().size() == 2) needsFmt = true;
            if ("bool".equals(id) || "any".equals(id) || "all".equals(id) || "filter".equals(id)) needsTruthy = true;
            if ("len".equals(id)) needsLen = true;
            if ("divmod".equals(id)) { needsMod = true; needsDiv = true; }
            // str()/print(): go through __nekoPyStr (the Python spelling of None/True/False and arrays)
            if ("str".equals(id) || "print".equals(id)) needsPyStr = true;
            // isinstance(x, int/str/...) or isinstance(x, (A, str)): a type position references a built-in type name -> probe prelude
            if ("isinstance".equals(id)) {
                for (PythonNode a : call.args()) if (refsBuiltinType(a)) needsTypes = true;
            }
        } else if (o instanceof PythonNode.Call call && call.func() instanceof PythonNode.Attribute attr) {
            // Method names map one-to-one onto the Python str semantics helpers; this prescan is conservative and unrelated to receiver hijack detection.
            switch (attr.attr()) {
                case "replace" -> needsReplace = true;
                case "count" -> needsStrCount = true;
                case "zfill" -> needsZfill = true;
                default -> { }
            }
        } else if (o instanceof PythonNode.ExceptClause ec) {
            // A type position such as except (ValueError, str) can reference built-in type names too (the same route as isinstance)
            for (PythonNode t : ec.types()) if (refsBuiltinType(t)) needsTypes = true;
        } else if (o instanceof PythonNode.Assert) {
            needsTruthy = true;   // assert tests its condition by Python truthiness
            needsExc = true;      // an assert failure -> new AssertionError(...)
        } else if (o instanceof PythonNode.If || o instanceof PythonNode.While || o instanceof PythonNode.Ternary
                || o instanceof PythonNode.IfComp) {
            needsTruthy = true;
        } else if (o instanceof PythonNode.Unary u && "not".equals(u.op())) {
            needsTruthy = true;
        } else if (o instanceof PythonNode.Binary b) {
            switch (b.op()) {
                case "and", "or" -> needsTruthy = true;
                case "%" -> needsMod = true;
                case "+" -> needsAdd = true;   // array/string concatenation and mixed-type TypeError -> __nekoAdd
                case "*" -> needsMul = true;
                case "/", "//" -> needsDiv = true;   // division by zero must throw ZeroDivisionError -> __nekoDiv/__nekoFloorDiv
                default -> { }
            }
        } else if (o instanceof PythonNode.AugAssign ag) {
            if ("%=".equals(ag.op())) needsMod = true;
            else if ("*=".equals(ag.op())) needsMul = true;
            else if ("+=".equals(ag.op())) needsAdd = true;
            else if ("/=".equals(ag.op()) || "//=".equals(ag.op())) needsDiv = true;
        } else if (o instanceof PythonNode.Compare cmp && ("in".equals(cmp.op()) || "not in".equals(cmp.op()))) {
            needsIn = true;
        } else if (o instanceof PythonNode.For || o instanceof PythonNode.ForComp) {
            needsIter = true;
        } else if (o instanceof PythonNode.Match mt) {
            // A case guard on match is tested with Python truthiness
            for (var mc : mt.cases()) if (mc.guard() != null) needsTruthy = true;
        }
        // Walk into record components and list elements to keep collecting flags, independently of the
        // branch checks above.
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            for (var rc : c.getRecordComponents()) {
                try {
                    scanHelpers(rc.getAccessor().invoke(o));
                } catch (ReflectiveOperationException ignored) { }
            }
            return;
        }
        if (o instanceof java.util.List<?> list) {
            for (var e : list) scanHelpers(e);
        }
    }

    /**
     * Tells whether a type expression (the second argument of isinstance or an element of an except
     * type tuple) references a built-in type name (int/float/str/...), which requires emitting the
     * {@link #TYPE_PRELUDE} probes. Only type positions count, so an ordinary {@code int('42')} call or
     * a variable with such a name does not trigger it.
     */
    private static boolean refsBuiltinType(PythonNode n) {
        if (n instanceof PythonNode.Name nm) return BUILTIN_TYPES.contains(nm.id());
        if (n instanceof PythonNode.TupleLit t) {
            for (PythonNode e : t.elements()) if (refsBuiltinType(e)) return true;
        }
        if (n instanceof PythonNode.ListLit l) {
            for (PythonNode e : l.elements()) if (refsBuiltinType(e)) return true;
        }
        return false;
    }

    /** True if a parameter list declares {@code **kwargs} (the only trigger for the kw-aware lowering). */
    private static boolean hasKwargs(List<Param> params) {
        for (Param p : params) if (p.kwDict()) return true;
        return false;
    }

    /** Pre-pass: record every function/method/class that declares {@code **kwargs} (for call-site routing). */
    private void collectKwAware(PythonNode node) {
        switch (node) {
            case PythonNode.Module m -> { for (PythonNode s : m.body()) collectKwAware(s); }
            case PythonNode.FunctionDef f -> {
                if (hasKwargs(f.params())) kwFunctions.add(f.name());
                for (PythonNode s : f.body()) collectKwAware(s);   // nested defs
            }
            case PythonNode.ClassDef c -> {
                for (PythonNode member : c.body()) {
                    if (member instanceof PythonNode.FunctionDef m) {
                        if (hasKwargs(m.params())) {
                            kwMethods.add(m.name());
                            if ("__init__".equals(m.name())) kwClassNames.add(c.name());
                        }
                        collectKwAware(m);
                    } else collectKwAware(member);
                }
            }
            case PythonNode.If i -> { for (PythonNode s : i.thenBody()) collectKwAware(s); for (PythonNode s : i.elseBody()) collectKwAware(s); }
            case PythonNode.For f -> { for (PythonNode s : f.body()) collectKwAware(s); }
            case PythonNode.While w -> { for (PythonNode s : w.body()) collectKwAware(s); }
            case PythonNode.Try t -> {
                for (PythonNode s : t.body()) collectKwAware(s);
                for (var ex : t.excepts()) for (PythonNode s : ex.body()) collectKwAware(s);
                for (PythonNode s : t.finallyBody()) collectKwAware(s);
            }
            case PythonNode.With w -> { for (PythonNode s : w.body()) collectKwAware(s); }
            default -> { }
        }
    }

    /**
     * Emits the prologue that reconstructs Python parameter binding from a JS {@code arguments}
     * object, used only by functions/methods that declare {@code **kwargs}. Call sites pass keyword
     * args as a single tagged trailing object {@code { name: value, ..., __nekoKw: true }}; this
     * prologue separates it from positional args, binds each named positional param (positional
     * beats keyword beats default), collects {@code *args}, and gathers the remaining keyword args
     * into the {@code **kwargs} dict (excluding names that bound to a positional param).
     *
     * @param skipFirst drop the leading positional param (the implicit {@code self}/{@code cls} of a method)
     */
    private void emitKwPrologue(List<Param> params, boolean skipFirst) {
        List<Param> positional = new ArrayList<>();
        Param starParam = null;
        Param kwParam = null;
        for (Param p : params) {
            if (p.kwDict()) kwParam = p;
            else if (p.starArg()) starParam = p;
            else positional.add(p);
        }
        int start = skipFirst ? 1 : 0;
        line("var __nekoLast = arguments.length > 0 ? arguments[arguments.length - 1] : undefined;");
        line("var __nekoHasKw = (typeof __nekoLast === \"object\" && __nekoLast !== null && __nekoLast.__nekoKw === true);");
        line("var __kw = __nekoHasKw ? __nekoLast : {};");
        line("var __posCount = arguments.length - (__nekoHasKw ? 1 : 0);");
        for (int i = start; i < positional.size(); i++) {
            Param p = positional.get(i);
            String def = p.defaultValue() != null ? emitExpr(p.defaultValue()) : "undefined";
            String pick = "(__posCount > " + (i - start) + ") ? arguments[" + (i - start) + "]"
                    + " : (\"" + p.name() + "\" in __kw ? __kw[\"" + p.name() + "\"] : " + def + ")";
            line("var " + jsName(p.name()) + " = " + pick + ";");
        }
        if (starParam != null) {
            line("var " + jsName(starParam.name()) + " = [];");
            line("for (var __i = " + (positional.size() - start) + "; __i < __posCount; __i++) "
                    + jsName(starParam.name()) + ".push(arguments[__i]);");
        }
        if (kwParam != null) {
            StringBuilder excl = new StringBuilder(" __nekoK !== \"__nekoKw\"");
            for (int i = start; i < positional.size(); i++) excl.append(" && __nekoK !== \"").append(positional.get(i).name()).append("\"");
            line("var " + jsName(kwParam.name()) + " = {};");
            line("for (var __nekoK in __kw) { if (" + excl + ") " + jsName(kwParam.name()) + "[__nekoK] = __kw[__nekoK]; }");
        }
    }

    /** Builds the tagged trailing object carrying keyword args (+ any {@code **} spreads) at a call site. */
    private String kwObjectLiteral(Map<String, PythonNode> kwargs, List<PythonNode> kwSpreads) {
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (PythonNode spread : kwSpreads) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("...").append(emitExpr(spread));
        }
        for (var e : kwargs.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append(": ").append(emitExpr(e.getValue()));
        }
        return sb.append(", __nekoKw: true }").toString();
    }

    /**
     * Builds the ESM import for one {@code import m[.sub][ as alias]} spec. Dots map to path
     * separators ({@code a.b.c} → {@code ./a/b/c}); the local binding is the alias or the leaf
     * segment, so {@code import utils} exposes a namespace accessed as {@code utils.x}.
     */
    private String esmNamespaceImport(PythonNode.Spec s) {
        String local = s.alias() != null ? s.alias() : lastSegment(s.name());
        return "import * as " + jsName(local) + " from '" + moduleSpecifier(s.name()) + "';";
    }

    /** Builds the ESM named import for {@code from m[.sub] import a [as x], b}. */
    private String esmNamedImport(PythonNode.ImportFrom imp) {
        StringBuilder sb = new StringBuilder("import { ");
        List<PythonNode.Spec> specs = imp.specs();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode.Spec s = specs.get(i);
            String imported = s.name();
            String local = s.alias() != null ? jsName(s.alias()) : jsName(s.name());
            if (s.alias() != null) {
                sb.append(imported).append(" as ").append(local);
            } else if (!imported.equals(local)) {
                sb.append(imported).append(" as ").append(local);
            } else {
                sb.append(imported);
            }
        }
        return sb.append(" } from '").append(moduleSpecifier(imp.module())).append("';").toString();
    }

    /** {@code foo} → {@code './foo'}; {@code a.b.c} → {@code './a/b/c'} (sibling-file / package path). */
    private static String moduleSpecifier(String dotted) {
        return "./" + dotted.replace('.', '/');
    }

    private void emitExportBlock() {
        StringBuilder sb = new StringBuilder("export { ");
        int i = 0;
        for (String n : definedNames) {
            if (i++ > 0) sb.append(", ");
            sb.append(jsName(n));
        }
        line(sb.append(" };").toString());
    }

    /**
     * Applies Python decorators as post-definition rewrites: {@code @a / @b / def f} becomes
     * {@code f = b(f); f = a(f);} (nearest decorator applied first), yielding {@code f = a(b(f))}.
     * Class decorators apply the same way after the class body.
     */
    private void applyDecorators(String name, List<String> decorators) {
        for (int i = decorators.size() - 1; i >= 0; i--) {
            line(name + " = " + decoratorRef(decorators.get(i)) + "(" + name + ");");
        }
    }

    /** A decorator is a dotted identifier; only the first segment is a variable reference (property names stay raw). */
    private String decoratorRef(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? jsName(dotted) : jsName(dotted.substring(0, dot)) + dotted.substring(dot);
    }

    /** First identifier segment of a dotted decorator (e.g. {@code module.deco} → {@code module}). */
    private static String decoratorRoot(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    private void emitAssign(PythonNode.Assign a) {
        String value = emitExpr(a.value());
        if (a.targets().size() == 1) {
            line(emitAssignTarget(a.targets().get(0), value));
        } else {
            // a = b = v  →  evaluate once into a temp, then assign each target
            String tmp = "__neko_py_t" + (tempCounter++);
            line("var " + tmp + " = " + value + ";");
            for (PythonNode t : a.targets()) line(emitAssignTarget(t, tmp));
        }
    }

    /** Returns a full assignment statement (with trailing {@code ;}) for one target. */
    private String emitAssignTarget(PythonNode target, String value) {
        return switch (target) {
            case PythonNode.Name n -> "var " + jsName(n.id()) + " = " + value + ";";
            case PythonNode.TupleLit t -> "var " + emitTarget(t) + " = " + value + ";";
            default -> emitExpr(target) + " = " + value + ";";   // Attribute / Index
        };
    }

    private void emitAugAssign(PythonNode.AugAssign a) {
        String target = emitExpr(a.target());
        String value = emitExpr(a.value());
        String op = a.op();
        if (op.equals("//=")) {
            // Floor division assignment: division by zero must throw ZeroDivisionError, so it goes through the helper
            line(target + " = __nekoFloorDiv(" + target + ", " + value + ");");
        } else if (op.equals("/=")) {
            // True division assignment: it also goes through the helper, which checks for division by
            // zero (same value semantics as a / b)
            line(target + " = __nekoDiv(" + target + ", " + value + ");");
        } else if (op.equals("%=")) {
            // Python modulo takes its sign from the divisor, so it goes through the helper (JS %= takes
            // it from the dividend)
            line(target + " = __nekoMod(" + target + ", " + value + ");");
        } else if (op.equals("*=")) {
            // Sequence repetition ([0] *= 4, 'ab' *= 3) or numeric multiplication, so it goes through the helper
            line(target + " = __nekoMul(" + target + ", " + value + ");");
        } else if (op.equals("+=")) {
            // Container concatenation (xs += ys) or numeric/string addition, so it uses the same helper
            // as binary + (JS += would also silently concatenate)
            line(target + " = __nekoAdd(" + target + ", " + value + ");");
        } else if (op.equals("@=")) {
            throw err("python matmul '@=' is not supported");
        } else {
            // += -= **= &= |= ^= <<= >>=  — all valid JS augmented operators
            line(target + " " + op + " " + value + ";");
        }
    }

    private void emitClass(PythonNode.ClassDef c) {
        classNames.add(c.name());   // track so calls like Counter(10) emit `new Counter(10)`
        StringBuilder header = new StringBuilder("class ").append(jsName(c.name()));
        if (c.base() != null) header.append(" extends ").append(emitExpr(c.base()));
        header.append(" {");
        line(header.toString());
        indent++;
        for (PythonNode member : c.body()) {
            if (member instanceof PythonNode.FunctionDef m) emitMethod(m);
            else if (member instanceof PythonNode.Assign a) emitClassField(a);
            else if (member instanceof PythonNode.ExprStmt e && e.expr() instanceof PythonNode.StrLit doc)
                emitClassDocstring(e, doc);
            else emitStmt(member);
        }
        indent--;
        line("}");
        applyDecorators(jsName(c.name()), c.decorators());
    }

    /**
     * A plain assignment in a class body ({@code class C:\n    x = 5}) defines a class attribute in
     * Python. A JS class body accepts no {@code var} or plain statement (that is a syntax error), so
     * the assignment becomes an ES2022 static class field {@code static x = 5;} and is read through
     * {@code C.x}, which matches Python class attribute semantics. Complex forms such as multiple
     * assignment or tuple unpacking are not supported yet and fail at compile time, like the other
     * unsupported features in a file.
     */
    private void emitClassField(PythonNode.Assign a) {
        if (a.targets().size() != 1 || !(a.targets().get(0) instanceof PythonNode.Name n)) {
            throw err(
                    "python class-body assignments support only a single name target");
        }
        recordMapping(a);
        // Class-body fields are PROPERTY positions, not variable bindings, so the raw Python name
        // must be preserved. Reserved/unsafe names are emitted as computed property names so the
        // JS remains valid (`["new"] = 5;`); non-reserved names keep the plain form byte-for-byte.
        String fieldName = JS_RESERVED_IDENTIFIERS.contains(n.id())
                ? "[" + jsString(n.id()) + "]"
                : n.id();
        // An instance field, not static: Python class attributes are read and written through self
        // (self.total += 1 is instance semantics), and a static field is invisible to this.total,
        // which used to leave every self access undefined or NaN.
        // Direct ClassName.attr access (a class-level read in Python) is no longer supported; scripts use self.
        line(fieldName + " = " + emitExpr(a.value()) + ";");
    }

    /**
     * A class docstring (a bare string statement in the class body) becomes a one-line comment, because
     * a JS class body accepts no bare expression statement and it has no runtime effect in Python either.
     * The source map still maps that statement line.
     */
    private void emitClassDocstring(PythonNode.ExprStmt e, PythonNode.StrLit doc) {
        recordMapping(e);
        line("// docstring: " + doc.value().replace('\n', ' ').replace('\r', ' '));
    }

    private void emitMethod(PythonNode.FunctionDef m) {
        List<String> decos = m.decorators();
        boolean isStatic = decos.contains("staticmethod");
        boolean isClass = decos.contains("classmethod");
        boolean isProp = decos.contains("property");
        for (String d : decos) {
            if (!d.equals("staticmethod") && !d.equals("classmethod") && !d.equals("property")) {
                throw err(
                        "python method decorators other than @staticmethod/@classmethod/@property are not supported (got @" + d + ")");
            }
        }
        boolean prev = rewriteSelf;
        rewriteSelf = !isStatic && !isClass;   // instance/property methods rewrite self → this
        String star = m.isGenerator() ? "*" : "";   // generator method: *name(...)
        String name = jsMethodName(m.name());
        // Reserved method names are legal as raw property names (e.g. `new() {}`), EXCEPT
        // `constructor` and `prototype`: raw forms have special JS class-body meanings. Emit
        // computed names for those two so they remain normal callable methods.
        if ("constructor".equals(m.name()) || "prototype".equals(m.name())) {
            name = "[" + jsString(name) + "]";
        }
        String prefix = isProp ? "get " : (isStatic || isClass) ? "static " : "";
        String params = isStatic ? emitParams(m.params()) : dropFirstParam(m.params());
        if (hasKwargs(m.params())) {
            line(prefix + star + name + "() {");
            if (isClass) line("var cls = this;");   // classmethod: cls is the called constructor
            emitKwPrologue(m.params(), !isStatic);   // instance/class/property drop the leading self/cls
        } else {
            line(prefix + star + name + "(" + params + ") {");
            if (isClass) line("var cls = this;");
        }
        block(m.body());
        line("}");
        rewriteSelf = prev;
    }

    /** Drops the leading self/cls parameter of an instance method. */
    private String dropFirstParam(List<Param> params) {
        if (params.isEmpty()) return "";
        return emitParams(params.subList(1, params.size()));
    }

    private static String jsMethodName(String name) {
        return switch (name) {
            case "__init__" -> "constructor";
            case "__str__" -> "toString";
            default -> name;   // snake_case etc. preserved (valid JS method names)
        };
    }

    /**
     * Python builtin exception names → prelude classes (see {@link #EXC_PRELUDE}), so
     * {@code raise ValueError('x')} / {@code except ValueError} work end to end with the right type.
     * The {@code Exception} root maps to plain {@code Error} (prelude exceptions all extend Error, and
     * this keeps catching JS-native errors working); other builtin names match via
     * {@code __nekoExcIs(e, T)} which also accepts JS-native errors by {@code .name}.
     */
    private static final java.util.Set<String> BUILTIN_EXCEPTIONS = java.util.Set.of(
            "Exception", "ValueError", "TypeError", "KeyError", "IndexError", "RuntimeError",
            "AttributeError", "NameError", "ZeroDivisionError", "ArithmeticError", "LookupError",
            "AssertionError", "OverflowError", "NotImplementedError", "StopIteration", "ImportError",
            "OSError", "EOFError", "MemoryError", "RecursionError");

    /** One disjunct of an except/isinstance type check over {@code varName}. */
    private String instanceOfCond(List<PythonNode> types, String varName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(exceptionCond(varName, types.get(i)));
        }
        return sb.toString();
    }

    /**
     * Builds one exception or type match condition. The {@code Exception} root becomes
     * {@code instanceof Error}, which covers both built-in and native JS errors. Any other built-in
     * exception name becomes {@code __nekoExcIs(e, T)}, which checks the prelude class and falls back
     * to the name of a native JS Error, and a built-in type name (int/str/list/...) becomes a
     * {@link #TYPE_PRELUDE} probe, because emitting {@code e instanceof str} would throw a
     * ReferenceError as JS has no such global. A user class uses plain {@code instanceof}. An except
     * clause and the (tuple) type chain of isinstance share this routing, so a mixed tuple
     * {@code isinstance(e, (ValueError, str))} naturally becomes
     * {@code __nekoExcIs(e, ValueError) || __nekoPyStr(e)}.
     */
    private String exceptionCond(String varName, PythonNode type) {
        if (type instanceof PythonNode.Name n) {
            if ("Exception".equals(n.id())) return "(" + varName + " instanceof Error)";
            if (BUILTIN_EXCEPTIONS.contains(n.id())) return "__nekoExcIs(" + varName + ", " + n.id() + ")";
            if (BUILTIN_TYPES.contains(n.id())) {
                String probe = "__nekoPy" + Character.toUpperCase(n.id().charAt(0)) + n.id().substring(1);
                return probe + "(" + varName + ")";
            }
        }
        return "(" + varName + " instanceof " + emitExpr(type) + ")";
    }

    /**
     * Lowers {@code isinstance(x, T)} / {@code isinstance(x, (T1, T2))} / {@code isinstance(x, [T1, T2])}
     * to a chain of {@link #exceptionCond} disjuncts over {@code valueExpr}, so builtin exception
     * names follow the same matching rules as except clauses.
     */
    private String instanceOfTypeCheck(PythonNode type, String valueExpr) {
        List<PythonNode> types = switch (type) {
            case PythonNode.TupleLit tl -> tl.elements();
            case PythonNode.ListLit ll -> ll.elements();
            default -> List.of(type);
        };
        if (types.isEmpty()) return "false";   // isinstance(x, ()) is always False in Python
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(exceptionCond(valueExpr, types.get(i)));
        }
        return sb.append(")").toString();
    }

    private static String lastSegment(String dotted) {
        int idx = dotted.lastIndexOf('.');
        return idx < 0 ? dotted : dotted.substring(idx + 1);
    }

    /** If {@code idx} is a negative numeric literal, returns its magnitude; otherwise -1. */
    private static long negativeLiteralMagnitude(PythonNode idx) {
        if (idx instanceof PythonNode.IntLit lit && lit.value() < 0) return -lit.value();
        if (idx instanceof PythonNode.Unary u && "-".equals(u.op())
                && u.operand() instanceof PythonNode.IntLit ol && ol.value() >= 0) {
            return ol.value();
        }
        return -1;
    }

    /**
     * Emits a slice subscript. No-step slices use the fast {@code .slice(lo, hi)} path; any step
     * (including {@code [::-1]}) lowers to a Python-faithful helper that honours negative indices,
     * runtime-determined step sign, and the per-sign {@code None} defaults (start=0/stop=length for
     * positive step; start=length-1/stop=-1 sentinel for negative step). Strings join back to a
     * string; everything else returns an array.
     */
    private String emitSlice(String obj, PythonNode.Slice s) {
        if (s.step() == null) {
            String lo = s.lower() != null ? emitExpr(s.lower()) : "0";
            return s.upper() != null ? obj + ".slice(" + lo + ", " + emitExpr(s.upper()) + ")"
                    : obj + ".slice(" + lo + ")";
        }
        String lo = s.lower() != null ? emitExpr(s.lower()) : "null";
        String hi = s.upper() != null ? emitExpr(s.upper()) : "null";
        String step = emitExpr(s.step());
        return "((function (__s) {\n"
                + "  var __n = __s.length, __step = " + step + ";\n"
                + "  if (__step === 0) throw new Error(\"slice step cannot be zero\");\n"
                + "  var __lo = " + lo + ", __hi = " + hi + ", __r = [];\n"
                + "  if (__step > 0) {\n"
                + "    var __start = (__lo === null) ? 0 : __lo, __stop = (__hi === null) ? __n : __hi;\n"
                + "    if (__start < 0) { __start += __n; if (__start < 0) __start = 0; } if (__start > __n) __start = __n;\n"
                + "    if (__stop < 0) { __stop += __n; if (__stop < 0) __stop = 0; } if (__stop > __n) __stop = __n;\n"
                + "    for (var __i = __start; __i < __stop; __i += __step) __r.push(__s[__i]);\n"
                + "  } else {\n"
                + "    var __start = (__lo === null) ? (__n - 1) : __lo, __stop = (__hi === null) ? -1 : __hi;\n"
                + "    if (__start < 0) { __start += __n; if (__start < 0) __start = -1; } if (__start >= __n) __start = __n - 1;\n"
                + "    if (__hi !== null) { if (__stop < 0) { __stop += __n; if (__stop < 0) __stop = -1; } if (__stop >= __n) __stop = __n - 1; }\n"
                + "    for (var __i = __start; __i > __stop; __i += __step) __r.push(__s[__i]);\n"
                + "  }\n"
                + "  return (typeof __s === \"string\") ? __r.join(\"\") : __r;\n"
                + "})(" + obj + "))";
    }

    /**
     * Maps common Python str/list/dict/set method calls to JS idioms. Returns null for unmapped
     * methods — and now also for arity mismatches — so those fall through to a verbatim
     * {@code obj.method(args)} call (a loud runtime TypeError instead of silently applying the wrong
     * mapping). The worst-offender dict method names ({@link #DICT_METHODS}) are additionally
     * hijack-guarded: a user class defining {@code def keys(self)} keeps its own method (see
     * {@link #rtDispatch}); likewise list names JS natives own (sort/pop/copy/insert/remove, see
     * {@link #rtArrayDispatch}) and str names JS natives lack (index/count, via rtDispatch) —
     * a bare-name-bound user instance keeps its own method in all of them.
     */
    private String emitMethodCall(PythonNode.Attribute attr, List<PythonNode> args) {
        String obj = emitExpr(attr.obj());
        String m = attr.attr();
        String a = emitArgs(args);
        String e0 = args.isEmpty() ? "" : emitExpr(args.get(0));
        String e1 = args.size() > 1 ? emitExpr(args.get(1)) : null;
        String e2 = args.size() > 2 ? emitExpr(args.get(2)) : null;
        boolean userInstance = DICT_METHODS.contains(m) && hijackReceiverIsUserInstance(attr);
        boolean plainReceiver = DICT_METHODS.contains(m) && hijackReceiverIsPlain(attr);
        return switch (m) {
            // str
            case "upper" -> args.isEmpty() ? obj + ".toUpperCase()" : null;
            case "lower" -> args.isEmpty() ? obj + ".toLowerCase()" : null;
            case "strip" -> args.isEmpty() ? obj + ".trim()" : null;
            case "lstrip" -> args.isEmpty() ? obj + ".trimStart()" : null;
            case "rstrip" -> args.isEmpty() ? obj + ".trimEnd()" : null;
            case "find" -> args.size() == 1 ? obj + ".indexOf(" + a + ")" : null;
            case "rfind" -> args.size() == 1 ? obj + ".lastIndexOf(" + a + ")" : null;
            case "index" -> {
                // Hijack guard, the same gap sort had: a user class with def index(self, x) must not be
                // mapped to indexOf. JS gives str and array no .index method, so a typeof probe cannot hit
                // by mistake, and this uses the same rtDispatch as the dict method family.
                if (args.size() != 1 || hijackReceiverIsUserInstance(attr)) yield null;
                yield hijackReceiverIsPlain(attr)
                        ? rtDispatch(obj, "index", obj + ".index(" + a + ")", obj + ".indexOf(" + a + ")")
                        : obj + ".indexOf(" + a + ")";
            }
            case "ljust" -> args.size() == 1 ? obj + ".padEnd(" + a + ")" : null;
            case "rjust" -> args.size() == 1 ? obj + ".padStart(" + a + ")" : null;
            case "zfill" -> {
                if (hijackReceiverIsUserInstance(attr)) yield null;
                yield args.size() == 1 ? "__nekoPyZfill(" + obj + ", " + e0 + ")" : null;
            }
            case "replace" -> {
                if (hijackReceiverIsUserInstance(attr)) yield null;
                if (args.size() < 2 || args.size() > 3) {
                    throw err("python 'str.replace' needs 2 or 3 arguments (replace(old, new[, count]))");
                }
                yield "__nekoPyStrReplace(" + obj + ", " + e0 + ", " + e1 + ", "
                        + (args.size() == 2 ? "-1" : e2) + ")";
            }
            case "startswith" -> args.size() == 1 ? obj + ".startsWith(" + a + ")" : null;
            case "endswith" -> args.size() == 1 ? obj + ".endsWith(" + a + ")" : null;
            case "count" -> {
                // Same as index: a user class with def count(self, x) keeps its own method, and neither
                // str nor array has a .count method, so rtDispatch probes it.
                if (hijackReceiverIsUserInstance(attr)) yield null;
                if (args.isEmpty() || args.size() > 3) {
                    throw err("python 'str.count' needs 1 to 3 arguments (count(sub[, start[, end]]))");
                }
                String strForm = "__nekoPyStrCount(" + obj + ", " + e0 + ", "
                        + (args.size() >= 2 ? e1 : "null") + ", "
                        + (args.size() >= 3 ? e2 : "null") + ")";
                yield hijackReceiverIsPlain(attr)
                        ? rtDispatch(obj, "count", obj + ".count(" + a + ")", strForm)
                        : strForm;
            }
            case "split" -> {
                if (hijackReceiverIsUserInstance(attr)) yield null;
                if (args.size() > 2) {
                    throw err("python 'str.split' needs at most 2 arguments (split([sep[, maxsplit]]))");
                }
                if (args.size() <= 1) {
                    yield args.isEmpty()
                            ? obj + ".trim().split(/\\s+/).filter(function (x) { return x !== \"\"; })"
                            : obj + ".split(" + a + ")";
                }
                yield "((function (__o, __sep, __max) {"
                        + " if (__max < 0) return __o.split(__sep);"
                        + " var __limit = Math.trunc(__max);"
                        + " if (__limit <= 0) return [__o];"
                        + " if (__sep === '') return __o.split('');"
                        + " var __parts = [], __idx = 0, __sepLen = __sep.length;"
                        + " while (__limit > 0) {"
                        + "   var __i = __o.indexOf(__sep, __idx);"
                        + "   if (__i < 0) break;"
                        + "   __parts.push(__o.substring(__idx, __i));"
                        + "   __idx = __i + __sepLen;"
                        + "   __limit--;"
                        + " }"
                        + " __parts.push(__o.substring(__idx));"
                        + " return __parts;"
                        + " })(" + obj + ", " + e0 + ", " + e1 + "))";
            }
            case "join" -> args.size() == 1 ? "([..." + a + "].join(" + obj + "))" : null;
            // list
            case "append" -> args.size() == 1 ? obj + ".push(" + a + ")" : null;
            // copy/insert/remove/pop get the same hijack guard as sort. A statically known user-class
            // receiver (Cls(...).m or self.m inside a method body) goes straight to the native call.
            // A simple receiver (Name/attribute chain/index) is probed at runtime: only a real array
            // with no own property of that name is mapped to the array fallback, so a user-class
            // instance held in a plain name (b = Box(); b.pop()) keeps its own method. The reverse
            // fallback has the same shape as the native call (obj.reverse()), so it cannot be
            // hijacked and needs no probe.
            case "copy" -> {
                if (!args.isEmpty() || hijackReceiverIsUserInstance(attr)) yield null;
                yield hijackReceiverIsPlain(attr)
                        ? rtArrayDispatch(obj, "copy", obj + ".slice()", obj + ".copy()")
                        : obj + ".slice()";
            }
            case "reverse" -> args.isEmpty() ? obj + ".reverse()" : null;
            case "insert" -> {
                if (args.size() != 2 || hijackReceiverIsUserInstance(attr)) yield null;
                String arrForm = obj + ".splice(" + e0 + ", 0, " + e1 + ")";
                yield hijackReceiverIsPlain(attr)
                        ? rtArrayDispatch(obj, "insert", arrForm, obj + ".insert(" + e0 + ", " + e1 + ")")
                        : arrForm;
            }
            case "remove" -> {
                if (args.size() != 1 || hijackReceiverIsUserInstance(attr)) yield null;
                String arrForm = "((function (arr, v) { var i = arr.indexOf(v); if (i >= 0) arr.splice(i, 1); })(" + obj + ", " + e0 + "))";
                yield hijackReceiverIsPlain(attr)
                        ? rtArrayDispatch(obj, "remove", arrForm, obj + ".remove(" + e0 + ")")
                        : arrForm;
            }
            case "pop" -> {
                // A JS array already has .pop (on Array.prototype, so a typeof probe would hit a real
                // array), so this uses the same two-condition probe as sort: Array.isArray plus no own
                // pop. With no arguments the array fallback has the same shape as the native call
                // (obj.pop()), so no probe is needed.
                if (args.size() > 1 || userInstance) yield null;
                if (args.isEmpty()) yield obj + ".pop()";
                String arrForm = obj + ".splice(" + e0 + ", 1)[0]";
                if (!hijackReceiverIsPlain(attr)) yield arrForm;   // Complex receiver: evaluate it once and treat it as an array
                yield rtArrayDispatch(obj, "pop", arrForm, obj + ".pop(" + e0 + ")");
            }
            case "sort" -> {
                // The same comparator as sorted(): numbers compare by value (JS sorts by string by
                // default, so [10,2,1] would become [1,10,2]).
                // Hijack guard: a statically known user-class receiver (Cls(...).sort or self.sort in a
                // method body) goes straight to the native call. A simple receiver (Name/attribute
                // chain/index) is probed at runtime, and the comparator is injected only into a real
                // array with no own sort; a user-class instance keeps its own sort method, since
                // injecting the comparator unconditionally would pass it as the first argument to that
                // method and produce silently wrong values.
                if (!args.isEmpty() || hijackReceiverIsUserInstance(attr)) yield null;
                String cmp = obj + ".sort((a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0)))";
                if (!hijackReceiverIsPlain(attr)) yield cmp;   // Complex receiver: evaluate it once and treat it as an array
                yield rtArrayDispatch(obj, "sort", cmp, obj + ".sort()");
            }
            // dict (obj is a plain JS object): the worst offenders go through the hijack guard
            case "keys" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".keys()", "Object.keys(" + obj + ")")
                        : "Object.keys(" + obj + ")";
            }
            case "values" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".values()", "Object.values(" + obj + ")")
                        : "Object.values(" + obj + ")";
            }
            case "items" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".items()", "Object.entries(" + obj + ")")
                        : "Object.entries(" + obj + ")";
            }
            case "update" -> {
                if (args.size() != 1 || userInstance) yield null;
                yield plainReceiver
                        ? rtDispatch(obj, m, obj + ".update(" + a + ")", "Object.assign(" + obj + ", " + a + ")")
                        : "Object.assign(" + obj + ", " + a + ")";
            }
            case "get" -> {
                if (userInstance) yield null;   // The user class's own get method, including a zero-argument def get(self)
                if (args.isEmpty()) {
                    // .get() with no argument would emit `obj[]`, which is invalid JS, so it fails at compile time
                    throw err("python '.get()' needs a key argument (get(key[, default]))");
                }
                if (args.size() > 2) yield null;
                // .get(k) without a default: a missing key returns None, because a bare index gives
                // undefined and an is None check on it fails
                String fb = (e1 != null)
                        ? "(" + obj + "[" + e0 + "] !== undefined ? " + obj + "[" + e0 + "] : " + e1 + ")"
                        : "(" + obj + "[" + e0 + "] !== undefined ? " + obj + "[" + e0 + "] : null)";
                yield plainReceiver ? rtDispatch(obj, m, obj + ".get(" + a + ")", fb) : fb;
            }
            // set
            case "discard" -> args.size() == 1 ? obj + ".delete(" + a + ")" : null;
            default -> null;
        };
    }

    /**
     * Dict method-name hijack guard, part 1: the two cases where the receiver is a user-class instance
     * can be decided statically, a construction call {@code UserClass(...).m(...)} and
     * {@code self.m(...)} inside a method body. A true result means the method name is not intercepted
     * and the native {@code obj.m(args)} call is used.
     */
    private boolean hijackReceiverIsUserInstance(PythonNode.Attribute attr) {
        if (attr.obj() instanceof PythonNode.Call cc
                && cc.func() instanceof PythonNode.Name cn && classNames.contains(cn.id())) return true;
        return rewriteSelf && attr.obj() instanceof PythonNode.Name sn && "self".equals(sn.id());
    }

    /** Dict method-name hijack guard, part 2: the receiver is a side-effect-free simple expression
     * (a Name, an attribute chain or an index). */
    private static boolean hijackReceiverIsPlain(PythonNode.Attribute attr) {
        PythonNode o = attr.obj();
        return o instanceof PythonNode.Name || o instanceof PythonNode.Attribute || o instanceof PythonNode.Index;
    }

    /**
     * Hijack guard for method names, part 3: runtime probe dispatch. If the receiver defines a method
     * of that name (a user-class instance, or a Map with its own get/keys and so on), the call goes to
     * it; otherwise it goes to the fallback mapping. This guard covers the dict method family
     * (get/keys/values/items/update/pop) and the str/list names that JS has no native equivalent for
     * (index/count). The receiver must be a simple expression (see {@link #hijackReceiverIsPlain}),
     * because the obj text is evaluated more than once.
     */
    private static String rtDispatch(String obj, String attrName, String userForm, String dictForm) {
        return "(typeof " + obj + "." + attrName + " === \"function\" ? " + userForm + " : " + dictForm + ")";
    }

    /**
     * Hijack guard for method names, part 4: names that a JS array already has, such as sort, pop,
     * copy, insert and remove (or that live on Array.prototype), cannot use the typeof probe of
     * {@link #rtDispatch}, because {@code typeof xs.sort === "function"} is always true for a real
     * array and would match the wrong branch. Instead this uses two conditions, Array.isArray plus no
     * own property of that name: only a real array (whose methods are all on Array.prototype, never own
     * properties) gets the array fallback mapping, and any other receiver (a user-class instance keeps
     * its methods on the prototype, not as own properties) calls its own method natively. When the user
     * overrides with {@code xs.pop = fn}, the own property exists, so the override is respected. The
     * receiver must be a simple expression (see {@link #hijackReceiverIsPlain}), because the obj text
     * is evaluated more than once.
     */
    private static String rtArrayDispatch(String obj, String attrName, String arrForm, String userForm) {
        return "((Array.isArray(" + obj + ") && !Object.prototype.hasOwnProperty.call(" + obj + ", \"" + attrName + "\")) ? "
                + arrForm + " : " + userForm + ")";
    }

    /**
     * Lowers {@code with ctx [as tgt]: body} to an in-scope acquire/try/finally (not wrapped in an
     * IIFE) so that {@code return}/{@code break}/{@code continue} inside the body propagate to the
     * enclosing scope. If the context object exposes JS {@code __enter__}/{@code __exit__} methods
     * (a Python-style context manager), they are called; otherwise the value is bound as-is, which
     * covers the common {@code with EXPR as x:} binding case. Multiple items nest.
     */
    private void emitWith(PythonNode.With w, int idx) {
        if (idx == w.items().size()) {
            for (PythonNode s : w.body()) emitStmt(s);   // body sits inside the innermost try { }
            return;
        }
        PythonNode.WithItem item = w.items().get(idx);
        String ctxVar = "__nekoCtx" + (tempCounter++);
        line("var " + ctxVar + " = " + emitExpr(item.context()) + ";");
        String entered = "((" + ctxVar + " != null && typeof " + ctxVar + ".__enter__ === \"function\") ? "
                + ctxVar + ".__enter__() : " + ctxVar + ")";
        if (item.target() != null) {
            line("var " + emitTarget(item.target()) + " = " + entered + ";");
        } else {
            line(entered + ";");   // call __enter__ for its side effect, discard the value
        }
        line("try {");
        indent++;
        emitWith(w, idx + 1);
        indent--;
        line("} finally {");
        indent++;
        line("if (" + ctxVar + " != null && typeof " + ctxVar + ".__exit__ === \"function\") " + ctxVar + ".__exit__();");
        indent--;
        line("}");
    }

    /**
     * Lowers a generator expression {@code (expr for x in iter if cond)} to an immediately-invoked
     * generator function, so it yields lazily like Python and interoperates with {@code list()}/
     * {@code sum()}/{@code any()} (which spread). Multi-clause for/if nest as JS for-of / if blocks.
     */
    private String emitGenExp(PythonNode.GenExp g) {
        StringBuilder sb = new StringBuilder("((function* () {\n");
        int d = 1;
        for (PythonNode.CompClause c : g.clauses()) {
            String pad = "  ".repeat(d);
            if (c instanceof PythonNode.ForComp fc) {
                sb.append(pad).append("for (var ").append(emitTarget(fc.target())).append(" of __nekoIter(")
                        .append(emitExpr(fc.iter())).append(")) {\n");
            } else if (c instanceof PythonNode.IfComp ic) {
                sb.append(pad).append("if (__nekoTruthy(").append(emitExpr(ic.cond())).append(")) {\n");
            }
            d++;
        }
        sb.append("  ".repeat(d)).append("yield ").append(emitExpr(g.element())).append(";\n");
        for (int k = 0; k < g.clauses().size(); k++) {
            d--;
            sb.append("  ".repeat(d)).append("}\n");
        }
        return sb.append("})())").toString();
    }

    /**
     * Lowers a {@code match} statement to an if/else-if chain guarded by a "matched" flag (so a
     * guard that fails, or a non-matching case, falls through to the next case; the first matching
     * case's body runs and the match then ends, matching Python). Each case contributes a pattern
     * condition (over the captured subject) and zero or more bindings; bindings are emitted inside
     * the case's block before the guard so the guard can reference them.
     */
    private void emitMatch(PythonNode.Match m) {
        String subj = "__nekoSubj" + (tempCounter++);
        String matched = "__nekoMatched" + (tempCounter++);
        line("var " + subj + " = " + emitExpr(m.subject()) + ";");
        line("var " + matched + " = false;");
        for (PythonNode.MatchCase mc : m.cases()) {
            List<String> conds = new ArrayList<>();
            List<String> binds = new ArrayList<>();
            emitMatchCond(mc.pattern(), subj, conds, binds);
            String patCond = conds.isEmpty() ? "true" : "(" + String.join(" && ", conds) + ")";
            line("if (!" + matched + " && " + patCond + ") {");
            indent++;
            for (String b : binds) line(b);
            String guard = mc.guard() != null ? "__nekoTruthy(" + emitExpr(mc.guard()) + ")" : "true";
            line("if (" + guard + ") {");
            indent++;
            line(matched + " = true;");
            for (PythonNode s : mc.body()) emitStmt(s);
            indent--;
            line("}");
            indent--;
            line("}");
        }
    }

    /** Accumulates the match condition terms (conds) and binding statements (binds) for one pattern. */
    private void emitMatchCond(PythonNode.Pattern p, String subj, List<String> conds, List<String> binds) {
        if (p instanceof PythonNode.LiteralPat lp) {
            conds.add(subj + " === " + emitExpr(lp.value()));
        } else if (p instanceof PythonNode.CapturePat cp) {
            if (!"_".equals(cp.name())) binds.add("var " + jsName(cp.name()) + " = " + subj + ";");   // wildcard → no bind
        } else if (p instanceof PythonNode.OrPat op) {
            List<String> altConds = new ArrayList<>();
            for (PythonNode.Pattern alt : op.alts()) {
                List<String> ac = new ArrayList<>();
                emitMatchCond(alt, subj, ac, new ArrayList<>());   // OR alternatives must not bind (Python rule)
                altConds.add(ac.isEmpty() ? "true" : "(" + String.join(" && ", ac) + ")");
            }
            conds.add("(" + String.join(" || ", altConds) + ")");
        } else if (p instanceof PythonNode.SequencePat sp) {
            conds.add("Array.isArray(" + subj + ")");   // sequence patterns don't match strings (Python)
            int n = sp.elements().size();
            conds.add(sp.starName() == null ? subj + ".length === " + n : subj + ".length >= " + n);
            for (int i = 0; i < n; i++) {
                String child = (i < sp.starIndex()) ? subj + "[" + i + "]"
                        : subj + "[" + subj + ".length - " + (n - i) + "]";
                emitMatchCond(sp.elements().get(i), child, conds, binds);
            }
            if (sp.starName() != null) {
                binds.add("var " + jsName(sp.starName()) + " = " + subj + ".slice(" + sp.starIndex() + ", "
                        + subj + ".length - " + (n - sp.starIndex()) + ");");
            }
        } else if (p instanceof PythonNode.MappingPat mp) {
            for (int i = 0; i < mp.keys().size(); i++) {
                String k = emitExpr(mp.keys().get(i));
                conds.add(subj + "[" + k + "] !== undefined");
                binds.add("var " + jsName(mp.valueNames().get(i)) + " = " + subj + "[" + k + "];");
            }
            if (mp.restName() != null) {
                StringBuilder copy = new StringBuilder("(function (__o) { var __r = {}; for (var __k in __o) __r[__k] = __o[__k];");
                for (PythonNode key : mp.keys()) copy.append(" delete __r[").append(emitExpr(key)).append("];");
                copy.append(" return __r; })(").append(subj).append(")");
                binds.add("var " + jsName(mp.restName()) + " = " + copy + ";");
            }
        } else if (p instanceof PythonNode.ClassPat cp) {
            conds.add(subj + " instanceof " + jsName(cp.className()));
            for (var e : cp.keyword().entrySet()) {
                emitMatchCond(e.getValue(), subj + "." + e.getKey(), conds, binds);
            }
        }
    }

    private void writeIf(PythonNode.If i, boolean leadIndent) {
        if (!leadIndent) {
            // An inlined nested if (the elif rewrite, or the single if under else:) does not go through
            // emitStmt, so its own recorded source line sets curLine and the source map here. Then an
            // emit-time error in an elif condition reports the elif line instead of the outer if line,
            // and the generated `} else if` line maps back to the Python source. emitStmt restores
            // curLine when the outer statement ends, so nothing is saved or restored here.
            Integer ln = srcLines.get(i);
            if (ln != null) {
                curLine = ln;
                recordMapping(i);
            }
        }
        if (leadIndent) out.append(ind());
        // The condition is tested with Python truthiness, so an empty container is false. The condition
        // text may contain a multi-line expression (a stepped slice or a generator expression), and its
        // embedded newlines must advance jsLine the way line() accounts for them, or the source map drifts.
        String cond = emitExpr(i.cond());
        jsLine += countNewlines(cond);
        out.append("if (__nekoTruthy(").append(cond).append(")) {");
        br();
        block(i.thenBody());
        List<PythonNode> els = i.elseBody();
        if (els.isEmpty()) {
            out.append(ind()).append("}");
            br();
        } else if (els.size() == 1 && els.get(0) instanceof PythonNode.If nested) {
            out.append(ind()).append("} else ");
            writeIf(nested, false);
        } else {
            out.append(ind()).append("} else {");
            br();
            block(els);
            out.append(ind()).append("}");
            br();
        }
    }

    // ---- expressions (each compound wraps itself in parens) ----

    private String emitExpr(PythonNode node) {
        return switch (node) {
            case PythonNode.IntLit l -> Long.toString(l.value());
            case PythonNode.FloatLit l -> Double.toString(l.value());
            case PythonNode.StrLit l -> jsString(l.value());
            case PythonNode.FString f -> jsTemplate(f);
            case PythonNode.BoolLit l -> Boolean.toString(l.value());
            case PythonNode.NoneLit l -> "null";
            case PythonNode.Name n -> (rewriteSelf && "self".equals(n.id())) ? "this" : jsName(n.id());
            case PythonNode.Attribute a -> emitExpr(a.obj()) + "." + a.attr();
            case PythonNode.Index ix -> {
                if (ix.index() instanceof PythonNode.Slice s) {
                    yield emitSlice(emitExpr(ix.obj()), s);
                }
                // negative literal index → Python last-element semantics via slice
                // (`-1` parses as Unary("-", IntLit), so detect both forms)
                long mag = negativeLiteralMagnitude(ix.index());
                if (mag >= 0) {
                    yield emitExpr(ix.obj()) + ".slice(-" + mag + ")[0]";
                }
                yield emitExpr(ix.obj()) + "[" + emitExpr(ix.index()) + "]";
            }
            case PythonNode.Call c -> emitCall(c);
            case PythonNode.Unary u -> "not".equals(u.op())
                    ? "(!__nekoTruthy(" + emitExpr(u.operand()) + "))"   // not is tested with Python truthiness, so an empty container is false
                    : "(" + u.op() + emitExpr(u.operand()) + ")";   // - + ~
            case PythonNode.Binary b -> emitBinary(b);
            case PythonNode.Compare c -> emitCompare(c);
            case PythonNode.Ternary t -> "(__nekoTruthy(" + emitExpr(t.cond()) + ") ? " + emitExpr(t.ifTrue()) + " : " + emitExpr(t.ifFalse()) + ")";
            case PythonNode.Walrus w -> "(" + jsName(w.name()) + " = " + emitExpr(w.value()) + ")";
            case PythonNode.GenExp g -> emitGenExp(g);
            case PythonNode.Starred s -> "..." + emitExpr(s.value());   // standalone (rare); spreads normally apply at emitArgs/emitElements
            case PythonNode.ListLit l -> emitElements(l.elements());
            case PythonNode.TupleLit l -> emitElements(l.elements());
            case PythonNode.DictLit d -> emitDict(d);
            case PythonNode.SetLit l -> "new Set(" + emitElements(l.elements()) + ")";
            case PythonNode.Lambda lam -> {
                if (hasKwargs(lam.params())) {
                    throw err("python **kwargs is not supported in a lambda (use a def)");
                }
                yield "((" + emitParams(lam.params()) + ") => (" + emitExpr(lam.body()) + "))";
            }
            case PythonNode.ListComp lc -> compChain(lc.clauses(), emitExpr(lc.element()));
            case PythonNode.Yield y -> y.from()
                    ? ("(yield* " + emitExpr(y.value()) + ")")
                    : ("(yield" + (y.value() != null ? " " + emitExpr(y.value()) : "") + ")");
            case PythonNode.DictComp dc -> "Object.fromEntries(" + compChain(dc.clauses(),
                    "[" + emitExpr(dc.key()) + ", " + emitExpr(dc.value()) + "]") + ")";
            case PythonNode.SetComp sc -> "new Set(" + compChain(sc.clauses(), emitExpr(sc.element())) + ")";
            default -> throw err("unsupported expression: " + node.getClass().getSimpleName());
        };
    }

    private String emitCall(PythonNode.Call c) {
        // super().__init__(args) → super(args);  super().method(args) → super.method(args)
        if (c.func() instanceof PythonNode.Attribute attr
                && attr.obj() instanceof PythonNode.Call sup
                && sup.func() instanceof PythonNode.Name sn && "super".equals(sn.id()) && sup.args().isEmpty()) {
            // kwargs to super() cannot be lowered (JS super() takes positionals only) — reject them
            // instead of silently dropping them, which previously produced wrong behaviour.
            for (PythonNode a : c.args()) {
                if (a instanceof PythonNode.Kwarg k) {
                    throw err("python keyword argument '" + k.name()
                            + "=' in super() is not supported");
                }
                if (a instanceof PythonNode.Starred s && s.dictSpread()) {
                    throw err("python **kwargs spread in super() is not supported");
                }
            }
            String args = emitArgs(c.args());
            if ("__init__".equals(attr.attr())) return "super(" + args + ")";
            return "super." + attr.attr() + "(" + args + ")";
        }
        // separate positional args / keyword args / **dict-spreads first (so method-call mappings never see kwargs)
        List<PythonNode> positional = new ArrayList<>();   // may contain Starred(*) → spread by emitArgs
        Map<String, PythonNode> kwargs = new LinkedHashMap<>();
        List<PythonNode> kwSpreads = new ArrayList<>();     // **expr → merged into the trailing kw object
        for (PythonNode a : c.args()) {
            if (a instanceof PythonNode.Kwarg k) kwargs.put(k.name(), k.value());
            else if (a instanceof PythonNode.Starred s && s.dictSpread()) kwSpreads.add(s.value());
            else positional.add(a);
        }
        boolean hasKw = !kwargs.isEmpty() || !kwSpreads.isEmpty();
        // method calls: map common str/list/dict/set methods to JS idioms (they take no kwargs)
        if (!hasKw && c.func() instanceof PythonNode.Attribute mem) {
            String mapped = emitMethodCall(mem, positional);
            if (mapped != null) return mapped;
        }
        if (c.func() instanceof PythonNode.Name fn) {
            String e0 = positional.isEmpty() ? "" : emitExpr(positional.get(0));
            switch (fn.id()) {
                case "range" -> { return emitRange(positional); }
                case "len" -> { if (positional.size() == 1) return "__nekoLen(" + e0 + ")"; }
                case "print" -> { return emitPrint(positional, kwargs); }
                case "abs" -> { if (positional.size() == 1) return "Math.abs(" + e0 + ")"; }
                case "min" -> {
                    PythonNode keyFn = kwargs.get("key");
                    if (keyFn != null && positional.size() == 1) {
                        String kf = emitExpr(keyFn);
                        return "([...(" + e0 + ")]).reduce(function (a, b) { return (" + kf + "(a) <= " + kf + "(b)) ? a : b; })";
                    }
                    return positional.size() == 1 ? "Math.min(..." + e0 + ")" : "Math.min(" + emitArgs(positional) + ")";
                }
                case "max" -> {
                    PythonNode keyFn = kwargs.get("key");
                    if (keyFn != null && positional.size() == 1) {
                        String kf = emitExpr(keyFn);
                        return "([...(" + e0 + ")]).reduce(function (a, b) { return (" + kf + "(a) >= " + kf + "(b)) ? a : b; })";
                    }
                    return positional.size() == 1 ? "Math.max(..." + e0 + ")" : "Math.max(" + emitArgs(positional) + ")";
                }
                case "sum" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).reduce((a, b) => (a + b), 0)"; }
                case "str" -> { if (positional.size() == 1) return "__nekoPyStr(" + e0 + ")"; }
                case "int" -> { return "parseInt(" + emitArgs(positional) + ")"; }
                case "float" -> { if (positional.size() == 1) return "Number(" + e0 + ")"; }
                case "bool" -> { if (positional.size() == 1) return "__nekoTruthy(" + e0 + ")"; }
                case "list" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "dict" -> { return positional.isEmpty() ? "({})" : "Object.fromEntries(" + e0 + ")"; }
                case "sorted" -> { return emitSorted(positional, kwargs); }
                case "enumerate" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).map((v, i) => [i, v])"; }
                case "set" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                case "tuple" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "any" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).some((x) => __nekoTruthy(x))"; }
                case "all" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).every((x) => __nekoTruthy(x))"; }
                case "ord" -> { if (positional.size() == 1) return "(" + e0 + ").codePointAt(0)"; }
                case "chr" -> { if (positional.size() == 1) return "String.fromCodePoint(" + e0 + ")"; }
                case "pow" -> { if (positional.size() == 2) return "Math.pow(" + emitArgs(positional) + ")"; }
                case "callable" -> { if (positional.size() == 1) return "(typeof " + e0 + " === \"function\")"; }
                case "isinstance" -> {
                    if (positional.size() == 2) {
                        return instanceOfTypeCheck(positional.get(1), e0);
                    }
                }
                case "type" -> { if (positional.size() == 1) return "(" + e0 + ").constructor"; }
                case "hex" -> { if (positional.size() == 1) return signPrefix(e0, "0x", 16); }
                case "oct" -> { if (positional.size() == 1) return signPrefix(e0, "0o", 8); }
                case "bin" -> { if (positional.size() == 1) return signPrefix(e0, "0b", 2); }
                case "repr" -> { if (positional.size() == 1) return "JSON.stringify(" + e0 + ")"; }
                case "round" -> {
                    if (positional.size() == 1) return "Math.round(" + e0 + ")";
                    if (positional.size() == 2) {
                        String p1 = emitExpr(positional.get(1));
                        return "(Math.round(" + e0 + " * Math.pow(10, " + p1 + ")) / Math.pow(10, " + p1 + "))";
                    }
                }
                case "divmod" -> {
                    if (positional.size() == 2) {
                        // Both elements go through the division-by-zero check, so divmod(1, 0) throws
                        // ZeroDivisionError as Python does
                        String p1 = emitExpr(positional.get(1));
                        return "[__nekoFloorDiv(" + e0 + ", " + p1 + "), __nekoMod(" + e0 + ", " + p1 + ")]";
                    }
                }
                case "reversed" -> { if (positional.size() == 1) return "[...(" + e0 + ")].reverse()"; }
                case "map" -> {
                    if (positional.size() == 2)
                        return "[...(" + emitExpr(positional.get(1)) + ")].map(" + emitExpr(positional.get(0)) + ")";
                }
                case "filter" -> {
                    // The predicate result is tested with Python truthiness, so an empty container is false
                    // (JS treats [] as always true). filter(None, seq) is an identity filter on each
                    // element's own truthiness, and a null predicate passed to .filter would throw a
                    // TypeError at runtime. The predicate is called through the reserved name
                    // __nekoFx, so the arrow parameter does not shadow a variable of the same name in
                    // the predicate expression.
                    if (positional.size() == 2) {
                        String seq = emitExpr(positional.get(1));
                        if (positional.get(0) instanceof PythonNode.NoneLit) {
                            return "[...(" + seq + ")].filter((__nekoFx) => __nekoTruthy(__nekoFx))";
                        }
                        String pred = emitExpr(positional.get(0));
                        return "[...(" + seq + ")].filter((__nekoFx) => __nekoTruthy(" + pred + "(__nekoFx)))";
                    }
                }
                case "zip" -> {
                    return "((function () { var __its = [" + emitArgs(positional)
                            + "].map(function (x) { return [...x]; }); var __n = __its.length ? Math.min.apply(null, __its.map(function (a) { return a.length; })) : 0; var __r = [];"
                            + " for (var __i = 0; __i < __n; __i++) { var __t = []; for (var __j = 0; __j < __its.length; __j++) __t.push(__its[__j][__i]); __r.push(__t); } return __r; })())";
                }
                case "format" -> {
                    if (positional.size() == 2)
                        return "__nekoFmt(" + e0 + ", " + emitExpr(positional.get(1)) + ", null)";
                }
                case "getattr" -> {
                    if (positional.size() == 2) return e0 + "[" + emitExpr(positional.get(1)) + "]";
                    if (positional.size() == 3) {
                        String k = emitExpr(positional.get(1)), d = emitExpr(positional.get(2));
                        return "(" + e0 + "[" + k + "] !== undefined ? " + e0 + "[" + k + "] : " + d + ")";
                    }
                }
                case "hasattr" -> { if (positional.size() == 2) return "(" + e0 + "[" + emitExpr(positional.get(1)) + "] !== undefined)"; }
                case "setattr" -> { if (positional.size() == 3) return "(" + e0 + "[" + emitExpr(positional.get(1)) + "] = " + emitExpr(positional.get(2)) + ")"; }
                case "delattr" -> { if (positional.size() == 2) return "(delete " + e0 + "[" + emitExpr(positional.get(1)) + "])"; }
                case "iter" -> { if (positional.size() == 1) return "(" + e0 + "[Symbol.iterator]())"; }
                case "next" -> { if (positional.size() == 1) return "(" + e0 + ".next().value)"; }
                case "frozenset" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                default -> {}
            }
            // Construction of a built-in exception class: ValueError('x') becomes new ValueError("x").
            // EXC_PRELUDE provides the classes. A bare call would throw a ReferenceError at runtime, and
            // except would then catch it as the wrong type. An alias of the class (VE = ValueError) is
            // constructed the same way.
            if (!hasKw && (BUILTIN_EXCEPTIONS.contains(fn.id()) || excClassAliases.contains(fn.id()))) {
                return "new " + jsName(fn.id()) + "(" + emitArgs(positional) + ")";
            }
            if (classNames.contains(fn.id())) {
                if (hasKw) {
                    if (!kwClassNames.contains(fn.id())) {
                        throw err("python keyword arguments to '" + fn.id()
                                + "()' require its __init__ to declare **kwargs");
                    }
                    return "new " + jsName(fn.id()) + "(" + emitArgs(positional)
                            + (positional.isEmpty() ? "" : ", ") + kwObjectLiteral(kwargs, kwSpreads) + ")";
                }
                return "new " + jsName(fn.id()) + "(" + emitArgs(positional) + ")";
            }
        }
        if (hasKw) {
            // Keyword args route to a tagged trailing object only when the callee declares **kwargs.
            boolean kwAware = false;
            String label = "()";
            if (c.func() instanceof PythonNode.Name fn) {
                kwAware = kwFunctions.contains(fn.id());
                label = fn.id() + "()";
            } else if (c.func() instanceof PythonNode.Attribute a) {
                kwAware = kwMethods.contains(a.attr());
                label = "." + a.attr() + "()";
            }
            if (!kwAware) {
                throw err("python keyword arguments require the target to declare **kwargs"
                        + " (or be print/sorted); '" + label + "' does not");
            }
            return emitExpr(c.func()) + "(" + emitArgs(positional)
                    + (positional.isEmpty() ? "" : ", ") + kwObjectLiteral(kwargs, kwSpreads) + ")";
        }
        return emitExpr(c.func()) + "(" + emitArgs(positional) + ")";
    }

    /** Emits print(args, sep=, end=) as console.log: every argument goes through __nekoPyStr
     *  first (None/True/False, array elements recursively), then the results are joined with
     *  sep. end= is ignored because console.log already adds a newline. */
    private String emitPrint(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        String sep = kwargs.containsKey("sep") ? emitExpr(kwargs.get("sep")) : "\" \"";
        return "console.log([" + emitArgs(args) + "].map(__nekoPyStr).join(" + sep + "))";
    }

    /** sorted(iter, reverse=) → numeric/string sort; reverse honours a True/False literal (else runtime). */
    private String emitSorted(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        if (args.size() != 1) breakKeyword("sorted", args.size());
        PythonNode keyFn = kwargs.get("key");
        String cmp = keyFn != null
                ? "function (a, b) { var ka = (" + emitExpr(keyFn) + ")(a), kb = (" + emitExpr(keyFn) + ")(b); return (ka < kb) ? -1 : (ka > kb) ? 1 : 0; }"
                : "(a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0))";
        String sorted = "([...(" + emitExpr(args.get(0)) + ")]).sort(" + cmp + ")";
        PythonNode rev = kwargs.get("reverse");
        if (rev == null) return sorted;
        if (rev instanceof PythonNode.BoolLit b) return b.value() ? sorted + ".reverse()" : sorted;
        // non-literal reverse flag → decide at runtime
        return "((function (__a) { if (" + emitExpr(rev) + ") __a.reverse(); return __a; })(" + sorted + "))";
    }

    private void breakKeyword(String name, int argc) {
        throw err("python " + name + "() unsupported with " + argc + " positional args");
    }

    /** {@code hex/oct/bin}: {@code "-0x" + abs.toString(base)} for negatives, {@code "0x" + ...} otherwise. */
    private static String signPrefix(String numExpr, String prefix, int base) {
        return "(((" + numExpr + ") < 0) ? \"-" + prefix + "\" : \"" + prefix + "\") + Math.abs(Math.trunc("
                + numExpr + ")).toString(" + base + ")";
    }

    private String emitRange(List<PythonNode> args) {
        // The Array.from callback parameter must NOT shadow variables in the start/step expressions
        // (e.g. range(i + 1, n) inside a for-loop), so use a reserved param name.
        final String idx = "__nekoRangeIdx";
        if (args.isEmpty()) throw err("python range() needs at least 1 arg");
        if (args.size() == 1) {
            String stop = emitExpr(args.get(0));
            return "Array.from({length: " + stop + "}, function (_, " + idx + ") { return " + idx + "; })";
        }
        if (args.size() == 2) {
            String start = emitExpr(args.get(0));
            String stop = emitExpr(args.get(1));
            return "Array.from({length: (" + stop + " - " + start + ")}, function (_, " + idx + ") { return " + idx + " + " + start + "; })";
        }
        String start = emitExpr(args.get(0));
        String stop = emitExpr(args.get(1));
        String step = emitExpr(args.get(2));
        return "Array.from({length: Math.ceil((" + stop + " - " + start + ") / " + step + ")}, function (_, " + idx + ") { return "
                + start + " + " + idx + " * " + step + "; })";
    }

    private String emitBinary(PythonNode.Binary b) {
        String op = b.op();
        if (op.equals("//")) {
            // Floor division. Division by zero raises ZeroDivisionError like Python (JS would silently
            // return Infinity), so the operands go through a helper.
            return "(__nekoFloorDiv(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("/")) {
            // True division. Also goes through a helper that checks for division by zero.
            return "(__nekoDiv(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("@")) {
            throw err("python matmul '@' is not supported");
        }
        if (op.equals("%")) {
            // Python modulo takes the sign of the divisor; JS % takes the sign of the dividend
            // (-7 % 2 is -1 in JS but 1 in Python).
            return "(__nekoMod(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("*")) {
            // Sequence repetition ([0] * 4 repeats an array, 'ab' * 3 repeats a string) or numeric
            // multiplication; both go through a helper.
            return "(__nekoMul(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("+")) {
            // Array concatenation, str + str concatenation, or numeric addition. Mixing str with a
            // non-str value raises TypeError; JS + would silently turn [1,2]+[3] into "1,23" and
            // 'a'+5 into 'a5', so the operands go through a helper.
            return "(__nekoAdd(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("and")) {
            // Value semantics, Python truthiness and short-circuiting: the right operand is wrapped in a
            // thunk to keep it lazy, and the left operand is passed as an argument so it is evaluated once.
            return "__nekoAnd(" + emitExpr(b.left()) + ", () => (" + emitExpr(b.right()) + "))";
        }
        if (op.equals("or")) {
            return "__nekoOr(" + emitExpr(b.left()) + ", () => (" + emitExpr(b.right()) + "))";
        }
        return "(" + emitExpr(b.left()) + " " + op + " " + emitExpr(b.right()) + ")";
    }

    private String emitCompare(PythonNode.Compare c) {
        // Chained comparison: a<b<c parses to Compare(Compare(a,<,b),<,c) → emit ((a<b) && (b<c)).
        // (Python evaluates each operand once; the JS form may re-evaluate the middle operand —
        // acceptable for side-effect-free comparisons, the common case.)
        if (c.left() instanceof PythonNode.Compare lc) {
            String mid = emitExpr(lc.right());
            String right = emitExpr(c.right());
            // The second part of a chained in / not in (a in b in c becomes (a in b) && (b in c)) must
            // also go through __nekoIn. Emitting the JS in operator directly gives a runtime
            // TypeError or wrong semantics when the right operand is a dict/Map, array or string.
            String second = switch (c.op()) {
                case "in" -> "__nekoIn(" + mid + ", " + right + ")";
                case "not in" -> "(!__nekoIn(" + mid + ", " + right + "))";
                default -> "(" + mid + " " + jsCompareOp(c.op()) + " " + right + ")";
            };
            return "(" + emitCompare(lc) + " && " + second + ")";
        }
        String left = emitExpr(c.left());
        String right = emitExpr(c.right());
        return switch (c.op()) {
            // in / not in go through a helper: a dict (object) checks its own keys, Map/Set use .has,
            // arrays and strings use .includes. Calling .includes for every operand is a runtime
            // TypeError for dict and set.
            case "in" -> "__nekoIn(" + left + ", " + right + ")";
            case "not in" -> "(!__nekoIn(" + left + ", " + right + "))";
            case "is" -> "(" + left + " === " + right + ")";
            case "is not" -> "(" + left + " !== " + right + ")";
            // == / != use strict equality: JS loose == coerces types ("1" == 1 is true) and silently
            // produces wrong results.
            default -> "(" + left + " " + jsCompareOp(c.op()) + " " + right + ")";
        };
    }

    /** Python {@code ==/!=} do not coerce types, so they become JS {@code ===/!==}; every other
     *  comparison operator is passed through unchanged. */
    private static String jsCompareOp(String op) {
        return switch (op) {
            case "==" -> "===";
            case "!=" -> "!==";
            default -> op;
        };
    }

    /**
     * Lowers a comprehension's clause list. A single {@code for} (with any trailing {@code if}
     * guards) emits the idiomatic {@code (iter).filter(...).map(...)} chain; multiple {@code for}
     * clauses emit nested {@code flatMap} calls (innermost wraps the element in an array), which is
     * the only way to express nested loops as a single expression. Guards on a for-level become a
     * conjunction inside that level's arrow ({@code guard ? <rest> : []}).
     *
     * @param elementExpr the per-iteration value — a plain element for list/set, or {@code [k, v]} for dict
     */
    private String compChain(List<PythonNode.CompClause> clauses, String elementExpr) {
        // Group clauses: each ForComp starts a group that absorbs its trailing IfComp guards.
        List<String> targets = new ArrayList<>();
        List<String> iters = new ArrayList<>();
        List<String> guards = new ArrayList<>();
        for (PythonNode.CompClause c : clauses) {
            if (c instanceof PythonNode.ForComp fc) {
                targets.add(emitTarget(fc.target()));
                iters.add("__nekoIter(" + emitExpr(fc.iter()) + ")");   // dict/Map: iterate the keys
                guards.add("");
            } else if (c instanceof PythonNode.IfComp ic) {
                int last = guards.size() - 1;
                String g = guards.get(last);
                g = g.isEmpty() ? "__nekoTruthy(" + emitExpr(ic.cond()) + ")"
                        : g + " && (__nekoTruthy(" + emitExpr(ic.cond()) + "))";
                guards.set(last, g);
            }
        }
        if (targets.size() == 1) {
            String base = "(" + iters.get(0) + ")";
            String chained = guards.get(0).isEmpty() ? base
                    : base + ".filter((" + targets.get(0) + ") => " + guards.get(0) + ")";
            return chained + ".map((" + targets.get(0) + ") => " + elementExpr + ")";
        }
        // multiple for-clauses → nested flatMap; innermost wraps the element in an array
        String body = "[" + elementExpr + "]";
        for (int gi = targets.size() - 1; gi >= 0; gi--) {
            String guard = guards.get(gi);
            String arrowBody = guard.isEmpty() ? body : ("(" + guard + " ? " + body + " : [])");
            body = "((" + iters.get(gi) + ").flatMap((" + targets.get(gi) + ") => " + arrowBody + "))";
        }
        return body;
    }

    private String emitDict(PythonNode.DictLit d) {
        StringBuilder sb = new StringBuilder("({");
        for (int i = 0; i < d.keys().size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode k = d.keys().get(i);
            if (k instanceof PythonNode.Starred sk) sb.append("...").append(emitExpr(sk.value()));   // {**spread}
            else sb.append("[").append(emitExpr(k)).append("]: ").append(emitExpr(d.values().get(i)));
        }
        return sb.append("})").toString();
    }

    private String emitElements(List<PythonNode> elems) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elems.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode e = elems.get(i);
            if (e instanceof PythonNode.Starred s) sb.append("...").append(emitExpr(s.value()));   // *spread
            else sb.append(emitExpr(e));
        }
        return sb.append("]").toString();
    }

    private String emitArgs(List<PythonNode> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode a = args.get(i);
            if (a instanceof PythonNode.Starred s) sb.append("...").append(emitExpr(s.value()));   // f(*args)
            else sb.append(emitExpr(a));
        }
        return sb.toString();
    }

    private String emitParams(List<Param> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            Param p = params.get(i);
            if (p.starArg()) sb.append("...");
            sb.append(jsName(p.name()));
            if (p.defaultValue() != null) sb.append(" = ").append(emitExpr(p.defaultValue()));
        }
        return sb.toString();
    }

    /** A target pattern (for-of / comprehension param): name or {@code [a, b]} destructuring. */
    private String emitTarget(PythonNode target) {
        return switch (target) {
            case PythonNode.Name n -> jsName(n.id());
            case PythonNode.TupleLit t -> {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < t.elements().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(emitTarget(t.elements().get(i)));
                }
                yield sb.append("]").toString();
            }
            default -> emitExpr(target);
        };
    }

    // ---- string helpers ----

    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private String jsTemplate(PythonNode.FString f) {
        StringBuilder sb = new StringBuilder().append('`');
        for (PythonNode part : f.parts()) {
            if (part instanceof PythonNode.StrLit lit) {
                for (int i = 0; i < lit.value().length(); i++) {
                    char c = lit.value().charAt(i);
                    switch (c) {
                        case '`' -> sb.append("\\`");
                        case '\\' -> sb.append("\\\\");
                        case '\n' -> sb.append("\\n");
                        case '\r' -> sb.append("\\r");
                        case '$' -> {
                            // escape ${ to avoid spurious interpolation
                            if (i + 1 < lit.value().length() && lit.value().charAt(i + 1) == '{') sb.append("\\$");
                            else sb.append('$');
                        }
                        default -> sb.append(c);
                    }
                }
            } else if (part instanceof PythonNode.Formatted fm) {
                sb.append("${__nekoFmt(").append(emitExpr(fm.expr())).append(", ")
                        .append(fm.spec() != null ? jsString(fm.spec()) : "null").append(", ")
                        .append(fm.conv() != null ? jsString(fm.conv()) : "null").append(")}");
            } else {
                sb.append("${").append(emitExpr(part)).append('}');
            }
        }
        return sb.append('`').toString();
    }

    // ---- indent / block helpers ----

    private void block(List<PythonNode> stmts) {
        indent++;
        for (PythonNode s : stmts) emitStmt(s);
        indent--;
    }

    private void line(String s) {
        out.append(ind()).append(s);
        // s may embed a multi-line expression string (a step-slice IIFE or a generator expression); its newlines
        // advance the generated-line count too, otherwise every later source-map mapping is off (br() adds only 1).
        jsLine += countNewlines(s);
        br();
    }

    /** Emits one line without indentation (for runtime helpers / prelude; no source-map line). */
    private void line0(String s) {
        out.append(s);
        br();
    }

    /** Appends a line terminator and advances the generated-line counter (single point of truth for jsLine). */
    private void br() {
        out.append('\n');
        jsLine++;
    }

    private static int countNewlines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    /**
     * The single exit for emission-time errors: attaches the Python source line of the current statement ({@link #curLine},
     * kept by emitStmt from srcLines; an expression-level error reports its statement's line). Without a statement
     * context the argument is returned unchanged.
     */
    private IllegalArgumentException err(String msg) {
        // Message text is unchanged; the statement's authored line now also travels as
        // NekoCompileException so preparation publishes a real authored position instead of only a
        // formatted string. Emission errors are statement-scoped, so the column is the statement
        // start — the emitter has no token-level column for the failing construct.
        return curLine > 0
                ? new NekoCompileException(msg + " (python source line " + curLine + ")", curLine, 1)
                : new IllegalArgumentException(msg);
    }

    private String ind() {
        return "  ".repeat(indent);
    }
}
