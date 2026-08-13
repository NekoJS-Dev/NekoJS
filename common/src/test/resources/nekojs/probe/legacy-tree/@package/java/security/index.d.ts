import { $Serializable } from "java:java/io";
import { $ClassLoader, $String } from "java:java/lang";
import { $URL } from "java:java/net";
import { $Certificate } from "java:java/security/cert";
import { $Enumeration } from "java:java/util";
import { $Stream } from "java:java/util/stream";

declare module "java:java/security" {
    export class $ProtectionDomain {
        constructor(arg0: $CodeSource, arg1: $PermissionCollection);
        constructor(arg0: $CodeSource, arg1: $PermissionCollection, arg2: $ClassLoader, arg3: $Principal[]);
        get classLoader(): $ClassLoader;
        getClassLoader(): $ClassLoader;
        get codeSource(): $CodeSource;
        getCodeSource(): $CodeSource;
        get permissions(): $PermissionCollection;
        getPermissions(): $PermissionCollection;
        get principals(): $Principal[];
        getPrincipals(): $Principal[];
        implies(arg0: $Permission): boolean;
        staticPermissionsOnly(): boolean;
        toString(): string;
    }

    export class $CodeSource implements $Serializable {
        constructor(arg0: $URL, arg1: $CodeSigner[]);
        constructor(arg0: $URL, arg1: $Certificate[]);
        get certificates(): $Certificate[];
        getCertificates(): $Certificate[];
        get codeSigners(): $CodeSigner[];
        getCodeSigners(): $CodeSigner[];
        get location(): $URL;
        getLocation(): $URL;
        equals(arg0: object): boolean;
        hashCode(): number;
        implies(arg0: $CodeSource): boolean;
        toString(): string;
    }

    export class $PermissionCollection implements $Serializable {
        constructor();
        get readOnly(): boolean;
        isReadOnly(): boolean;
        add(arg0: $Permission): void;
        elementsAsStream(): $Stream<$Permission>;
        elements(): $Enumeration<$Permission>;
        implies(arg0: $Permission): boolean;
        setReadOnly(): void;
        toString(): string;
    }

    export class $Permission implements $Guard, $Serializable {
        constructor(arg0: string);
        get actions(): string;
        getActions(): string;
        get name(): string;
        getName(): string;
        checkGuard(arg0: object): void;
        equals(arg0: object): boolean;
        hashCode(): number;
        implies(arg0: $Permission): boolean;
        newPermissionCollection(): $PermissionCollection;
        toString(): string;
    }

}
