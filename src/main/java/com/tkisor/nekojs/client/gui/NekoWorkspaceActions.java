package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.components.NekoMenuBar;
import com.tkisor.nekojs.client.gui.components.NekoContextMenu.MenuItem;
import com.tkisor.nekojs.client.gui.components.NekoTabbedEditor;
import com.tkisor.nekojs.client.gui.components.NekoToast;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.*;
import net.minecraft.util.Util;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
            Path path = NekoJSPaths.ROOT.resolve(tab.path);
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
        ClientPacketDistributor.sendToServer(new SaveScriptPacket(tabbedEditor.getActiveTab().path, tabbedEditor.getActiveTab().editor.getValue()));
        toast.show(I18n.get("nekojs.gui.toast.pushing_current"));
    }

    public static void syncDownloadCurrent(NekoTabbedEditor tabbedEditor, NekoToast toast) {
        if (tabbedEditor == null || tabbedEditor.getActiveTab() == null) {
            toast.show(I18n.get("nekojs.gui.toast.error.no_file_open")); return;
        }
        ClientPacketDistributor.sendToServer(new FetchScriptRequestPacket(tabbedEditor.getActiveTab().path));
        toast.show(I18n.get("nekojs.gui.toast.pulling_current"));
    }

    public static void syncUploadAll(NekoToast toast) {
        toast.show(I18n.get("nekojs.gui.toast.pushing_all"));
        Map<String, String> localFiles = NekoJSNetwork.collectAllValidScripts(NekoJSPaths.ROOT);
        if (localFiles.isEmpty()) {
            toast.show(I18n.get("nekojs.gui.toast.error.empty_dir")); return;
        }
        ClientPacketDistributor.sendToServer(new UploadAllScriptsPacket(localFiles));
    }

    public static void syncDownloadAll(NekoToast toast) {
        toast.show(I18n.get("nekojs.gui.toast.pulling_all"));
        ClientPacketDistributor.sendToServer(new FetchAllScriptsRequestPacket());
    }

    public static void openLocalDir() {
        Util.getPlatform().openFile(NekoJSPaths.ROOT.toFile());
    }
}