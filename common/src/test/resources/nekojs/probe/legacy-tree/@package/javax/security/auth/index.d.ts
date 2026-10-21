import { $Serializable } from "java:java/io";
import { $Class } from "java:java/lang";
import { $AccessControlContext, $Principal, $Principal_, $PrivilegedAction, $PrivilegedAction_, $PrivilegedExceptionAction, $PrivilegedExceptionAction_ } from "java:java/security";
import { $Set } from "java:java/util";
import { $Callable, $Callable_ } from "java:java/util/concurrent";

declare module "java:javax/security/auth" {
    export class $Subject implements $Serializable {
        constructor();
        constructor(arg0: boolean, arg1: $Set<$Principal>, arg2: $Set<any>, arg3: $Set<any>);
        get principals(): $Set<$Principal>;
        getPrincipals(): $Set<$Principal>;
        get privateCredentials(): $Set<unknown>;
        getPrivateCredentials(): $Set<unknown>;
        get publicCredentials(): $Set<unknown>;
        getPublicCredentials(): $Set<unknown>;
        get readOnly(): boolean;
        isReadOnly(): boolean;
        static callAs<T>(arg0: $Subject, arg1: $Callable_<T, T>): T;
        static current(): $Subject;
        static doAsPrivileged<T>(arg0: $Subject, arg1: $PrivilegedAction_<T, T>, arg2: $AccessControlContext): T;
        static doAsPrivileged<T>(arg0: $Subject, arg1: $PrivilegedExceptionAction_<T, T>, arg2: $AccessControlContext): T;
        static doAs<T>(arg0: $Subject, arg1: $PrivilegedAction_<T, T>): T;
        static doAs<T>(arg0: $Subject, arg1: $PrivilegedExceptionAction_<T, T>): T;
        static getSubject(arg0: $AccessControlContext): $Subject;
        equals(arg0: unknown): boolean;
        getPrincipals<T extends $Principal>(arg0: $Class<T>): $Set<T>;
        getPrivateCredentials<T>(arg0: $Class<T>): $Set<T>;
        getPublicCredentials<T>(arg0: $Class<T>): $Set<T>;
        hashCode(): number;
        setReadOnly(): void;
        toString(): string;
    }

}
