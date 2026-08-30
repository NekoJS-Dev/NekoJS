// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.components.NekoMenuBar;
import com.tkisor.nekojs.client.gui.components.NekoContextMenu.MenuItem;
import com.tkisor.nekojs.client.gui.components.NekoTabbedEditor;
import com.tkisor.nekojs.client.gui.components.NekoToast;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.*;
import net.minecraft.client.resources.language.I18n;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;

public class NekoWorkspaceActions {

    public static NekoMenuBar createSharedMenuBar(Supplier<NekoTabbedEditor> editorSupplier, NekoToast toast, Runnable onRefresh, Runnable onClose) {
        return new NekoMenuBar(List.of(
                new NekoMenuBar.MenuCategory(I18n.get("nekojs.gui.menu.file"), List.of(
                        new MenuItem(I18n.get("nekojs.gui.menu.file.save"), () -> saveTab(editorSupplier.get() != null ? editorSupplier.get().getActiveTab() : null, toast)),
                        new MenuItem(I18n.get("nekojs.gui.menu.file.open_external"), NekoWorkspaceActions::openLocalDir),
                        new MenuItem(I18n.get("nekojs.gui.menu.file.exit"), onClose)
                )),
                new NekoMenuBar.MenuCategory(I18n.get("nekojs.gui.menu.view"), List.of(
                        new MenuItem(I18n.get("nekojs.gui.menu.view.refresh"), () -> {
                            if (onRefresh != null) onRefresh.run();
                            toast.show(I18n.get("nekojs.gui.toast.refresh_success"));
                        })
                )),
                new NekoMenuBar.MenuCategory(I18n.get("nekojs.gui.menu.sync"), List.of(
                        new MenuItem(I18n.get("nekojs.gui.menu.sync.push_current"), () -> syncUploadCurrent(editorSupplier.get(), toast)),
                        new MenuItem(I18n.get("nekojs.gui.menu.sync.pull_current"), () -> syncDownloadCurrent(editorSupplier.get(), toast)),
                        new MenuItem(I18n.get("nekojs.gui.menu.sync.push_all"), () -> syncUploadAll(toast)),
                        new MenuItem(I18n.get("nekojs.gui.menu.sync.pull_all"), () -> syncDownloadAll(toast))
                ))
        ));
    }

    public static void saveTab(NekoTabbedEditor.Tab tab, NekoToast toast) {
        if (tab == null || tab.editor == null) {
            toast.show(I18n.get("nekojs.gui.toast.error.no_file_open")); return;
        }
        try {
            Path path = NekoJSPaths.get().verifyScriptSyncPath(tab.path);
            Files.writeString(path, tab.editor.getValue());
            toast.show(I18n.get("nekojs.gui.toast.save_success", tab.path));
            tab.editor.markSaved();
        } catch (Exception e) {
            toast.show(I18n.get("nekojs.gui.toast.save_fail", e.getMessage()));
        }
    }

    public static void syncUploadCurrent(NekoTabbedEditor tabbedEditor, NekoToast toast) {
        if (tabbedEditor == null || tabbedEditor.getActiveTab() == null) {
            toast.show(I18n.get("nekojs.gui.toast.error.no_file_open")); return;
        }
        PacketDistributor.sendToServer(new SaveScriptPacket(tabbedEditor.getActiveTab().path, tabbedEditor.getActiveTab().editor.getValue()));
        toast.show(I18n.get("nekojs.gui.toast.pushing_current"));
    }

    public static void syncDownloadCurrent(NekoTabbedEditor tabbedEditor, NekoToast toast) {
        if (tabbedEditor == null || tabbedEditor.getActiveTab() == null) {
            toast.show(I18n.get("nekojs.gui.toast.error.no_file_open")); return;
        }
        PacketDistributor.sendToServer(new FetchScriptRequestPacket(tabbedEditor.getActiveTab().path));
        toast.show(I18n.get("nekojs.gui.toast.pulling_current"));
    }

    public static void syncUploadAll(NekoToast toast) {
        toast.show(I18n.get("nekojs.gui.toast.pushing_all"));
        Map<String, String> localFiles = ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.get().root());
        if (localFiles.isEmpty()) {
            toast.show(I18n.get("nekojs.gui.toast.error.empty_dir")); return;
        }
        PacketDistributor.sendToServer(new UploadAllScriptsPacket(localFiles));
    }

    public static void syncDownloadAll(NekoToast toast) {
        toast.show(I18n.get("nekojs.gui.toast.pulling_all"));
        PacketDistributor.sendToServer(new FetchAllScriptsRequestPacket());
    }

    public static void openLocalDir() {
        Util.getPlatform().openUri(NekoJSPaths.get().root().toUri());
    }
}
