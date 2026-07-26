




        add(arg0: $Permission): void;
        checkGuard(arg0: object): void;
        constructor();
        constructor(arg0: $CodeSource, arg1: $PermissionCollection);
        constructor(arg0: $CodeSource, arg1: $PermissionCollection, arg2: $ClassLoader, arg3: $Principal[]);
        constructor(arg0: $URL, arg1: $Certificate[]);
        constructor(arg0: $URL, arg1: $CodeSigner[]);
        constructor(arg0: string);
        elements(): $Enumeration<$Permission>;
        elementsAsStream(): $Stream<$Permission>;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        get actions(): string;
        get certificates(): $Certificate[];
        get classLoader(): $ClassLoader;
        get codeSigners(): $CodeSigner[];
        get codeSource(): $CodeSource;
        get location(): $URL;
        get name(): string;
        get permissions(): $PermissionCollection;
        get principals(): $Principal[];
        get readOnly(): boolean;
        getActions(): string;
        getCertificates(): $Certificate[];
        getClassLoader(): $ClassLoader;
        getCodeSigners(): $CodeSigner[];
        getCodeSource(): $CodeSource;
        getLocation(): $URL;
        getName(): string;
        getPermissions(): $PermissionCollection;
        getPrincipals(): $Principal[];
        hashCode(): number;
        hashCode(): number;
        implies(arg0: $CodeSource): boolean;
        implies(arg0: $Permission): boolean;
        implies(arg0: $Permission): boolean;
        implies(arg0: $Permission): boolean;
        isReadOnly(): boolean;
        newPermissionCollection(): $PermissionCollection;
        setReadOnly(): void;
        staticPermissionsOnly(): boolean;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
    export class $CodeSource implements $Serializable {
    export class $Permission implements $Guard, $Serializable {
    export class $PermissionCollection implements $Serializable {
    export class $ProtectionDomain {
    }
    }
    }
    }
declare module "java:java/security" {
import { $Certificate } from "java:java/security/cert";
import { $ClassLoader, $String } from "java:java/lang";
import { $Enumeration } from "java:java/util";
import { $Serializable } from "java:java/io";
import { $Stream } from "java:java/util/stream";
import { $URL } from "java:java/net";
}