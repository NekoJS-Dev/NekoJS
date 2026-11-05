// PROTOTYPE: standalone NeoForge 26.x CLIENT JSX rendering of blue-a-2.
// The four reference fixtures match the HTML; numbered text is an additional local acceptance sample.
import { UI } from "nekojs/jsx-runtime";

const normal = JSON.parse("[{\"id\":\"nekojs:server/events/player_tick.js\",\"path\":\"server_scripts/events/player_tick.js\",\"line\":42,\"count\":3,\"message\":\"TypeError: Cannot read properties of null (reading 'id')\",\"fullDetails\":\"环境: SERVER\\n脚本: server_scripts/events/player_tick.js\\n频次: 连续发生了 3 次\\n\\n>> 异常代码片段 (行 42, 列 11):\\n > 42 | const playerId = event.player.id\\n     |           ^\\n\\nTypeError: Cannot read properties of null (reading 'id')\\n    at onPlayerTick (server_scripts/events/player_tick.js:42)\\n\"},{\"id\":\"nekojs:server/commands/example.js\",\"path\":\"server_scripts/commands/example.js\",\"line\":18,\"count\":1,\"message\":\"SyntaxError: Unexpected token '}'\",\"fullDetails\":\"环境: SERVER\\n脚本: server_scripts/commands/example.js\\n\\n>> 异常代码片段 (行 18, 列 1):\\n > 18 | }\\n     | ^\\n\\nSyntaxError: Unexpected token '}'\\n\"},{\"id\":\"nekojs:rt/SERVER/timer/1a2b3c\",\"path\":\"timer/1a2b3c\",\"line\":-1,\"count\":7,\"message\":\"ReferenceError: missingBinding is not defined\",\"fullDetails\":\"环境: SERVER\\n脚本: timer/1a2b3c\\n频次: 连续发生了 7 次\\n\\nReferenceError: missingBinding is not defined\\n    at <anonymous> (Unknown Source)\\n\"}]");
const longPath='server_scripts/'+('very_long_directory_name/').repeat(12)+'超长脚本名称.js';
const longMessage='Error: '+('这是一段用于检查换行与滚动的长消息。').repeat(32);
const scenarios={normal,empty:[],missing:[{...normal[2],path:'Unknown location',fullDetails:'环境: SERVER\n脚本: Unknown location\n\nReferenceError: missingBinding is not defined\n    at <anonymous> (Unknown Source)\n'}],long:[{id:'nekojs:server/long-example.js',path:longPath,line:128,count:12,message:longMessage,fullDetails:'环境: SERVER\n脚本: '+longPath+'\n\n'+longMessage+'\n'+Array.from({length:60},(_,i)=>'    at callback'+i+' ('+longPath+':128)').join('\n')}]};

const rawMessage = "Error: long sample message\n"
    + Array.from({ length: 80 }, (_, index) => "Error message line " + String(index).padStart(3, "0") + ": read-only sample message.").join("\n");
const rawFixture = scenarios.long[0].fullDetails.replace(longMessage, rawMessage);
const rawStackAt = rawFixture.search(/^[\t ]+at[\t ]+/m);
scenarios.raw = [{ ...scenarios.long[0], message: rawMessage, fullDetails: rawFixture.slice(0, rawStackAt)
    + Array.from({ length: 160 }, (_, index) => "Raw snapshot line " + String(index).padStart(3, "0") + ": read-only sample details.").join("\n")
    + "\n" + rawFixture.slice(rawStackAt) }];

const prefix = "nekojs.prototype.dashboard.";
const locales = {
    en_us: {
        controls: "Prototype controls / sample snapshots",
        normal: "Errors", long: "Long text", raw: "Long raw", missing: "No source", empty: "Empty",
        hide: "Hide panel", reopen: "Reopen panel", keys: "Tab navigate / Esc close",
        locateTitle: "VS Code location preview", locatePlan: "Example target: %s:%s",
        locateNote: "Example only. A real opener must verify the local file. Remote and virtual locations cannot be mapped directly. No external program is started.",
        locateStatus: "Location plan shown / VS Code was not started",
        copyRetry: "Try copying again", errorId: "ID: %s"
    },
    zh_cn: {
        controls: "原型控制 / 演示快照",
        normal: "错误列表", long: "长文本", raw: "长原文", missing: "缺少源码", empty: "空列表",
        hide: "收起面板", reopen: "重新打开", keys: "Tab 导航 / Esc 关闭",
        locateTitle: "VS Code 定位演示", locatePlan: "示例定位目标：%s:%s",
        locateNote: "仅为示例。实际打开前必须验证本地文件，远端和虚拟位置不能直接映射。本操作不会启动外部程序。",
        locateStatus: "已显示定位计划 / 未启动 VS Code",
        copyRetry: "重新复制", errorId: "ID：%s"
    }
};
for (const language of Object.keys(locales)) {
    ClientEvents.lang(language, event => {
        for (const key of Object.keys(locales[language])) event.add(prefix + key, locales[language][key]);
    });
}
function t(key, ...args) {
    return Component.translatable("nekojs.gui.dashboard." + key, ...args).string;
}
function p(key, ...args) {
    return Component.translatableWithFallback(prefix + key, locales.en_us[key], ...args).string;
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
const selectedButtonStyle = {
    ...buttonStyle, background: colors.selected, borderColor: colors.accent, color: colors.accent,
    hover: { background: "#2D4663", borderColor: colors.accent }
};
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
const scenario = UI.createSignal("normal");
const scenarioNames = ["normal", "long", "raw", "missing", "empty"];
const query = UI.createSignal("");
const selected = UI.createSignal(normal[0].id);
const collapsed = UI.createSignal(false);
const panelOpen = UI.createSignal(true);
const rawOpen = UI.createSignal(true);
const stackOpen = UI.createSignal(false);
const status = UI.createSignal("");
const modal = UI.createSignal(null);
const allowedLocations = new Set([normal[0].path, normal[1].path, longPath]);

function rowsFor(value) {
    const needle = value.trim().toLocaleLowerCase();
    const snapshot = scenarios[scenario.get()];
    return snapshot.filter(entry => {
        const fields = [entry.id, entry.path, entry.message, entry.fullDetails];
        return fields.some(text => text.toLocaleLowerCase().includes(needle));
    });
}
function choose(entry) {
    UI.batch(() => {
        selected.set(entry.id);
        rawOpen.set(true);
        stackOpen.set(false);
        status.set("");
    });
}
function search(value) {
    UI.batch(() => {
        query.set(value);
        const rows = rowsFor(value);
        if (!rows.some(entry => entry.id === selected.get())) selected.set(rows.length ? rows[0].id : null);
        status.set("");
    });
}
function changeScenario(value) {
    UI.batch(() => {
        scenario.set(value);
        query.set("");
        selected.set(scenarios[value].length ? scenarios[value][0].id : null);
        rawOpen.set(true);
        stackOpen.set(false);
        panelOpen.set(true);
        modal.set(null);
        status.set("");
    });
}
function moveSelection(event, entry) {
    if (event.key !== "up" && event.key !== "down") return;
    const rows = rowsFor(query.get());
    const index = rows.findIndex(row => row.id === entry.id);
    choose(rows[Math.max(0, Math.min(rows.length - 1, index + (event.key === "up" ? -1 : 1)))]);
}
function canLocate(entry) {
    return allowedLocations.has(entry.path) && Number.isInteger(entry.line) && entry.line > 0;
}
function lineText(entry) { return entry.line > 0 ? t("line", entry.line) : t("unknown_line"); }
function copyText(value, full) {
    const client = Minecraft.getInstance();
    try {
        client.keyboardHandler.clipboard = value;
        if (client.keyboardHandler.clipboard !== value) {
            status.set(t("copy_failed"));
            modal.set({ kind: "copy", title: t("copy_failed"), text: value, full });
            return;
        }
        status.set(t(full ? "copied_full" : "copied_path"));
        modal.set(null);
    } catch (failure) {
        status.set(t("copy_failed"));
        modal.set({ kind: "copy", title: t("copy_failed"), text: value, full });
    }
}
function locate(entry) {
    if (!canLocate(entry)) return;
    modal.set({ kind: "locate", title: p("locateTitle"), text: p("locatePlan", entry.path, entry.line) });
    status.set(p("locateStatus"));
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
                        <label coordinateSpace="design" width="fill" height={22} color={colors.text} wrap={true}>{entry.path}</label>
                        <label coordinateSpace="design" width="fill" height={22} color="#C4CEDC" wrap={true}>{entry.message}</label>
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
                        <label coordinateSpace="design" id={"dashboard-" + name + "-text"} width="fill" color="#B8BEC9" wrap={true}>{text}</label>
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
                            onClick={() => copyText(entry.path, false)}>{t("copy_path_short")}</button>
                        <button coordinateSpace="design" id="dashboard-copy-full" width={64} height={22} appearance={buttonStyle}
                            onClick={() => copyText(entry.fullDetails, true)}>{t("copy_full_short")}</button>
                        <button coordinateSpace="design" id="dashboard-locate" width={104} height={22} appearance={buttonStyle}
                            disabled={!canLocate(entry)} tooltip={canLocate(entry) ? p("locateTitle") : t("location.invalid_path")}
                            onClick={() => locate(entry)}>{t("vscode")}</button>
                    </row>
                </row>
            </panel>
            <panel coordinateSpace="design" width="fill" height={1} background={colors.line} />
            <scroll coordinateSpace="design" id="dashboard-detail-scroll" key={entry.id} width="fill" height="fill" scrollY={true}>
                <panel coordinateSpace="design" width="fill" minHeight="100%" padding={10} background={colors.background}>
                    <column coordinateSpace="design" width="fill" gap={8}>
                        <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{entry.path}</label>
                        <row coordinateSpace="design" width="fill" gap={12}>
                            <label coordinateSpace="design" color="#B8C8DC">{lineText(entry)}</label>
                            <label coordinateSpace="design" color={colors.red}>{t("count", entry.count)}</label>
                        </row>
                        <label coordinateSpace="design" id="dashboard-detail-id" width="fill" color={colors.muted} wrap={true}>{p("errorId", entry.id)}</label>
                        <panel coordinateSpace="design" width="fill" background="#352827">
                            <row coordinateSpace="design" width="fill" align="stretch">
                                <panel coordinateSpace="design" width={2} height="fill" background={colors.red} />
                                <scroll coordinateSpace="design" id="dashboard-message-scroll" width="fill" height="auto" maxHeight={96} scrollY={true}>
                                    <column coordinateSpace="design" width="fill" padding={9}>
                                        <label coordinateSpace="design" id="dashboard-message" width="fill" color="#FFC0B8" wrap={true}>{entry.message}</label>
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
    const data = scenarios[scenario.get()];
    const rows = rowsFor(query.get());
    const entry = rows.find(row => row.id === selected.get()) || null;
    const dialog = modal.get();
    const sidebarWidth = collapsed.get() ? 26 : "26%";
    const idle = data.length === 0 ? t("readonly_empty") : rows.length === 0 ? t("no_match") : t("readonly");
    return (
        <screen coordinateSpace="design" id="dashboard-screen" width="fill" height="fill">
            <column coordinateSpace="design" width="fill" height="fill">
                <panel coordinateSpace="design" id="dashboard-panel" width="fill" height={panelOpen.get() ? "fill" : 0} background={colors.background}
                    borderWidth={1} borderColor="#52647D" visible={panelOpen.get()}>
                    <column coordinateSpace="design" width="fill" height="fill">
                        <panel coordinateSpace="design" width="fill" background={colors.surface} padding={10} borderWidth={1} borderColor={colors.line}>
                            <row coordinateSpace="design" width="fill" gap={9} align="center">
                                <panel coordinateSpace="design" width={22} height={22} background="#26394F" borderWidth={1} borderColor="#586F8F">
                                    <row coordinateSpace="design" width="fill" height="fill" justify="center" align="center">
                                        <label coordinateSpace="design" fontSize={14} color={colors.accent}>!</label>
                                    </row>
                                </panel>
                                <column coordinateSpace="design" width="fill" gap={3}>
                                    <label coordinateSpace="design" color={colors.accent} fontSize={8}>NEKOJS / ERROR SNAPSHOT</label>
                                    <label coordinateSpace="design" color={colors.text} fontSize={14}>{t("title")}</label>
                                </column>
                                <label coordinateSpace="design" id="dashboard-totals" width={170}
                                    color={colors.red} wrap={true}>{t("totals", data.length, data.reduce((sum, row) => sum + row.count, 0))}</label>
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
                                                maxLength={512} placeholder={t("search")} appearance={inputStyle}
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
                                <label coordinateSpace="design" color={colors.muted} visible={true}>{p("keys")}</label>
                            </row>
                        </panel>
                    </column>
                </panel>
                <spacer coordinateSpace="design" width="fill" height={panelOpen.get() ? 0 : "fill"} visible={!panelOpen.get()} />
                <panel coordinateSpace="design" id="dashboard-prototype-controls" width="fill" padding={4} background="#171F2B" borderColor="#52647D" borderWidth={1}>
                    <column coordinateSpace="design" width="fill" gap={4}>
                        <label coordinateSpace="design" color={colors.muted} fontSize={8}>{p("controls") + " · " + p(scenario.get())}</label>
                        <row coordinateSpace="design" width="fill" height={22} gap={4}>
                            {scenarioNames.map(value =>
                                <button coordinateSpace="design" id={"dashboard-scenario-" + value} key={value} width="fill" height="fill"
                                    appearance={scenario.get() === value ? selectedButtonStyle : buttonStyle} onClick={() => changeScenario(value)}>{p(value)}</button>)}
                            <button coordinateSpace="design" id="dashboard-reopen" width="fill" height="fill" appearance={buttonStyle}
                                onClick={() => panelOpen.set(!panelOpen.get())}>{p(panelOpen.get() ? "hide" : "reopen")}</button>
                        </row>
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
                                <label coordinateSpace="design" width="fill" color={colors.muted} wrap={true}>{dialog.kind === "locate" ? p("locateNote") : t("copy_failed_note")}</label>
                            </column>
                        </scroll>
                        <row coordinateSpace="design" width="fill" height={22} gap={6}>
                            {dialog.kind === "copy" ? <button coordinateSpace="design" id="dashboard-copy-retry" width="fill" height="fill"
                                appearance={buttonStyle} onClick={() => copyText(dialog.text, dialog.full)}>{p("copyRetry")}</button> : null}
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
let host = null;
let root = null;
export function openDashboardPrototype() {
    if (root && !root.isDisposed()) { host.open(); return root; }
    host = ClientUI.screen(t("title"), false);
    const mounted = UI.createRoot(Dashboard, host, { viewport: dashboardViewport(host.viewport()) });
    // Keep the design canvas on native resize; the host reports only the current logical dimensions.
    root = Object.freeze({ ...mounted, resize: viewport => mounted.resize(dashboardViewport(viewport)) });
    host.bindRoot(root);
    host.open();
    return root;
}
// Startup only registers the entry; native UI construction waits until a player is ready.
ClientEvents.loggedIn(() => openDashboardPrototype());
const client = Minecraft.getInstance();
if (client && client.player) openDashboardPrototype();
