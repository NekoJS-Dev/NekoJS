import { $InputStream, $OutputStream, $Serializable } from "java:java/io";
import { $Class, $Comparable, $String } from "java:java/lang";
import { $Permission } from "java:java/security";
import { $List, $Map } from "java:java/util";

declare module "java:java/net" {
    export class $Proxy {
        constructor(arg0: $Proxy$Type, arg1: $SocketAddress);
        static NO_PROXY: $Proxy;
        address(): $SocketAddress;
        equals(arg0: object): boolean;
        hashCode(): number;
        toString(): string;
        type(): $Proxy$Type;
    }

    export class $URI implements $Comparable, $Serializable {
        constructor(arg0: string);
        constructor(arg0: string, arg1: string, arg2: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: number, arg4: string, arg5: string, arg6: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: string, arg4: string);
        get authority(): string;
        getAuthority(): string;
        get fragment(): string;
        getFragment(): string;
        get host(): string;
        getHost(): string;
        get path(): string;
        getPath(): string;
        get port(): number;
        getPort(): number;
        get query(): string;
        getQuery(): string;
        get rawAuthority(): string;
        getRawAuthority(): string;
        get rawFragment(): string;
        getRawFragment(): string;
        get rawPath(): string;
        getRawPath(): string;
        get rawQuery(): string;
        getRawQuery(): string;
        get rawSchemeSpecificPart(): string;
        getRawSchemeSpecificPart(): string;
        get rawUserInfo(): string;
        getRawUserInfo(): string;
        get schemeSpecificPart(): string;
        getSchemeSpecificPart(): string;
        get scheme(): string;
        getScheme(): string;
        get userInfo(): string;
        getUserInfo(): string;
        get absolute(): boolean;
        isAbsolute(): boolean;
        get opaque(): boolean;
        isOpaque(): boolean;
        static create(arg0: string): $URI;
        compareTo(arg0: object): number;
        compareTo(arg0: $URI): number;
        equals(arg0: object): boolean;
        hashCode(): number;
        normalize(): $URI;
        parseServerAuthority(): $URI;
        relativize(arg0: $URI): $URI;
        resolve(arg0: string): $URI;
        resolve(arg0: $URI): $URI;
        toASCIIString(): string;
        toString(): string;
        toURL(): $URL;
    }

    export class $URL implements $Serializable {
        constructor(arg0: string);
        constructor(arg0: string, arg1: string, arg2: number, arg3: string);
        constructor(arg0: string, arg1: string, arg2: number, arg3: string, arg4: $URLStreamHandler);
        constructor(arg0: string, arg1: string, arg2: string);
        constructor(arg0: $URL, arg1: string);
        constructor(arg0: $URL, arg1: string, arg2: $URLStreamHandler);
        get authority(): string;
        getAuthority(): string;
        get content(): object;
        getContent(): object;
        get defaultPort(): number;
        getDefaultPort(): number;
        get file(): string;
        getFile(): string;
        get host(): string;
        getHost(): string;
        get path(): string;
        getPath(): string;
        get port(): number;
        getPort(): number;
        get protocol(): string;
        getProtocol(): string;
        get query(): string;
        getQuery(): string;
        get ref(): string;
        getRef(): string;
        get userInfo(): string;
        getUserInfo(): string;
        static of(arg0: $URI, arg1: $URLStreamHandler): $URL;
        static setURLStreamHandlerFactory(arg0: $URLStreamHandlerFactory): void;
        equals(arg0: object): boolean;
        getContent(arg0: $Class<any>[]): object;
        hashCode(): number;
        openConnection(arg0: $Proxy): $URLConnection;
        openConnection(): $URLConnection;
        openStream(): $InputStream;
        sameFile(arg0: $URL): boolean;
        toExternalForm(): string;
        toString(): string;
        toURI(): $URI;
    }

    export class $URLConnection {
        get allowUserInteraction(): boolean;
        getAllowUserInteraction(): boolean;
        set allowUserInteraction(value: boolean);
        get connectTimeout(): number;
        getConnectTimeout(): number;
        set connectTimeout(value: number);
        get contentEncoding(): string;
        getContentEncoding(): string;
        get contentLengthLong(): number;
        getContentLengthLong(): number;
        get contentLength(): number;
        getContentLength(): number;
        get contentType(): string;
        getContentType(): string;
        get content(): object;
        getContent(): object;
        get date(): number;
        getDate(): number;
        get defaultUseCaches(): boolean;
        getDefaultUseCaches(): boolean;
        set defaultUseCaches(value: boolean);
        get doInput(): boolean;
        getDoInput(): boolean;
        set doInput(value: boolean);
        get doOutput(): boolean;
        getDoOutput(): boolean;
        set doOutput(value: boolean);
        get expiration(): number;
        getExpiration(): number;
        get headerFields(): $Map<string, $List<string>>;
        getHeaderFields(): $Map<string, $List<string>>;
        get ifModifiedSince(): number;
        getIfModifiedSince(): number;
        set ifModifiedSince(value: number);
        get inputStream(): $InputStream;
        getInputStream(): $InputStream;
        get lastModified(): number;
        getLastModified(): number;
        get outputStream(): $OutputStream;
        getOutputStream(): $OutputStream;
        get permission(): $Permission;
        getPermission(): $Permission;
        get readTimeout(): number;
        getReadTimeout(): number;
        set readTimeout(value: number);
        get requestProperties(): $Map<string, $List<string>>;
        getRequestProperties(): $Map<string, $List<string>>;
        get uRL(): $URL;
        getURL(): $URL;
        get useCaches(): boolean;
        getUseCaches(): boolean;
        set useCaches(value: boolean);
        static getDefaultAllowUserInteraction(): boolean;
        static getDefaultRequestProperty(arg0: string): string;
        static getDefaultUseCaches(arg0: string): boolean;
        static getFileNameMap(): $FileNameMap;
        static guessContentTypeFromName(arg0: string): string;
        static guessContentTypeFromStream(arg0: $InputStream): string;
        static setContentHandlerFactory(arg0: $ContentHandlerFactory): void;
        static setDefaultAllowUserInteraction(arg0: boolean): void;
        static setDefaultRequestProperty(arg0: string, arg1: string): void;
        static setDefaultUseCaches(arg0: string, arg1: boolean): void;
        static setFileNameMap(arg0: $FileNameMap): void;
        addRequestProperty(arg0: string, arg1: string): void;
        connect(): void;
        getContent(arg0: $Class<any>[]): object;
        getHeaderFieldDate(arg0: string, arg1: number): number;
        getHeaderFieldInt(arg0: string, arg1: number): number;
        getHeaderFieldKey(arg0: number): string;
        getHeaderFieldLong(arg0: string, arg1: number): number;
        getHeaderField(arg0: number): string;
        getHeaderField(arg0: string): string;
        getRequestProperty(arg0: string): string;
        setRequestProperty(arg0: string, arg1: string): void;
        toString(): string;
    }

    export class $URLStreamHandler {
        constructor();
    }

    export interface $URLStreamHandlerFactory {
        createURLStreamHandler(arg0: string): $URLStreamHandler;
    }

}
