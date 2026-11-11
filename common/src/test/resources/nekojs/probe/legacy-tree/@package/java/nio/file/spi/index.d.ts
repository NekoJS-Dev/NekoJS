import { $InputStream, $OutputStream } from "java:java/io";
import { $Class } from "java:java/lang";
import { $URI } from "java:java/net";
import { $AsynchronousFileChannel, $FileChannel, $SeekableByteChannel } from "java:java/nio/channels";
import { $AccessMode, $AccessMode_, $CopyOption, $DirectoryStream, $DirectoryStream$Filter, $DirectoryStream$Filter_, $FileStore, $FileSystem, $LinkOption, $LinkOption_, $OpenOption, $Path } from "java:java/nio/file";
import { $BasicFileAttributes, $FileAttribute, $FileAttributeView, $FileAttributeView_ } from "java:java/nio/file/attribute";
import { $List, $List_, $Map, $Map_, $Set, $Set_ } from "java:java/util";
import { $ExecutorService } from "java:java/util/concurrent";

declare module "java:java/nio/file/spi" {
    export class $FileSystemProvider {
        get scheme(): string;
        getScheme(): string;
        static installedProviders(): $List<$FileSystemProvider>;
        checkAccess(arg0: $Path, arg1?: $AccessMode_[]): void;
        copy(arg0: $Path, arg1: $Path, arg2?: $CopyOption[]): void;
        createDirectory(arg0: $Path, arg1?: $FileAttribute<any>[]): void;
        createLink(arg0: $Path, arg1: $Path): void;
        createSymbolicLink(arg0: $Path, arg1: $Path, arg2?: $FileAttribute<any>[]): void;
        deleteIfExists(arg0: $Path): boolean;
        delete(arg0: $Path): void;
        exists(arg0: $Path, arg1?: $LinkOption_[]): boolean;
        getFileAttributeView<V extends $FileAttributeView>(arg0: $Path, arg1: $Class<V>, arg2?: $LinkOption_[]): V;
        getFileStore(arg0: $Path): $FileStore;
        getFileSystem(arg0: $URI): $FileSystem;
        getPath(arg0: $URI): $Path;
        isHidden(arg0: $Path): boolean;
        isSameFile(arg0: $Path, arg1: $Path): boolean;
        move(arg0: $Path, arg1: $Path, arg2?: $CopyOption[]): void;
        newAsynchronousFileChannel(
            arg0: $Path,
            arg1: $Set<$OpenOption>,
            arg2: $ExecutorService,
            arg3?: $FileAttribute<any>[]
        ): $AsynchronousFileChannel;
        newByteChannel(arg0: $Path, arg1: $Set<$OpenOption>, arg2?: $FileAttribute<any>[]): $SeekableByteChannel;
        newDirectoryStream(arg0: $Path, arg1: $DirectoryStream$Filter_<$Path, $Path, boolean>): $DirectoryStream<$Path>;
        newFileChannel(arg0: $Path, arg1: $Set<$OpenOption>, arg2?: $FileAttribute<any>[]): $FileChannel;
        newFileSystem(arg0: $URI, arg1: $Map_<string, any>): $FileSystem;
        newFileSystem(arg0: $Path, arg1: $Map_<string, any>): $FileSystem;
        newInputStream(arg0: $Path, arg1?: $OpenOption[]): $InputStream;
        newOutputStream(arg0: $Path, arg1?: $OpenOption[]): $OutputStream;
        readAttributesIfExists<A extends $BasicFileAttributes>(arg0: $Path, arg1: $Class<A>, arg2?: $LinkOption_[]): A;
        readAttributes<A extends $BasicFileAttributes>(arg0: $Path, arg1: $Class<A>, arg2?: $LinkOption_[]): A;
        readAttributes(arg0: $Path, arg1: string, arg2?: $LinkOption_[]): $Map<string, unknown>;
        readSymbolicLink(arg0: $Path): $Path;
        setAttribute(arg0: $Path, arg1: string, arg2: unknown, arg3?: $LinkOption_[]): void;
    }

}
