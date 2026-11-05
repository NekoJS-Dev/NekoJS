import { UI } from "nekojs/jsx-runtime";

// Native actions enter through bridge; localization uses the shared CLIENT Binding.
export default function createErrorDashboard(host, bridge, initialErrors) {
function t(key, ...args) { return I18n.translate("nekojs.gui.dashboard." + key, ...args); }
function display(value) {
    return value.replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\t/g, "    ")
        .replace(/§/g, "�").replace(/\0/g, "�");
}
const colors = {
    background: "#18212E", surface: "#1C2533", card: "#222D3D", selected: "#253B55",
    line: "#3B4B62", accent: "#80B9FF", text: "#E4EBF4", muted: "#A4B2C6", red: "#FF8984"
};
const buttonStyle = {
    background: "#30333A", borderColor: "#535760", color: colors.text,
    hover: { background: "#3A3F48", borderColor: "#667080" },
    focused: { borderColor: colors.accent }, pressed: { background: "#25282E" },
    disabled: { background: "#20252D", borderColor: "#343A44", color: "#6B7584" }
};
const collapseStyle = { ...buttonStyle, color: colors.accent };
const disclosureStyle = {
    background: "#00000000", borderColor: "#00000000",
    hover: { background: "#0AFFFFFF" },
    focused: { borderColor: colors.accent }, pressed: { background: "#12000000" }
};
const inputStyle = {
    background: "#111B29", borderColor: "#526B8C", color: colors.text,
    placeholderColor: "#8295AD", selectionColor: "#566EA4D1", cursorColor: colors.accent,
    focused: { borderColor: colors.accent }
};
const overlayStyle = {
    background: "#00000000", borderColor: "#00000000", color: colors.text,
    hover: { background: "#1080B9FF", borderColor: colors.accent },
    focused: { borderColor: colors.accent }, pressed: { background: "#2080B9FF" }
};
const errors = UI.createSignal(initialErrors);
const query = UI.createSignal("");
const selected = UI.createSignal(initialErrors.length ? initialErrors[0].id : null);
const collapsed = UI.createSignal(false);
const rawOpen = UI.createSignal(true);
const stackOpen = UI.createSignal(false);
const status = UI.createSignal("");
const modal = UI.createSignal(null);
const location = UI.createSignal({ id: null, available: false, opening: false, reason: t("location.no_error") });
function rowsFor(value) {
    const needle = value.trim().toLowerCase();
    return errors.get().filter(entry => [entry.id, entry.path, entry.message, entry.fullDetails]
        .some(text => text.toLowerCase().includes(needle)));
}
function refreshLocation() {
    const id = selected.get();
    location.set({ id, available: false, opening: false, reason: t(id ? "checking_location" : "location.no_error") });
    bridge.checkLocation(id);
}
function choose(entry) {
    if (!entry) return;
    UI.batch(() => {
        selected.set(entry.id); rawOpen.set(true); stackOpen.set(false); status.set("");
        refreshLocation();
    });
}
function search(value) {
    UI.batch(() => {
        query.set(value);
        const rows = rowsFor(value);
        const previous = selected.get();
        if (!rows.some(entry => entry.id === previous)) selected.set(rows.length ? rows[0].id : null);
        status.set("");
        if (previous !== selected.get()) refreshLocation();
    });
}
function moveSelection(event, entry) {
    if (event.key !== "up" && event.key !== "down") return;
    const rows = rowsFor(query.get());
    const index = rows.findIndex(row => row.id === selected.get());
    choose(rows[Math.max(0, Math.min(rows.length - 1, index + (event.key === "up" ? -1 : 1)))]);
}
function canLocate(entry) {
    const state = location.get();
    return state.id === entry.id && state.available && !state.opening;
}
function lineText(entry) { return entry.line > 0 ? t("line", entry.line) : t("unknown_line"); }
function copyText(entry, full) {
    const value = full ? entry.fullDetails : entry.path;
    const success = bridge.copy(entry.id, full);
    UI.batch(() => {
        status.set(t(success ? (full ? "copied_full" : "copied_path") : "copy_failed"));
        modal.set(success ? null : { kind: "copy", id: entry.id, full, title: t("copy_failed"),
            text: value, note: t("copy_failed_note") });
    });
}
function retryCopy(dialog) {
    const entry = errors.get().find(row => row.id === dialog.id);
    if (entry) copyText(entry, dialog.full);
    else modal.set(null);
}
function locate(entry) {
    if (!canLocate(entry)) return;
    UI.batch(() => {
        location.set({ ...location.get(), opening: true, reason: t("opening") });
        status.set(t("opening"));
    });
    bridge.openSource(entry.id);
}
function ErrorCard({ entry }) {
    const chosen = entry.id === selected.get();
    return (
        <stack coordinateSpace="design" width="fill" height={84}>
            <panel coordinateSpace="design" width="fill" height="fill" background={chosen ? colors.selected : colors.card}
                borderColor={chosen ? "#659BD5" : colors.line} borderWidth={1}>
                <row coordinateSpace="design" width="fill" height="fill">
                    <panel coordinateSpace="design" width={3} height="fill" background={chosen ? colors.accent : colors.card} />
                    <column coordinateSpace="design" padding={7} gap={4} width="fill" height="fill">
                        <label coordinateSpace="design" width="fill" height={22} color={colors.text} wrap={true}>{display(entry.path)}</label>
                        <label coordinateSpace="design" width="fill" height={22} color="#C4CEDC" wrap={true}>{display(entry.message)}</label>
                        <row coordinateSpace="design" width="fill" height={12}>
                            <label coordinateSpace="design" width="fill" color={colors.muted}>{lineText(entry)}</label>
                            <label coordinateSpace="design" width={56} color={colors.red}>{t("count", entry.count)}</label>
                        </row>
                    </column>
                </row>
            </panel>
            <button coordinateSpace="design" id={"dashboard-row:" + entry.id} width="fill" height="fill" text=""
                tooltip={entry.path + "\n" + entry.message} appearance={overlayStyle}
                onClick={() => choose(entry)} onKey={event => moveSelection(event, entry)} />
        </stack>
    );
}
function Disclosure({ name, title, open, text, onToggle }) {
    return (
        <panel coordinateSpace="design" width="fill" padding={1} background="#181A1E" borderWidth={1} borderColor="#3D424B">
            <column coordinateSpace="design" width="fill">
                <stack coordinateSpace="design" width="fill">
                    <panel coordinateSpace="design" width="fill" padding={8} background="#25282E">
                        <row coordinateSpace="design" width="fill" gap={4} align="center">
                            <label coordinateSpace="design" width={8} color={colors.accent}>{open ? "▾" : "▸"}</label>
                            <label coordinateSpace="design" width="fill" color="#C6CBD4" wrap={true}>{title}</label>
                        </row>
                    </panel>
                    <button coordinateSpace="design" id={"dashboard-toggle-" + name} width="fill" height="fill" text=""
                        tooltip={title} appearance={disclosureStyle} onClick={onToggle} />
                </stack>
                <scroll coordinateSpace="design" id={"dashboard-" + name + "-scroll"} width="fill" height="auto"
                    maxHeight={160} scrollY={true} visible={open}>
                    <panel coordinateSpace="design" id={"dashboard-" + name + "-panel"} width="fill" padding={10} background="#181A1E" visible={open}>
                        <label coordinateSpace="design" id={"dashboard-" + name + "-text"} width="fill" color="#B8BEC9" wrap={true}>{display(text)}</label>
                    </panel>
                </scroll>
            </column>
        </panel>
    );
}
function Details({ entry }) {
    const stackIndex = entry.fullDetails.search(/^[\t ]+at[\t ]+/m);
    const raw = stackIndex < 0 ? entry.fullDetails : entry.fullDetails.slice(0, stackIndex);
    const stack = stackIndex < 0 ? "" : entry.fullDetails.slice(stackIndex);
    return (
        <column coordinateSpace="design" width="fill" height="fill">
            <panel coordinateSpace="design" width="fill" padding={10} background={colors.background}>
                <row coordinateSpace="design" width="fill" gap={8} direction={"row"} align="center">
                    <column coordinateSpace="design" width="fill" gap={5}>
                        <label coordinateSpace="design" width="fill" color={colors.red}>{t("detail_eyebrow")}</label>
                        <label coordinateSpace="design" id="dashboard-detail-name" width="fill" fontSize={13} color={colors.text} wrap={true}>
                            {entry.path.split("/").pop()}
                        </label>
                    </column>
                    <row coordinateSpace="design" width={"auto"} gap={4} justify="end"
                        direction={"row"}>
                        <button coordinateSpace="design" id="dashboard-copy-path" width={64} height={22} appearance={buttonStyle}
                            onClick={() => copyText(entry, false)}>{t("copy_path_short")}</button>
                        <button coordinateSpace="design" id="dashboard-copy-full" width={64} height={22} appearance={buttonStyle}
                            onClick={() => copyText(entry, true)}>{t("copy_full_short")}</button>
                        <button coordinateSpace="design" id="dashboard-locate" width={104} height={22} appearance={buttonStyle}
                            disabled={!canLocate(entry)} tooltip={location.get().reason}
                            onClick={() => locate(entry)}>{t("vscode")}</button>
                    </row>
                </row>
            </panel>
            <panel coordinateSpace="design" width="fill" height={1} background={colors.line} />
            <scroll coordinateSpace="design" id="dashboard-detail-scroll" key={entry.id} width="fill" height="fill" scrollY={true}>
                <panel coordinateSpace="design" width="fill" minHeight="100%" padding={10} background={colors.background}>
                    <column coordinateSpace="design" width="fill" gap={8}>
                        <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{display(entry.path)}</label>
                        <row coordinateSpace="design" width="fill" gap={12}>
                            <label coordinateSpace="design" color="#B8C8DC">{lineText(entry)}</label>
                            <label coordinateSpace="design" color={colors.red}>{t("count", entry.count)}</label>
                        </row>
                        <label coordinateSpace="design" id="dashboard-detail-id" width="fill" color={colors.muted} wrap={true}>{t("error_id", entry.id)}</label>
                        <panel coordinateSpace="design" width="fill" background="#352827">
                            <row coordinateSpace="design" width="fill" align="stretch">
                                <panel coordinateSpace="design" width={2} height="fill" background={colors.red} />
                                <scroll coordinateSpace="design" id="dashboard-message-scroll" width="fill" height="auto" maxHeight={96} scrollY={true}>
                                    <column coordinateSpace="design" width="fill" padding={9}>
                                        <label coordinateSpace="design" id="dashboard-message" width="fill" color="#FFC0B8" wrap={true}>{display(entry.message)}</label>
                                    </column>
                                </scroll>
                            </row>
                        </panel>
                        <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>
                            {t(entry.fullDetails.includes(">> 异常代码片段") ? "source_note" : "no_excerpt")}
                        </label>
                        {UI.element(Disclosure, { name: "raw", title: t("raw") + " · fullDetails", open: rawOpen.get(), text: raw,
                            onToggle: () => rawOpen.set(!rawOpen.get()) })}
                        {stack ? UI.element(Disclosure, { name: "stack", title: t("stack"), open: stackOpen.get(), text: stack,
                            onToggle: () => stackOpen.set(!stackOpen.get()) }) : null}
                        <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{t("fold_note")}</label>
                    </column>
                </panel>
            </scroll>
        </column>
    );
}
function Dashboard() {
    const data = errors.get();
    const rows = rowsFor(query.get());
    const entry = rows.find(row => row.id === selected.get()) || null;
    const dialog = modal.get();
    const sidebarWidth = collapsed.get() ? 26 : "26%";
    const idle = data.length === 0 ? t("readonly_empty") : rows.length === 0 ? t("no_match") : t("readonly");
    return (
        <screen coordinateSpace="design" id="dashboard-screen" width="fill" height="fill">
            <column coordinateSpace="design" width="fill" height="fill">
                <panel coordinateSpace="design" id="dashboard-panel" width="fill" height="fill" background={colors.background}
                    borderWidth={1} borderColor="#52647D">
                    <column coordinateSpace="design" width="fill" height="fill">
                        <panel coordinateSpace="design" width="fill" background={colors.surface} padding={10} borderWidth={1} borderColor={colors.line}>
                            <row coordinateSpace="design" width="fill" gap={9} align="center">
                                <panel coordinateSpace="design" width={22} height={22} background="#26394F" borderWidth={1} borderColor="#586F8F">
                                    <column coordinateSpace="logical" width="fill" height="fill" justify="center" align="center">
                                        <panel coordinateSpace="logical" width="9%" minWidth={1} height="36%" minHeight={1} background={colors.accent} />
                                        <spacer coordinateSpace="logical" height="14%" minHeight={1} />
                                        <panel coordinateSpace="logical" width="9%" minWidth={1} height="9%" minHeight={1} background={colors.accent} />
                                    </column>
                                </panel>
                                <column coordinateSpace="design" width="fill" gap={3}>
                                    <label coordinateSpace="design" color={colors.accent} fontSize={8}>NEKOJS / ERROR SNAPSHOT</label>
                                    <label coordinateSpace="design" color={colors.text} fontSize={14}>{t("title")}</label>
                                </column>
                                <label coordinateSpace="design" id="dashboard-totals" width={170}
                                    color={colors.red} wrap={true}>{t("totals", data.length, data.reduce((sum, row) => sum + Math.max(0, row.count), 0))}</label>
                            </row>
                        </panel>
                        <row coordinateSpace="design" width="fill" height="fill">
                            <panel coordinateSpace="design" id="dashboard-sidebar" width={sidebarWidth} height="fill" background="#1B2432"
                                borderWidth={1} borderColor={colors.line}>
                                <column coordinateSpace="design" width="fill" height="fill">
                                    <row coordinateSpace="design" width="fill" height={30} padding={4} gap={4} align="center">
                                        <label coordinateSpace="design" width="fill" color={colors.muted} visible={!collapsed.get()}>{t("list_title")}</label>
                                        <label coordinateSpace="design" id="dashboard-result-count" width={44} color={colors.muted}
                                            visible={!collapsed.get()}>{t("matches", rows.length, data.length)}</label>
                                        <button coordinateSpace="design" id="dashboard-collapse" width={18} height={22} appearance={collapseStyle}
                                            tooltip={t(collapsed.get() ? "expand" : "collapse")}
                                            onClick={() => collapsed.set(!collapsed.get())}>{collapsed.get() ? "›" : "‹"}</button>
                                    </row>
                                    <column coordinateSpace="design" width="fill" height="fill" visible={!collapsed.get()}>
                                        <row coordinateSpace="design" width="fill" height={30} padding={4} gap={4}>
                                            <input coordinateSpace="design" id="dashboard-search" width="fill" height={22} value={query.get()}
                                                maxLength={4096} placeholder={t("search")} appearance={inputStyle}
                                                onChange={event => search(event.value || "")} />
                                            <button coordinateSpace="design" id="dashboard-clear" width={38} height={22} disabled={!query.get()}
                                                appearance={buttonStyle} onClick={() => search("")}>{t("clear_short")}</button>
                                        </row>
                                        <scroll coordinateSpace="design" id="dashboard-list-scroll" width="fill" height="fill" scrollY={true}>
                                            <column coordinateSpace="design" width="fill" padding={5} gap={6}>
                                                {rows.map(row => UI.element(ErrorCard, { key: row.id, entry: row }))}
                                            </column>
                                        </scroll>
                                    </column>
                                </column>
                            </panel>
                            <column coordinateSpace="design" id="dashboard-detail-pane" width="fill" height="fill">
                                {entry ? <Details key={entry.id} entry={entry} /> :
                                    <column coordinateSpace="design" id="dashboard-empty" width="fill" height="fill" padding={16} gap={10}>
                                        <label coordinateSpace="design" width="fill" fontSize={14} color={colors.text} wrap={true}>{t(data.length ? "no_match" : "empty")}</label>
                                        <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{t(data.length ? "no_match_note" : "empty_note")}</label>
                                        {data.length ? <button coordinateSpace="design" id="dashboard-empty-clear" width={120} height={22}
                                            appearance={buttonStyle} onClick={() => search("")}>{t("clear")}</button> : null}
                                    </column>}
                            </column>
                        </row>
                        <panel coordinateSpace="design" width="fill" background={colors.surface} padding={6} borderWidth={1} borderColor={colors.line}>
                            <row coordinateSpace="design" width="fill" gap={8}>
                                <label coordinateSpace="design" id="dashboard-status" width="fill" color={colors.accent} wrap={true}>{status.get() || idle}</label>
                                <label coordinateSpace="design" color={colors.muted} visible={true}>{t("keys")}</label>
                            </row>
                        </panel>
                    </column>
                </panel>
            </column>
            {dialog ? <dialog coordinateSpace="design" id="dashboard-dialog" title={dialog.title} onDismiss={() => modal.set(null)}>
                <panel coordinateSpace="design" width="85%" maxWidth={470} height="65%" anchor="center" padding={12}
                    background="#1A2636" borderColor="#627D9F" borderWidth={1}>
                    <column coordinateSpace="design" width="fill" height="fill" gap={10}>
                        <label coordinateSpace="design" width="fill" fontSize={14} color={colors.text} wrap={true}>{dialog.title}</label>
                        <scroll coordinateSpace="design" width="fill" height="fill" scrollY={true}>
                            <column coordinateSpace="design" width="fill" gap={10}>
                                <label coordinateSpace="design" width="fill" color={colors.accent} wrap={true}>{dialog.text}</label>
                                <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{dialog.note}</label>
                            </column>
                        </scroll>
                        <row coordinateSpace="design" width="fill" height={22} gap={6}>
                            {dialog.kind === "copy" ? <button coordinateSpace="design" id="dashboard-copy-retry" width="fill" height="fill"
                                appearance={buttonStyle} onClick={() => retryCopy(dialog)}>{t("copy_retry")}</button> : null}
                            <button coordinateSpace="design" id="dashboard-dialog-close" width="fill" height="fill" appearance={buttonStyle}
                                onClick={() => modal.set(null)}>{t("dismiss")}</button>
                        </row>
                    </column>
                </panel>
            </dialog> : null}
        </screen>
    );
}
function dashboardViewport(viewport) {
    return { width: viewport.width, height: viewport.height, designWidth: 1280, designHeight: 720 };
}
const mounted = UI.createRoot(Dashboard, host, { viewport: dashboardViewport(host.viewport()) });
const root = Object.freeze({ ...mounted, resize: viewport => mounted.resize(dashboardViewport(viewport)) });
return Object.freeze({
    root,
    refreshLocation,
    textInput(codepoint, inputFocused) {
        if (codepoint !== 47 || inputFocused || modal.get() != null) return false;
        collapsed.set(false);
        return bridge.focus("dashboard-search");
    },
    update(snapshot) {
        if (root.isDisposed()) return;
        UI.batch(() => {
            const previous = selected.get();
            errors.set(snapshot);
            const rows = rowsFor(query.get());
            if (!rows.some(row => row.id === previous)) selected.set(rows.length ? rows[0].id : null);
            if (previous !== selected.get()) {
                rawOpen.set(true); stackOpen.set(false); modal.set(null); status.set("");
            }
            refreshLocation();
        });
    },
    locationResult(id, available, reason) {
        if (root.isDisposed() || id !== selected.get()) return;
        location.set({ id, available, opening: false, reason: t(reason) });
    },
    openResult(id, accepted, lineRequested, reason) {
        if (root.isDisposed() || id !== selected.get()) return;
        UI.batch(() => {
            const text = t(accepted ? (lineRequested ? "dispatch_accepted" : "dispatch_file_only") : reason);
            status.set(text);
            if (!accepted) modal.set({ kind: "open", title: t("open_failed"), text, note: t("open_failed_note") });
        });
    }
});
}
