import { UI, jsx, type NekoUiCallback, type NekoUiComponent, type NekoUiHostAdapter, type NekoUiRenderable } from "nekojs/jsx-runtime";
import { createWorkspace } from "nekojs/jsx-workspace";

declare const host: NekoUiHostAdapter;

const counter = UI.createSignal(1);
counter.update(current => {
  const value: number = current;
  return value + 1;
});
// @ts-expect-error Signal updaters receive the signal's value type.
counter.update((current: string) => current.length);
// @ts-expect-error Signal updaters must return the same value type.
counter.update(current => String(current));
// @ts-expect-error Signal updates are synchronous values, not promises.
counter.update(async current => current + 1);

const state = UI.createStore({ count: 1, title: "Ready" });
state.update("count", current => {
  const value: number = current;
  return value + 1;
});
state.update("title", current => current.toUpperCase());
const title: string = state.get("title");
const count: number = state.snapshot().count;
// @ts-expect-error A known store key preserves its value type in the updater result.
state.update("count", current => String(current));
// @ts-expect-error A known store key preserves its value type in assignments.
state.set("title", 4);
// @ts-expect-error Inferred stores expose their declared keys.
state.get("missing");
const dynamic = UI.createStore<Record<string, unknown>>({});
dynamic.set("later", 1);
dynamic.update("later", current => typeof current === "number" ? current + 1 : 0);

const Badge: NekoUiComponent<{ title: string }> = props => <label text={props.title} />;
const badge = <Badge title="Ready" />;
UI.element(Badge, { title: "Ready" });
jsx(Badge, { title: "Ready" });
// @ts-expect-error Component props stay concrete through UI.element.
UI.element(Badge, { title: 3 });
// @ts-expect-error Component props stay concrete through the automatic factory.
jsx(Badge, { title: 3 });
// @ts-expect-error Typed components must return renderable values.
const invalidComponent: NekoUiComponent<{ title: string }> = props => ({ title: props.title });
const Invalid = () => ({ message: "not renderable" });
// @ts-expect-error UI.element rejects components returning arbitrary objects.
UI.element(Invalid, {});
// @ts-expect-error JSX rejects components returning arbitrary objects.
const invalidJsx = <Invalid />;
const Text = () => "text";
const text = <Text />;
const List: NekoUiComponent<{ values: readonly string[] }> = props => props.values.map(value => <label text={value} />);
const list = <List values={["one", "two"]} />;
const renderable: NekoUiRenderable = [badge, text, list, false, null, undefined];
UI.createRoot(() => renderable, host);
UI.createRoot(() => undefined, host);
// @ts-expect-error Root render must return a renderable value, not an arbitrary object.
UI.createRoot(() => ({ message: "invalid" }), host);
// @ts-expect-error Missing render return values are void, not an explicit empty render.
UI.createRoot(() => {}, host);
// @ts-expect-error Rendering does not await promises.
UI.createRoot(async () => <screen />, host);
// @ts-expect-error JSX children must be renderable values.
const invalidChildren = <row>{{ message: "invalid" }}</row>;

declare const workspaceHost: Parameters<typeof createWorkspace>[0];
const workspace = createWorkspace(workspaceHost);
workspace.add("valid", () => ["text", <label />]);
// @ts-expect-error Workspace render shares the runtime render return contract.
workspace.add("invalid", () => ({ message: "invalid" }));
// @ts-expect-error Workspace rendering does not await promises.
workspace.add("async", async () => <label />);

const result: { count: number } = UI.batch(() => ({ count: 1 }));
host.enqueue(() => {});
const action: NekoUiCallback = () => {};
host.enqueue(action);
// @ts-expect-error Queued actions receive no positional arguments.
host.enqueue((value: number) => {});
void title; void count; void result; void invalidComponent; void invalidJsx; void invalidChildren;
