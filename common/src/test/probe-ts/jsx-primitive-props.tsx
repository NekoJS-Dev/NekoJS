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
