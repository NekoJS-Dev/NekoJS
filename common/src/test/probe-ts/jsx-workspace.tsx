import { UI, type NekoUiEventName, type NekoUiHostAdapter, type NekoUiRootHandle } from "nekojs/jsx-runtime";
import { createWorkspace } from "nekojs/jsx-workspace";

declare const host: NekoUiHostAdapter & {
  bindRoot(root: NekoUiRootHandle): void;
  open(): void;
  close(): boolean;
};
const workspace = createWorkspace(host, {id: "desktop", viewport: {width: 640, height: 360}, closeWhenEmpty: false});
const accepted: boolean | "queued" = workspace.add("notes", () => <label>Notes</label>, {
  title: "Notes", rect: {x: 16, y: 24, width: 200, height: 160}, draggable: true, closable: true,
  resizable: true, minimizable: true, maximizable: true, minWidth: 120, minHeight: 80, maxWidth: 420, maxHeight: 260,
  responsiveRect: {x: "10%", width: {base: "50%", profiles: {5: "70%"}}},
  titlebarHeight: 28, titlebarButtonWidth: 24, titlebarTitle: false,
});
workspace.update("notes", {rect: {x: 32}, render: () => <button>Save</button>});
workspace.hide("notes");
workspace.show("notes");
workspace.activate("notes");
workspace.open();
const minimized: boolean | "queued" = workspace.minimize("notes");
const maximized: boolean | "queued" = workspace.maximize("notes");
const restored: boolean | "queued" = workspace.restore("notes");
const resized: boolean | "queued" = workspace.resize("notes", {width: 280, height: 180});
const mode: "normal" | "minimized" | "maximized" = workspace.snapshot().windows[0].mode;
// @ts-expect-error window state is read-only
workspace.snapshot().windows[0].mode = "normal";
// @ts-expect-error resize dimensions remain numeric GUI geometry
workspace.resize("notes", {width: "50%"});
const policy = workspace.snapshot().windows[0].responsiveRect;
if (policy != null && typeof policy.width === "object" && policy.width.profiles) {
  // @ts-expect-error published responsive profile branches are immutable
  policy.width.profiles[5] = "33%";
}
const active: string | null = workspace.snapshot().activeId;
const requestedX: number = workspace.snapshot().windows[0].rect.x;
const effectiveX: number | undefined = workspace.layout()?.nodes[0].rect.x;
workspace.closeWindow("notes");
const closed: boolean | "queued" = workspace.close();
const disposed: boolean = workspace.isDisposed();
// @ts-expect-error snapshot window ordering is immutable
workspace.snapshot().windows.push(workspace.snapshot().windows[0]);
// @ts-expect-error snapshots cannot mutate requested geometry
workspace.snapshot().windows[0].rect.x = 12;
// @ts-expect-error patches require numeric logical geometry
workspace.update("notes", {rect: {x: "12"}});
// @ts-expect-error unknown configuration is rejected
workspace.add("bad", () => null, {minimized: true});
// @ts-expect-error managed host close must report completion, not void
createWorkspace({...host, close() {}});
// @ts-expect-error render remains a lazy function, not an eager vnode
workspace.add("bad", <label>Bad</label>);
const root = UI.createRoot(() => <label>Root</label>, host);
const updating: boolean = root.isUpdating();
const detached: void = root.forceClose();
const rawWindow = <window id="native-window" title="Window" x={0} y={0} width={120} height={80}
  onDismiss={event => { const reason: "escape" | "backdrop" | "close" | undefined = event.reason; }} />;
root.dispatch("native-window", "dismiss", {reason: "close"});
const resizeEventName: NekoUiEventName = "onResize";
const restoreEventName: NekoUiEventName = "onRestore";
const managedRawWindow = <window id="managed-native" title="Managed" x={0} y={0} width={200} height={120}
  mode="maximized" resizable minimizable maximizable titlebarHeight={28} titlebarButtonWidth={24} titlebarTitle={false}
  responsiveRect={{width: {base: "50%", profiles: {5: "75%"}}, height: "60%"}} minWidth={100} maxHeight={240}
  onResize={event => {
    const width: number = event.width;
    const edge: "n" | "ne" | "e" | "se" | "s" | "sw" | "w" | "nw" = event.edge;
  }} onMinimize={() => {}} onMaximize={() => {}} onRestore={() => {}} />;
workspace.update("notes", {responsiveRect: null});
// @ts-expect-error responsive rectangles do not accept automatic dimensions
workspace.update("notes", {responsiveRect: {width: "auto"}});
// @ts-expect-error titlebar dimensions are numeric GUI pixels
workspace.update("notes", {titlebarHeight: "28"});
// @ts-expect-error raw windows never publish a minimized mode; their controller hides them
const invalidRawMode = <window mode="minimized" />;
const taskbarWorkspace = createWorkspace(host, {minimizedPresentation: "taskbar", taskbarHeight: 28});
const floatingWorkspace = createWorkspace(host, {minimizedPresentation: "floating"});
const customWorkspace = createWorkspace(host, {minimizedPresentation: ({windows, restore, activate}) =>
  windows.length === 0 ? null : <panel width="100%" height={28} anchor="bottom">
    {windows.map(window => <button key={window.id} id={`demo-workspace-restore:${window.id}`} onClick={() => restore(window.id)}>{window.title}</button>)}
  </panel>});
// @ts-expect-error custom minimized presentation returns a panel vnode or null, not text
createWorkspace(host, {minimizedPresentation: () => "icon"});
// @ts-expect-error minimized presentation is a preset or a renderer, not a geometry object
createWorkspace(host, {minimizedPresentation: {height: 28}});
