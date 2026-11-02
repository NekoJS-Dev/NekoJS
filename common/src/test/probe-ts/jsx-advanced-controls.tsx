import { UI, jsx, Tabs, Dialog, Validators, validateForm } from "nekojs/jsx-runtime";

const tabs = <Tabs id="tabs" value="form" tabs={[
  { value: "form", label: "Form", render: () => <input id="name" value="Neko" /> },
  { value: "preview", label: "Preview", disabled: false, render: () => <item item="minecraft:diamond" count={2} /> },
]} onChange={value => { const selected: string = value; }} />;
const dialog = <Dialog id="confirm" open={true} title="Confirm" width="80%" height={{base: 100, profiles: {3: "80%"}}}
  closeOnEscape={false} closeOnBackdrop={true} onOpenChange={open => { const next: boolean = open; }}>
  <label>Confirm?</label>
</Dialog>;
const rawDialog = <dialog id="raw" onDismiss={event => { const reason: "escape" | "backdrop" | undefined = event.reason; }} />;
const icon = <item item="minecraft:stone" count={99} itemTooltip={false} tooltip="Custom description" />;
UI.element("item", {item: "minecraft:diamond", count: 1});
UI.element(Dialog, {id: "direct", open: false, onOpenChange: value => { const next: boolean = value; }});
jsx(Tabs, {id: "direct-tabs", value: "a", tabs: [{value: "a", label: "A", render: () => null}], onChange: value => { const next: string = value; }});
const result = validateForm({name: "Neko", amount: "2", enabled: false, missing: null}, {
  name: [Validators.required(), Validators.minLength(1), Validators.maxLength(24)],
  amount: [Validators.numberRange(1, 64), (value, values) => {
    const scalar: string | number | boolean | null | undefined = value;
    const other: string | number | boolean | null = values.name;
    return value === "2" ? null : "Expected two";
  }],
});
const valid: boolean = result.valid;
const messages: readonly string[] = result.errors.name;
// @ts-expect-error validation errors are immutable
result.errors.name = [];
// @ts-expect-error validation messages are immutable
result.errors.name.push("changed");
// @ts-expect-error values must be scalar script data
validateForm({nested: {name: "Neko"}}, {});
// @ts-expect-error custom validators must be synchronous
validateForm({}, {field: [async () => null]});
// @ts-expect-error custom validators cannot return booleans
validateForm({}, {field: [() => false]});
// @ts-expect-error custom validator snapshots are readonly
validateForm({name: "Neko"}, {name: [(value, values) => { values.name = "changed"; return null; }]});
// @ts-expect-error tab render must remain lazy
const eagerTab = <Tabs id="bad" value="a" tabs={[{value: "a", label: "A", render: <label>A</label>}]} onChange={()=>{}} />;
// @ts-expect-error old tab content shape is not supported
const oldTab = <Tabs id="bad" value="a" tabs={[{value: "a", label: "A", content: <label>A</label>}]} onChange={()=>{}} />;
// @ts-expect-error Tabs requires caller state callback
const missingTabCallback = <Tabs id="bad" value="a" tabs={[]} />;
// @ts-expect-error Tabs callbacks receive string values
const numericTab = <Tabs id="bad" value="a" tabs={[]} onChange={(value: number)=>{}} />;
// @ts-expect-error Dialog requires caller open state callback
const missingDialogCallback = <Dialog id="bad" open={true} />;
// @ts-expect-error stale onClose API is not supported
const oldDialog = <Dialog id="bad" open={true} onClose={()=>{}} />;
// @ts-expect-error Dialog callbacks receive boolean values
const stringDialog = <Dialog id="bad" open={true} onOpenChange={(value: string)=>{}} />;
// @ts-expect-error raw dialogs require stable ids
const missingRawId = <dialog />;
// @ts-expect-error item id must be a string, not an ItemStack-like descriptor
const itemObject = <item item={{id: "minecraft:stone"}} />;
// @ts-expect-error item descriptor is mandatory
const missingItem = <item count={2} />;
// @ts-expect-error count is numeric
const textCount = <item item="minecraft:stone" count="2" />;
// @ts-expect-error automatic tooltip flag is boolean
const textTooltip = <item item="minecraft:stone" itemTooltip="true" />;
// @ts-expect-error tooltip override remains a string
const booleanTooltip = <item item="minecraft:stone" tooltip={true} />;
// @ts-expect-error no container or slot mutation API
const slot = <item item="minecraft:stone" slot={0} />;
// @ts-expect-error UI.element preserves concrete Dialog requirements
UI.element(Dialog, {id: "bad", open: true});
// @ts-expect-error the existing UI object has no newly invented control aliases
UI.Tabs;
