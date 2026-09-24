import { Fragment, UI, jsx, jsxs } from "nekojs/jsx-runtime";

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
