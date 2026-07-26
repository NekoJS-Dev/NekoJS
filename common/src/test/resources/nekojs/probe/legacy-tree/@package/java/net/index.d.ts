






        addRequestProperty(arg0: string, arg1: string): void;
        address(): $SocketAddress;
        compareTo(arg0: $URI): number;
        compareTo(arg0: object): number;
        connect(): void;
        constructor();
        constructor(arg0: $Proxy$Type, arg1: $SocketAddress);
        constructor(arg0: $URL, arg1: string);
        constructor(arg0: $URL, arg1: string, arg2: $URLStreamHandler);
        constructor(arg0: string);
        constructor(arg0: string);
        constructor(arg0: string, arg1: string, arg2: number, arg3: string);
        constructor(arg0: string, arg1: string, arg2: number, arg3: string, arg4: $URLStreamHandler);
        constructor(arg0: string, arg1: string, arg2: string);
        constructor(arg0: string, arg1: string, arg2: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: number, arg4: string, arg5: string, arg6: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: string);
        constructor(arg0: string, arg1: string, arg2: string, arg3: string, arg4: string);
        createURLStreamHandler(arg0: string): $URLStreamHandler;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        get absolute(): boolean;
        get allowUserInteraction(): boolean;
        get authority(): string;
        get authority(): string;
        get connectTimeout(): number;
        get content(): object;
        get content(): object;
        get contentEncoding(): string;
        get contentLength(): number;
        get contentLengthLong(): number;
        get contentType(): string;
        get date(): number;
        get defaultPort(): number;
        get defaultUseCaches(): boolean;
        get doInput(): boolean;
        get doOutput(): boolean;
        get expiration(): number;
        get file(): string;
        get fragment(): string;
        get headerFields(): $Map<string, $List<string>>;
        get host(): string;
        get host(): string;
        get ifModifiedSince(): number;
        get inputStream(): $InputStream;
        get lastModified(): number;
        get opaque(): boolean;
        get outputStream(): $OutputStream;
        get path(): string;
        get path(): string;
        get permission(): $Permission;
        get port(): number;
        get port(): number;
        get protocol(): string;
        get query(): string;
        get query(): string;
        get rawAuthority(): string;
        get rawFragment(): string;
        get rawPath(): string;
        get rawQuery(): string;
        get rawSchemeSpecificPart(): string;
        get rawUserInfo(): string;
        get readTimeout(): number;
        get ref(): string;
        get requestProperties(): $Map<string, $List<string>>;
        get scheme(): string;
        get schemeSpecificPart(): string;
        get uRL(): $URL;
        get useCaches(): boolean;
        get userInfo(): string;
        get userInfo(): string;
        getAllowUserInteraction(): boolean;
        getAuthority(): string;
        getAuthority(): string;
        getConnectTimeout(): number;
        getContent(): object;
        getContent(): object;
        getContent(arg0: $Class<any>[]): object;
        getContent(arg0: $Class<any>[]): object;
        getContentEncoding(): string;
        getContentLength(): number;
        getContentLengthLong(): number;
        getContentType(): string;
        getDate(): number;
        getDefaultPort(): number;
        getDefaultUseCaches(): boolean;
        getDoInput(): boolean;
        getDoOutput(): boolean;
        getExpiration(): number;
        getFile(): string;
        getFragment(): string;
        getHeaderField(arg0: number): string;
        getHeaderField(arg0: string): string;
        getHeaderFieldDate(arg0: string, arg1: number): number;
        getHeaderFieldInt(arg0: string, arg1: number): number;
        getHeaderFieldKey(arg0: number): string;
        getHeaderFieldLong(arg0: string, arg1: number): number;
        getHeaderFields(): $Map<string, $List<string>>;
        getHost(): string;
        getHost(): string;
        getIfModifiedSince(): number;
        getInputStream(): $InputStream;
        getLastModified(): number;
        getOutputStream(): $OutputStream;
        getPath(): string;
        getPath(): string;
        getPermission(): $Permission;
        getPort(): number;
        getPort(): number;
        getProtocol(): string;
        getQuery(): string;
        getQuery(): string;
        getRawAuthority(): string;
        getRawFragment(): string;
        getRawPath(): string;
        getRawQuery(): string;
        getRawSchemeSpecificPart(): string;
        getRawUserInfo(): string;
        getReadTimeout(): number;
        getRef(): string;
        getRequestProperties(): $Map<string, $List<string>>;
        getRequestProperty(arg0: string): string;
        getScheme(): string;
        getSchemeSpecificPart(): string;
        getURL(): $URL;
        getUseCaches(): boolean;
        getUserInfo(): string;
        getUserInfo(): string;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        isAbsolute(): boolean;
        isOpaque(): boolean;
        normalize(): $URI;
        openConnection(): $URLConnection;
        openConnection(arg0: $Proxy): $URLConnection;
        openStream(): $InputStream;
        parseServerAuthority(): $URI;
        relativize(arg0: $URI): $URI;
        resolve(arg0: $URI): $URI;
        resolve(arg0: string): $URI;
        sameFile(arg0: $URL): boolean;
        set allowUserInteraction(value: boolean);
        set connectTimeout(value: number);
        set defaultUseCaches(value: boolean);
        set doInput(value: boolean);
        set doOutput(value: boolean);
        set ifModifiedSince(value: number);
        set readTimeout(value: number);
        set useCaches(value: boolean);
        setRequestProperty(arg0: string, arg1: string): void;
        static NO_PROXY: $Proxy;
        static create(arg0: string): $URI;
        static getDefaultAllowUserInteraction(): boolean;
        static getDefaultRequestProperty(arg0: string): string;
        static getDefaultUseCaches(arg0: string): boolean;
        static getFileNameMap(): $FileNameMap;
        static guessContentTypeFromName(arg0: string): string;
        static guessContentTypeFromStream(arg0: $InputStream): string;
        static of(arg0: $URI, arg1: $URLStreamHandler): $URL;
        static setContentHandlerFactory(arg0: $ContentHandlerFactory): void;
        static setDefaultAllowUserInteraction(arg0: boolean): void;
        static setDefaultRequestProperty(arg0: string, arg1: string): void;
        static setDefaultUseCaches(arg0: string, arg1: boolean): void;
        static setFileNameMap(arg0: $FileNameMap): void;
        static setURLStreamHandlerFactory(arg0: $URLStreamHandlerFactory): void;
        toASCIIString(): string;
        toExternalForm(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toURI(): $URI;
        toURL(): $URL;
        type(): $Proxy$Type;
    export class $Proxy {
    export class $URI implements $Comparable, $Serializable {
    export class $URL implements $Serializable {
    export class $URLConnection {
    export class $URLStreamHandler {
    export interface $URLStreamHandlerFactory {
    }
    }
    }
    }
    }
    }
declare module "java:java/net" {
import { $Class, $Comparable, $String } from "java:java/lang";
import { $InputStream, $OutputStream, $Serializable } from "java:java/io";
import { $List, $Map } from "java:java/util";
import { $Permission } from "java:java/security";
}