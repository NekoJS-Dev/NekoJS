import { $Appendable, $AutoCloseable, $AutoCloseable_, $CharSequence, $Comparable, $Comparable_ } from "java:java/lang";
import { $URI, $URL } from "java:java/net";
import { $Charset } from "java:java/nio/charset";
import { $Path } from "java:java/nio/file";
import { $Locale } from "java:java/util";

declare module "java:java/io" {
    export interface $Closeable extends $AutoCloseable {
        close(): void;
    }

    export class $File implements $Serializable, $Comparable<$File> {
        constructor(arg0: string, arg1: string);
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: $URI);
        static pathSeparator: string;
        static pathSeparatorChar: string;
        static separator: string;
        static separatorChar: string;
        get absoluteFile(): $File;
        getAbsoluteFile(): $File;
        get absolutePath(): string;
        getAbsolutePath(): string;
        get canonicalFile(): $File;
        getCanonicalFile(): $File;
        get canonicalPath(): string;
        getCanonicalPath(): string;
        get freeSpace(): number;
        getFreeSpace(): number;
        get name(): string;
        getName(): string;
        get parentFile(): $File;
        getParentFile(): $File;
        get parent(): string;
        getParent(): string;
        get path(): string;
        getPath(): string;
        get totalSpace(): number;
        getTotalSpace(): number;
        get usableSpace(): number;
        getUsableSpace(): number;
        get absolute(): boolean;
        isAbsolute(): boolean;
        get directory(): boolean;
        isDirectory(): boolean;
        get file(): boolean;
        isFile(): boolean;
        get hidden(): boolean;
        isHidden(): boolean;
        static createTempFile(arg0: string, arg1: string, arg2: string): $File;
        static createTempFile(arg0: string, arg1: string): $File;
        static listRoots(): $File[];
        canExecute(): boolean;
        canRead(): boolean;
        canWrite(): boolean;
        compareTo(arg0: string): number;
        createNewFile(): boolean;
        deleteOnExit(): void;
        delete(): boolean;
        equals(arg0: object): boolean;
        exists(): boolean;
        hashCode(): number;
        lastModified(): number;
        length(): number;
        listFiles(arg0: $FileFilter): $File[];
        listFiles(arg0: $FilenameFilter): $File[];
        listFiles(): $File[];
        list(arg0: $FilenameFilter): string[];
        list(): string[];
        mkdirs(): boolean;
        mkdir(): boolean;
        renameTo(arg0: string): boolean;
        setExecutable(arg0: boolean, arg1: boolean): boolean;
        setExecutable(arg0: boolean): boolean;
        setLastModified(arg0: number): boolean;
        setReadOnly(): boolean;
        setReadable(arg0: boolean, arg1: boolean): boolean;
        setReadable(arg0: boolean): boolean;
        setWritable(arg0: boolean, arg1: boolean): boolean;
        setWritable(arg0: boolean): boolean;
        toPath(): $Path;
        toString(): string;
        toURI(): $URI;
        toURL(): $URL;
    }

    export class $FilterOutputStream extends $OutputStream {
        constructor(arg0: $OutputStream);
        close(): void;
        flush(): void;
        write(arg0: number[], arg1: number, arg2: number): void;
        write(arg0: number[]): void;
        write(arg0: number): void;
    }

    export class $InputStream implements $Closeable {
        constructor();
        static nullInputStream(): $InputStream;
        available(): number;
        close(): void;
        markSupported(): boolean;
        mark(arg0: number): void;
        readAllBytes(): number[];
        readNBytes(arg0: number[], arg1: number, arg2: number): number;
        readNBytes(arg0: number): number[];
        read(arg0: number[], arg1: number, arg2: number): number;
        read(arg0: number[]): number;
        read(): number;
        reset(): void;
        skipNBytes(arg0: number): void;
        skip(arg0: number): number;
        transferTo(arg0: $OutputStream): number;
    }

    export class $OutputStream implements $Closeable, $Flushable {
        constructor();
        static nullOutputStream(): $OutputStream;
        close(): void;
        flush(): void;
        write(arg0: number[], arg1: number, arg2: number): void;
        write(arg0: number[]): void;
        write(arg0: number): void;
    }

    export class $PrintStream extends $FilterOutputStream implements $Appendable, $Closeable {
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: $Charset);
        constructor(arg0: $OutputStream);
        constructor(arg0: $OutputStream, arg1: boolean);
        constructor(arg0: $OutputStream, arg1: boolean, arg2: string);
        constructor(arg0: $OutputStream, arg1: boolean, arg2: $Charset);
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: $Charset);
        append(arg0: string): $PrintStream;
        append(arg0: $CharSequence, arg1: number, arg2: number): $PrintStream;
        append(arg0: $CharSequence): $PrintStream;
        charset(): $Charset;
        checkError(): boolean;
        close(): void;
        flush(): void;
        format(arg0: string, arg1?: object[]): $PrintStream;
        format(arg0: $Locale, arg1: string, arg2?: object[]): $PrintStream;
        printf(arg0: string, arg1?: object[]): $PrintStream;
        printf(arg0: $Locale, arg1: string, arg2?: object[]): $PrintStream;
        println(arg0: boolean): void;
        println(arg0: string[]): void;
        println(arg0: string): void;
        println(arg0: number): void;
        println(arg0: number): void;
        println(arg0: number): void;
        println(arg0: object): void;
        println(arg0: string): void;
        println(arg0: number): void;
        println(): void;
        print(arg0: boolean): void;
        print(arg0: string[]): void;
        print(arg0: string): void;
        print(arg0: number): void;
        print(arg0: number): void;
        print(arg0: number): void;
        print(arg0: object): void;
        print(arg0: string): void;
        print(arg0: number): void;
        writeBytes(arg0: number[]): void;
        write(arg0: number[], arg1: number, arg2: number): void;
        write(arg0: number[]): void;
        write(arg0: number): void;
    }

    export class $PrintWriter extends $Writer {
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: $Charset);
        constructor(arg0: $OutputStream);
        constructor(arg0: $OutputStream, arg1: boolean);
        constructor(arg0: $OutputStream, arg1: boolean, arg2: $Charset);
        constructor(arg0: $Writer);
        constructor(arg0: $Writer, arg1: boolean);
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: $Charset);
        append(arg0: string): $PrintWriter;
        append(arg0: $CharSequence, arg1: number, arg2: number): $PrintWriter;
        append(arg0: $CharSequence): $PrintWriter;
        checkError(): boolean;
        close(): void;
        flush(): void;
        format(arg0: string, arg1?: object[]): $PrintWriter;
        format(arg0: $Locale, arg1: string, arg2?: object[]): $PrintWriter;
        printf(arg0: string, arg1?: object[]): $PrintWriter;
        printf(arg0: $Locale, arg1: string, arg2?: object[]): $PrintWriter;
        println(arg0: boolean): void;
        println(arg0: string[]): void;
        println(arg0: string): void;
        println(arg0: number): void;
        println(arg0: number): void;
        println(arg0: number): void;
        println(arg0: object): void;
        println(arg0: string): void;
        println(arg0: number): void;
        println(): void;
        print(arg0: boolean): void;
        print(arg0: string[]): void;
        print(arg0: string): void;
        print(arg0: number): void;
        print(arg0: number): void;
        print(arg0: number): void;
        print(arg0: object): void;
        print(arg0: string): void;
        print(arg0: number): void;
        write(arg0: string[], arg1: number, arg2: number): void;
        write(arg0: string[]): void;
        write(arg0: number): void;
        write(arg0: string, arg1: number, arg2: number): void;
        write(arg0: string): void;
    }

    export interface $Serializable {
    }

    export class $Writer implements $Appendable, $Closeable, $Flushable {
        static nullWriter(): $Writer;
        append(arg0: string): $Writer;
        append(arg0: $CharSequence, arg1: number, arg2: number): $Writer;
        append(arg0: $CharSequence): $Writer;
        close(): void;
        flush(): void;
        write(arg0: string[], arg1: number, arg2: number): void;
        write(arg0: string[]): void;
        write(arg0: number): void;
        write(arg0: string, arg1: number, arg2: number): void;
        write(arg0: string): void;
    }

    export type $Closeable_ = (() => void) | ($Closeable & { readonly [Symbol.hasInstance]?: never });
}
