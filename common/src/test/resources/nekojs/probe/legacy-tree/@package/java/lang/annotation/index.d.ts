import { $Class } from "java:java/lang";

declare module "java:java/lang/annotation" {
    export interface $Annotation {
        annotationType(): $Class<$Annotation>;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export type $Annotation_<CallbackResult = $Class<$Annotation>> = (() => CallbackResult) | ($Annotation & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "annotationType": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
}
