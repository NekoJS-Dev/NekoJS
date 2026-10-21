import { $Serializable } from "java:java/io";
import { $Provider, $PublicKey } from "java:java/security";
import { $Iterator, $List } from "java:java/util";

declare module "java:java/security/cert" {
    export class $Certificate implements $Serializable {
        get encoded(): number[];
        getEncoded(): number[];
        get publicKey(): $PublicKey;
        getPublicKey(): $PublicKey;
        get type(): string;
        getType(): string;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
        verify(arg0: $PublicKey, arg1: string): void;
        verify(arg0: $PublicKey, arg1: $Provider): void;
        verify(arg0: $PublicKey): void;
    }

    export class $CertPath implements $Serializable {
        get certificates(): $List<$Certificate>;
        getCertificates(): $List<$Certificate>;
        get encoded(): number[];
        getEncoded(): number[];
        get encodings(): $Iterator<string>;
        getEncodings(): $Iterator<string>;
        get type(): string;
        getType(): string;
        equals(arg0: unknown): boolean;
        getEncoded(arg0: string): number[];
        hashCode(): number;
        toString(): string;
    }

}
