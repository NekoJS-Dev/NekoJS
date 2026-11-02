// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const host = ClientUI.screen("Authoring counter", false);
const root = UI.createRoot(() => (
    <screen>
        <column>
            <label id="count">{String(count.get())}</label>
            <button id="increment" onClick={() => count.update(value => value + 1)}>
                Increment
            </button>
        </column>
    </screen>
), host);
host.bindRoot(root);
host.open();
