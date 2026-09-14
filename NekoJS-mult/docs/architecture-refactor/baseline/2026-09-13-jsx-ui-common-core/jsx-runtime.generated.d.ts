declare module 'nekojs/jsx-runtime' {
  export type NekoJsxElement = globalThis.NekoJsxElement;
  export const Fragment: unique symbol;
  export function jsx(type: unknown, props: Record<string, unknown> | null, key?: string | number): NekoJsxElement;
  export function jsxs(type: unknown, props: Record<string, unknown> | null, key?: string | number): NekoJsxElement;
}
