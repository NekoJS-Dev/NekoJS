import {
    $CommunityProbeTypes$Api as Api,
    $CommunityProbeTypes$Mapper as Mapper,
    $CommunityProbeTypes$Mapper_ as MapperInput,
    $CommunityProbeTypes$StringMapper_ as StringMapperInput,
    $CommunityProbeTypes$Payload as Payload,
    $CommunityProbeTypes$RpcBuilder as RpcBuilder,
} from 'java:com/tkisor/nekojs/probe/testfixture';

declare const api: Api;
declare const hostMapper: Mapper<Payload, string>;
api.consume(value => value.getName().toUpperCase());
api.consume(hostMapper);
api.produce(() => 'converted input');
api.produce(() => new Payload());
api.nested(() => () => new Payload());
api.nested(() => () => 'converted input');
api.infer(() => new Payload()).getName();
api.infer(() => 'inferred').toUpperCase();
// @ts-expect-error Caller method type variables preserve callback return inference.
api.infer(() => 123).toUpperCase();
// @ts-expect-error Nested callback results retain the Payload input contract.
api.nested(() => () => 123);
api.inherited(value => value.toUpperCase());
api.wildcard(value => value.getLabel().toUpperCase());
api.raw(value => String(value));
const standalone: MapperInput<Payload, string> = value => value.getName();
const standaloneInherited: StringMapperInput = value => value.toUpperCase();
api.consume(standalone);
api.inherited(standaloneInherited);
// @ts-expect-error Standalone alias defaults preserve host parameter types.
const wrongStandalone: MapperInput<Payload, string> = value => value.toUpperCase();
// @ts-expect-error Standalone inherited aliases preserve their resolved parameter type.
const wrongInherited: StringMapperInput = value => value.toFixed();
// @ts-expect-error Contravariant callback parameters preserve the concrete host value.
api.wildcard(value => value.toUpperCase());
// @ts-expect-error Callback arguments are host objects, not adapter input strings.
api.consume(value => value.toUpperCase());
// @ts-expect-error The callback returns a string.
api.consume(value => value.getName().length);
// @ts-expect-error Supplier result must be accepted by the Payload adapter.
api.produce(() => 123);
// @ts-expect-error Inherited SAM arguments have been resolved to string.
api.inherited(value => value.toFixed());

declare const rpc: RpcBuilder;
rpc.schema({ msg: 'string', count: 'int' }).returns('string').fn(({ msg, count }) => {
    const text: string = msg;
    const value: number = count;
    return text.repeat(value);
});
rpc.returns('int').schema({ msg: 'string' }).fn(({ msg }) => msg.length);
// @ts-expect-error Schema labels must be supported.
rpc.schema({ msg: 'unknown' });
// @ts-expect-error Declared string return rejects a number callback result.
rpc.schema({ msg: 'string' }).returns('string').fn(({ msg }) => msg.length);
rpc.schema({ msg: 'string' }).fn(({ msg }) => {
    // @ts-expect-error Schema inference preserves the field type.
    const wrong: number = msg;
    void wrong;
});
