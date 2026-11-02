import { Fragment, UI, jsx, jsxs, Toggle, Slider, Select } from "nekojs/jsx-runtime";

const label = <label text="ok" width={{ base: "fill", profiles: { 2: "50%" } }} />;
const row = <row gap={4} onClick={() => {}}>{label}</row>;
const Component = (props: { title: string }) => <label text={props.title} />;
const custom = <Component title="ok" />;
const grouped = <Fragment>{row}{custom}</Fragment>;

UI.element("label", { text: "ok" });
UI.element("row", { children: label });
UI.element(Component, { title: "ok" });
UI.element(Fragment, { children: grouped });
jsx("label", { text: "ok" });
jsxs("row", { children: [label, row] });
jsx(Component, { title: "ok" });
jsx(Fragment, { children: grouped });

// @ts-expect-error row has no text prop
UI.element("row", { text: "bad" });
// @ts-expect-error row has no text prop
jsx("row", { text: "bad" });
// @ts-expect-error row has no text prop
jsxs("row", { text: "bad" });
// @ts-expect-error row has no text prop
const invalid = <row text="bad" />;
// @ts-expect-error label has no scrollOffset prop
const invalidLabel = <label scrollOffset={1} />;

const visualPanel = <panel opacity={0.5} />;
const visualImage = <image resource="mymod:gui/hero" fit="contain" opacity={0.25} icon="mymod:gui/icon" crop={{ x: 1, y: 2, width: 8, height: 9 }} />;
const arrayCrop = <image resource="mymod:gui/hero" crop={[1, 2, 8, 9]} />;
const truncated = <label truncate>long text</label>;
const selectedFont = <label text="custom" font="mymod:custom" />;
const responsiveFont = <label font={{ base: 'minecraft:default', profiles: { 4: 'mymod:custom.json' } }} />;
UI.element('label', { font: { base: 'minecraft:default', profiles: { 3: 'mymod:custom' } } });
// @ts-expect-error label font must be a controlled id string or responsive string
const numericFont = <label font={12} />;
// @ts-expect-error responsive font overrides must contain strings
const invalidResponsiveFont = <label font={{ profiles: { 2: 12 } }} />;
// @ts-expect-error panel has no font selector
const invalidPanelFont = <panel font="mymod:custom" />;
UI.element('panel', { opacity: 0.5 });
UI.element('image', { icon: 'mymod:gui/icon', crop: [1, 2, 8, 9] });
// @ts-expect-error row has no opacity prop
const badOpacity = <row opacity={0.5} />;
// @ts-expect-error label has no opacity prop
const badLabelOpacity = <label opacity={0.5} />;
// @ts-expect-error image crop must be a rect shape or 4-number array
const badCrop = <image crop="nope" />;
// @ts-expect-error panel has no truncate prop
const badTruncate = <panel truncate />;

const themedButton = <button tooltip="Hint" appearance={{ background: '#123', color: 'white', hover: { color: 'red' }, disabled: { color: 0xff808080 } }} />;
const themedInput = <input value="hello" tooltip="Type" appearance={{ placeholderColor: '#abc', cursorColor: 'blue', selectionColor: 'gray', focused: { borderColor: '#fff' } }} />;
const primitiveSlider = <slider value={0.3} min={0} max={1} step={0.25} appearance={{ trackColor: 'gray', thumbColor: 'white' }} onChange={event => { const text: string | undefined = event.value; }} />;
const controlledToggle = <Toggle id="enabled" checked={true} label="Enabled" onChange={checked => { const value: boolean = checked; }} />;
const controlledSlider = <Slider id="volume" value={0.5} min={0} max={1} step={0.1} onChange={value => { const number: number = value; }} />;
const controlledSelect = <Select id="choice" value="a" options={[{value:'a',label:'Same'}, {value:'b',label:'Same',disabled:true}]} expanded={false} onExpandedChange={expanded => { const value: boolean = expanded; }} onChange={value => { const text: string = value; }} />;
UI.element(Toggle, {id:'enabled',checked:false,onChange:checked => { const value: boolean = checked; }});
jsx(Slider, {id:'volume',value:0,onChange:value => { const number: number = value; }});
jsxs(Select, {id:'choice',value:'a',options:[{value:'a',label:'A'}],expanded:false,onExpandedChange:()=>{},onChange:value => { const text: string = value; }});
// @ts-expect-error slider values are numbers, not text input strings
const stringSlider = <slider value="0.5" />;
// @ts-expect-error primitive slider event.value retains the string event contract
const numericPrimitiveHandler = <slider onChange={(event: {value:number}) => {}} />;
// @ts-expect-error nested appearance states are not color fields
const nestedAppearance = <button appearance={{ hover: { pressed: { color:'red' } } }} />;
// @ts-expect-error unknown appearance fields are rejected
const unknownAppearance = <input appearance={{ foreground:'red' }} />;
// @ts-expect-error appearance is only a control prop
const rowAppearance = <row appearance={{color:'red'}} />;
// @ts-expect-error controlled toggle needs a stable id
const missingToggleId = <Toggle checked={false} onChange={()=>{}} />;
// @ts-expect-error toggle callbacks receive a boolean
const wrongToggleHandler = <Toggle id="t" checked={false} onChange={(value:number)=>{}} />;
// @ts-expect-error wrapper slider callbacks receive a number
const wrongSliderHandler = <Slider id="s" value={0} onChange={(value:string)=>{}} />;
// @ts-expect-error wrapper slider value is mandatory
const missingSliderValue = <Slider id="s" onChange={()=>{}} />;
// @ts-expect-error Select expansion belongs to the caller
const missingExpansion = <Select id="s" value="a" options={[{value:'a',label:'A'}]} onChange={()=>{}} />;
// @ts-expect-error Select option values remain strings
const invalidOption = <Select id="s" value="a" options={[{value:1,label:'A'}]} expanded={false} onExpandedChange={()=>{}} onChange={()=>{}} />;
// @ts-expect-error UI.element preserves concrete component prop requirements
UI.element(Toggle, {checked:false,onChange:()=>{}});
// @ts-expect-error automatic JSX helper preserves concrete component callback types
jsx(Slider, {id:'s',value:0,onChange:(value:string)=>{}});
