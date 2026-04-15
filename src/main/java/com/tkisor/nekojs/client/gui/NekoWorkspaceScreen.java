package com.tkisor.nekojs.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.client.gui.components.*;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NekoWorkspaceScreen extends Screen {

    private NekoTabbedEditor tabbedEditor;
    private FileListWidget fileListWidget;
    private EditBox searchBox;
    private EditBox replaceBox;

    private boolean isMatchCase = false;
    private boolean isMatchWord = false;
    private boolean isRegex = false;
    private boolean isPreserveCase = false;

    private List<String> allLocalFiles = new ArrayList<>();
    private Map<String, String> fileContentCache = new HashMap<>();

    private FileNode treeRoot;
    private FileNode searchRoot;
    private String selectedFilePath = null;
    private int selectedMatchStart = -1;

    private boolean isSidebarOpen = true;
    private int activeActivity = 0;

    private double listScrollX = 0;
    private int maxListContentWidth = 0;

    private final int actBarW = 24;
    private int sidebarW;
    private int leftW;
    private int rightX;
    private final int topY = 20;

    private final NekoToast toast = new NekoToast();
    private NekoModal modal;
    private NekoMenuBar menuBar;
    private NekoContextMenu activeContextMenu = null;

    public NekoWorkspaceScreen() {
        super(Component.translatable("nekojs.gui.workspace.title"));
    }

    private static boolean hasShiftDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    protected void init() {
        super.init();
        if (this.modal == null) {
            this.modal = new NekoModal(this.font, () -> this.setFocused(null));
        }
        if (this.menuBar == null) {
            this.menuBar = NekoWorkspaceActions.createSharedMenuBar(
                    () -> this.tabbedEditor, this.toast,
                    () -> { scanLocalFiles(); refreshFileList(); },
                    this::onClose
            );
        }
        scanLocalFiles();
        buildWorkspaceLayout();
    }

    private void buildWorkspaceLayout() {
        String oldSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        String oldReplace = this.replaceBox != null ? this.replaceBox.getValue() : "";
        double oldScroll = this.fileListWidget != null ? this.fileListWidget.scrollAmount() : 0;
        this.clearWidgets();

        this.sidebarW = isSidebarOpen ? Math.max(160, Math.min(250, (int) (this.width * 0.18))) : 0;
        this.leftW = actBarW + sidebarW;
        this.rightX = leftW + 1;

        int contentH = this.height - topY;

        if (isSidebarOpen) {
            if (activeActivity == 0) {
                this.fileListWidget = new FileListWidget(this.minecraft, sidebarW, contentH - 30, topY + 24, 14);
                this.fileListWidget.setX(actBarW);
                refreshFileList();
                this.fileListWidget.setScrollAmount(oldScroll);
                this.addRenderableWidget(this.fileListWidget);
            } else if (activeActivity == 1) {
                int inputWidth = sidebarW - 12 - 50;

                this.searchBox = new EditBox(this.font, actBarW + 6, topY + 24, inputWidth, 18, Component.translatable("nekojs.gui.search.placeholder"));
                this.searchBox.setHint(Component.translatable("nekojs.gui.search.hint"));
                this.searchBox.setValue(oldSearch);
                this.searchBox.setTextColor(0xFFFFFFFF);
                this.searchBox.setResponder(s -> { buildSearchTree(); refreshFileList(); });
                this.addRenderableWidget(this.searchBox);

                this.replaceBox = new EditBox(this.font, actBarW + 6, topY + 46, inputWidth, 18, Component.translatable("nekojs.gui.replace.placeholder"));
                this.replaceBox.setHint(Component.translatable("nekojs.gui.replace.hint"));
                this.replaceBox.setValue(oldReplace);
                this.replaceBox.setTextColor(0xFFFFFFFF);
                this.addRenderableWidget(this.replaceBox);

                buildSearchTree();

                this.fileListWidget = new FileListWidget(this.minecraft, sidebarW, contentH - 74, topY + 68, 14);
                this.fileListWidget.setX(actBarW);
                refreshFileList();
                this.fileListWidget.setScrollAmount(oldScroll);
                this.addRenderableWidget(this.fileListWidget);

                this.setFocused(this.searchBox);
            }
        }

        if (this.tabbedEditor == null) {
            this.tabbedEditor = new NekoTabbedEditor(this.font, rightX, topY, this.width - rightX, contentH,
                    tab -> NekoWorkspaceActions.saveTab(tab, this.toast),
                    this::buildWorkspaceLayout,
                    this::buildWorkspaceLayout);
        } else {
            this.tabbedEditor.setBounds(rightX, topY, this.width - rightX, contentH);
        }

        if (this.tabbedEditor.getActiveTab() != null && this.tabbedEditor.getActiveTab().editor != null) {
            this.addRenderableWidget(this.tabbedEditor.getActiveTab().editor.getWidget());
            if (activeActivity != 1 && !modal.isOpen()) {
                this.setFocused(this.tabbedEditor.getActiveTab().editor.getWidget());
            }
        }

        this.modal.updateBounds(this.width, this.height);
        this.addRenderableWidget(this.modal.getWidget());
        if (this.modal.isInputMode()) this.setFocused(this.modal.getWidget());
    }

    private void collectExpandedPaths(FileNode node, Set<String> set) {
        if (node.isExpanded && !node.path.isEmpty()) set.add(node.path);
        for (FileNode child : node.children) collectExpandedPaths(child, set);
    }

    private void scanLocalFiles() {
        Set<String> expandedPaths = new HashSet<>();
        if (this.treeRoot != null) collectExpandedPaths(this.treeRoot, expandedPaths);

        this.allLocalFiles.clear();
        this.fileContentCache.clear();
        this.treeRoot = new FileNode("root", "", true, -1);
        this.treeRoot.isExpanded = true;

        Path root = NekoJSPaths.ROOT;
        if (!Files.exists(root)) return;

        try (Stream<Path> stream = Files.walk(root)) {
            stream.forEach(p -> {
                if (p.equals(root)) return;

                String relPath = root.relativize(p).toString().replace('\\', '/');
                boolean isActualDir = Files.isDirectory(p);

                if (!isActualDir) {
                    this.allLocalFiles.add(relPath);
                    try {
                        if (Files.size(p) < 1024 * 1024) {
                            fileContentCache.put(relPath, Files.readString(p));
                        }
                    } catch (Exception ignored) {}
                }

                String[] parts = relPath.split("/");
                FileNode current = treeRoot;
                StringBuilder pathBuilder = new StringBuilder();

                for (int i = 0; i < parts.length; i++) {
                    if (pathBuilder.length() > 0) pathBuilder.append("/");
                    pathBuilder.append(parts[i]);
                    String currentFullPath = pathBuilder.toString();

                    FileNode child = null;
                    for (FileNode c : current.children) {
                        if (c.name.equals(parts[i])) {
                            child = c; break;
                        }
                    }

                    if (child == null) {
                        boolean isDirNode = (i < parts.length - 1) || isActualDir;
                        child = new FileNode(parts[i], currentFullPath, isDirNode, current.depth + 1);
                        if (expandedPaths.contains(currentFullPath)) child.isExpanded = true;
                        current.children.add(child);
                    }
                    current = child;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.allLocalFiles.sort(String::compareToIgnoreCase);
        sortTree(treeRoot);
        buildSearchTree();
    }

    private Pattern getSearchPattern(String text) {
        if (text == null || text.isEmpty()) return null;
        String regex = isRegex ? text : Pattern.quote(text);
        if (isMatchWord) regex = "\\b" + regex + "\\b";
        int flags = isMatchCase ? 0 : Pattern.CASE_INSENSITIVE;
        try { return Pattern.compile(regex, flags); } catch (Exception e) { return null; }
    }

    private void buildSearchTree() {
        this.searchRoot = new FileNode("search_root", "", true, -1);
        this.searchRoot.isExpanded = true;

        String filter = searchBox != null ? searchBox.getValue() : "";
        if (filter.isEmpty()) return;

        Pattern pattern = getSearchPattern(filter);
        if (pattern == null) return;

        for (String path : allLocalFiles) {
            String content = fileContentCache.getOrDefault(path, "");
            String[] lines = content.replace("\r", "").split("\n", -1);

            FileNode fileNode = new FileNode(path, path, true, 0);
            fileNode.isExpanded = true;

            int globalOffset = 0;
            int matchCount = 0;

            for (int i = 0; i < lines.length; i++) {
                String rawLine = lines[i];
                String trimmedLine = rawLine.trim();
                int trimOffset = rawLine.indexOf(trimmedLine);
                if (trimOffset == -1) trimOffset = 0;

                Matcher m = pattern.matcher(rawLine);
                List<int[]> highlights = new ArrayList<>();

                int firstMatchAbsoluteStart = -1;
                int firstMatchAbsoluteEnd = -1;

                while (m.find()) {
                    matchCount++;
                    if (firstMatchAbsoluteStart == -1) {
                        firstMatchAbsoluteStart = globalOffset + m.start();
                        firstMatchAbsoluteEnd = globalOffset + m.end();
                    }

                    int localStart = m.start() - trimOffset;
                    int localEnd = m.end() - trimOffset;
                    localStart = Math.max(0, localStart);
                    localEnd = Math.min(trimmedLine.length(), localEnd);
                    highlights.add(new int[]{localStart, localEnd});
                }

                if (!highlights.isEmpty()) {
                    int firstMatchStart = highlights.get(0)[0];
                    String displayLine = trimmedLine;
                    int shift = 0;
                    boolean addedPrefix = false;

                    if (displayLine.length() > 60) {
                        if (firstMatchStart > 20) {
                            shift = firstMatchStart - 15;
                            int end = Math.min(displayLine.length(), shift + 50);
                            displayLine = "..." + displayLine.substring(shift, end) + (end < displayLine.length() ? "..." : "");
                            addedPrefix = true;
                        } else {
                            displayLine = displayLine.substring(0, 60) + "...";
                        }
                    }

                    List<int[]> finalHighlights = new ArrayList<>();
                    for (int[] hl : highlights) {
                        int s = hl[0] - shift + (addedPrefix ? 3 : 0);
                        int e = hl[1] - shift + (addedPrefix ? 3 : 0);
                        s = Math.max(0, Math.min(s, displayLine.length()));
                        e = Math.max(0, Math.min(e, displayLine.length()));
                        if (s < e) {
                            finalHighlights.add(new int[]{s, e});
                        }
                    }

                    SearchMatchNode matchNode = new SearchMatchNode(displayLine, path, finalHighlights, firstMatchAbsoluteStart, firstMatchAbsoluteEnd);
                    fileNode.children.add(matchNode);
                }
                globalOffset += rawLine.length() + 1;
            }

            if (matchCount > 0) {
                String[] parts = path.split("/");
                String fileName = parts[parts.length - 1];
                fileNode.name = fileName + " (" + matchCount + ")  §8" + getParentDir(path);
                searchRoot.children.add(fileNode);
            }
        }
    }

    private void refreshFileList() {
        if (fileListWidget == null || treeRoot == null) return;
        double oldScroll = fileListWidget.scrollAmount();
        fileListWidget.clearEntries();
        maxListContentWidth = 0;

        if (activeActivity == 0) {
            addNodesToWidget(treeRoot);
        } else if (activeActivity == 1) {
            if (searchRoot != null) {
                addNodesToWidget(searchRoot);
            }
        }
        fileListWidget.setScrollAmount(oldScroll);

        int visibleW = sidebarW - 12;
        int maxScrollX = Math.max(0, maxListContentWidth - visibleW);
        listScrollX = Mth.clamp(listScrollX, 0, maxScrollX);
    }

    private void addNodesToWidget(FileNode node) {
        if (node != treeRoot && node != searchRoot) {
            fileListWidget.addEntry(new FileEntry(node, false));
            int indent = node.depth * 10 + 4;
            int width = indent + 20 + this.font.width(node.name);
            maxListContentWidth = Math.max(maxListContentWidth, width);
        }
        if (node == treeRoot || node == searchRoot || (node.isDir && node.isExpanded)) {
            for (FileNode child : node.children) {
                addNodesToWidget(child);
            }
        }
    }

    private void doGlobalReplaceAll() {
        if (searchBox == null || replaceBox == null) return;
        String query = searchBox.getValue();
        String replacement = replaceBox.getValue();

        Pattern pattern = getSearchPattern(query);
        if (pattern == null) { toast.show(I18n.get("nekojs.gui.toast.error.invalid_regex")); return; }

        int affectedFiles = 0;
        int totalReplaced = 0;

        for (String path : allLocalFiles) {
            String content = fileContentCache.getOrDefault(path, "");
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String newContent;
                if (isPreserveCase) {
                    StringBuffer sb = new StringBuffer();
                    matcher.reset();
                    while (matcher.find()) {
                        String matchStr = matcher.group();
                        String actualReplacement = replacement;
                        if (matchStr.toUpperCase().equals(matchStr)) actualReplacement = replacement.toUpperCase();
                        else if (Character.isUpperCase(matchStr.charAt(0))) actualReplacement = replacement.substring(0, 1).toUpperCase() + (replacement.length() > 1 ? replacement.substring(1) : "");
                        else actualReplacement = replacement.toLowerCase();
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(actualReplacement));
                        totalReplaced++;
                    }
                    matcher.appendTail(sb);
                    newContent = sb.toString();
                } else {
                    matcher.reset();
                    newContent = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                    Matcher countMatcher = pattern.matcher(content);
                    while(countMatcher.find()) totalReplaced++;
                }

                if (!content.equals(newContent)) {
                    try {
                        Files.writeString(NekoJSPaths.ROOT.resolve(path), newContent);
                        fileContentCache.put(path, newContent);
                        affectedFiles++;
                        if (tabbedEditor != null) tabbedEditor.openTab(path, newContent);
                    } catch (Exception e) { toast.show(I18n.get("nekojs.gui.toast.error.replace_fail", path)); }
                }
            }
        }
        buildSearchTree();
        refreshFileList();
        toast.show(I18n.get("nekojs.gui.toast.replace_success", affectedFiles, totalReplaced));
    }

    private void sortTree(FileNode node) { node.children.sort((a, b) -> { if (a.isDir != b.isDir) return a.isDir ? -1 : 1; return a.name.compareToIgnoreCase(b.name); }); for (FileNode c : node.children) { if (c.isDir) sortTree(c); } }

    private void createNewItem(String targetDir, String name, boolean isDir) {
        try {
            if (name == null || name.trim().isEmpty()) return;
            String fullPath = targetDir.isEmpty() ? name : targetDir + "/" + name;
            Path p = NekoJSPaths.ROOT.resolve(fullPath);
            if (isDir) {
                Files.createDirectories(p);
            } else {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                if (!Files.exists(p)) Files.createFile(p);
            }
            scanLocalFiles();
            if (!targetDir.isEmpty()) { expandNodeByPath(treeRoot, targetDir); }
            refreshFileList();
            toast.show(I18n.get("nekojs.gui.toast.create_success", name));
        } catch (Exception e) { toast.show(I18n.get("nekojs.gui.toast.create_fail", e.getMessage())); }
    }

    private void expandNodeByPath(FileNode root, String path) { if (path == null || path.isEmpty()) return; String[] parts = path.split("/"); FileNode current = root; for (String part : parts) { for (FileNode child : current.children) { if (child.name.equals(part)) { child.isExpanded = true; current = child; break; } } } }

    private void deleteItem(String path) {
        try {
            Path target = NekoJSPaths.ROOT.resolve(path);
            if (Files.exists(target)) {
                if (Files.isDirectory(target)) {
                    try (Stream<Path> walk = Files.walk(target)) { walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); }
                } else {
                    if (!Files.isDirectory(target)) Files.delete(target);
                }
            }
            if (selectedFilePath != null && selectedFilePath.startsWith(path)) { selectedFilePath = null; }
            scanLocalFiles();
            refreshFileList();
            toast.show(I18n.get("nekojs.gui.toast.delete_success", path));
        } catch (Exception e) { toast.show(I18n.get("nekojs.gui.toast.delete_fail", e.getMessage())); }
    }

    private String getParentDir(String path) { int idx = path.lastIndexOf('/'); return (idx == -1) ? "" : path.substring(0, idx); }

    private void openFileInEditor(String path) {
        openFileInEditor(path, -1, -1);
    }

    private void openFileInEditor(String path, int selectionStart, int selectionEnd) {
        try {
            Path p = NekoJSPaths.ROOT.resolve(path);
            String text = Files.exists(p) ? Files.readString(p) : "";
            if (tabbedEditor == null) buildWorkspaceLayout();
            tabbedEditor.openTab(path, text);

            if (selectionStart != -1 && selectionEnd != -1) {
                if (tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().editor != null) {
                    tabbedEditor.getActiveTab().editor.setHighlightAndScroll(selectionStart, selectionEnd);
                }
            }
        } catch (Exception e) { toast.show(I18n.get("nekojs.gui.toast.error.read_fail", e.getMessage())); }
    }

    public void onSyncFeedback(boolean success, String message) { String prefix = success ? "§a✔ " : "§c✖ "; this.toast.show(prefix + message); if (success && tabbedEditor != null && tabbedEditor.getActiveTab() != null) { tabbedEditor.getActiveTab().editor.markSaved(); } }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF1E1E1E, 0xFF1E1E1E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, topY, 0xFF333333);
        graphics.text(this.font, "§cNEKO§fJS §8" + I18n.get("nekojs.gui.workspace.title"), 10, 6, -1);
        int closeX = this.width - 20;
        graphics.text(this.font, (mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= 4 && mouseY <= 16) ? "§c✖" : "§7✖", closeX, 6, -1);

        graphics.fill(0, topY, actBarW, this.height, 0xFF333333);

        int iconCenterYOffset = (actBarW - this.font.lineHeight) / 2;
        int iconCenterX1 = (actBarW - this.font.width("📂")) / 2;
        int iconCenterX2 = (actBarW - this.font.width("🔍")) / 2;
        int iconCenterX3 = (actBarW - this.font.width("⚙")) / 2;

        if (activeActivity == 0 && isSidebarOpen) { graphics.fill(0, topY, actBarW, topY + actBarW, 0xFF444444); graphics.fill(0, topY, 2, topY + actBarW, 0xFF007ACC); graphics.text(this.font, "§f📂", iconCenterX1, topY + iconCenterYOffset, -1); } else { boolean hov = mouseX >= 0 && mouseX <= actBarW && mouseY >= topY && mouseY < topY + actBarW; graphics.text(this.font, "§7📂", iconCenterX1, topY + iconCenterYOffset, hov ? 0xFFFFFFFF : -1); }
        int btn2Y = topY + actBarW;
        if (activeActivity == 1 && isSidebarOpen) { graphics.fill(0, btn2Y, actBarW, btn2Y + actBarW, 0xFF444444); graphics.fill(0, btn2Y, 2, btn2Y + actBarW, 0xFF007ACC); graphics.text(this.font, "§f🔍", iconCenterX2, btn2Y + iconCenterYOffset, -1); } else { boolean hov = mouseX >= 0 && mouseX <= actBarW && mouseY >= btn2Y && mouseY < btn2Y + actBarW; graphics.text(this.font, "§7🔍", iconCenterX2, btn2Y + iconCenterYOffset, hov ? 0xFFFFFFFF : -1); }
        int btnSettingsY = this.height - actBarW; boolean hovSettings = mouseX >= 0 && mouseX <= actBarW && mouseY >= btnSettingsY && mouseY < this.height; graphics.text(this.font, "§7⚙", iconCenterX3, btnSettingsY + iconCenterYOffset, hovSettings ? 0xFFFFFFFF : -1);

        if (isSidebarOpen) {
            graphics.fill(actBarW, topY, leftW, this.height, 0xFF252526);
            if (activeActivity == 0) graphics.text(this.font, I18n.get("nekojs.gui.workspace.explorer"), actBarW + 10, topY + 10, 0xFFBBBBBB);
            else if (activeActivity == 1) graphics.text(this.font, I18n.get("nekojs.gui.workspace.search_replace"), actBarW + 10, topY + 10, 0xFFBBBBBB);

            if (fileListWidget != null) {
                int visibleW = sidebarW - 12;
                int maxScrollX = Math.max(0, maxListContentWidth - visibleW);

                if (maxScrollX > 0) {
                    int trackW = sidebarW - 6;
                    int handleW = Math.max(15, (int) ((visibleW / (float) maxListContentWidth) * trackW));
                    int handleX = actBarW + 2 + (int) ((listScrollX / maxScrollX) * (trackW - handleW));
                    int scrollY = fileListWidget.getY() + fileListWidget.getHeight() + 2;

                    graphics.fill(actBarW + 2, scrollY, actBarW + 2 + trackW, scrollY + 4, 0xFF1E1E1E);
                    graphics.fill(handleX, scrollY, handleX + handleW, scrollY + 4, 0xFF4A4A4A);
                }
            }
        }

        graphics.fill(leftW, topY, rightX, this.height, 0xFF1E1E1E);
        if (tabbedEditor != null && !tabbedEditor.isEmpty()) { tabbedEditor.renderUnderlay(graphics, mouseX, mouseY); } else { graphics.fill(rightX, topY, this.width, this.height, 0xFF1E1E1E); graphics.centeredText(this.font, Component.translatable("nekojs.gui.workspace.empty_tip"), rightX + (this.width - rightX) / 2, this.height / 2, -1); }

        boolean blockEditorHover = modal.isOpen() || (activeContextMenu != null) || menuBar.isMenuOpen() || (tabbedEditor != null && tabbedEditor.isHoveringDropdown(mouseX, mouseY));
        super.extractRenderState(graphics, blockEditorHover ? -999 : mouseX, blockEditorHover ? -999 : mouseY, partialTick);

        String activeTooltip = null;
        if (isSidebarOpen && activeActivity == 1) {
            if (this.searchBox != null) this.searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
            if (this.replaceBox != null) this.replaceBox.extractRenderState(graphics, mouseX, mouseY, partialTick);

            int inputWidth = sidebarW - 12 - 50;
            int iconStartX = actBarW + 6 + inputWidth + 4;

            int sy = topY + 24;
            activeTooltip = drawToggleIcon(graphics, "Aa", iconStartX, sy, isMatchCase, mouseX, mouseY, activeTooltip, I18n.get("nekojs.gui.workspace.tooltip.match_case"));
            activeTooltip = drawToggleIcon(graphics, "\"\"", iconStartX + 16, sy, isMatchWord, mouseX, mouseY, activeTooltip, I18n.get("nekojs.gui.workspace.tooltip.match_word"));
            activeTooltip = drawToggleIcon(graphics, ".*", iconStartX + 32, sy, isRegex, mouseX, mouseY, activeTooltip, I18n.get("nekojs.gui.workspace.tooltip.regex"));

            int ry = topY + 46;
            activeTooltip = drawToggleIcon(graphics, "AB", iconStartX, ry, isPreserveCase, mouseX, mouseY, activeTooltip, I18n.get("nekojs.gui.workspace.tooltip.preserve_case"));
            activeTooltip = drawToggleIcon(graphics, "ALL", iconStartX + 20, ry, false, mouseX, mouseY, activeTooltip, I18n.get("nekojs.gui.workspace.tooltip.replace_all"));
        }

        int titleW = this.font.width("NEKOJS Workspace") + 30;
        this.menuBar.render(graphics, this.font, mouseX, mouseY, titleW, 3);

        this.toast.render(graphics, this.font, this.width, this.height);
        if (this.activeContextMenu != null) this.activeContextMenu.render(graphics, mouseX, mouseY);
        this.modal.render(graphics, mouseX, mouseY, partialTick, this.width, this.height);

        if (activeTooltip != null) {
            int tw = this.font.width(activeTooltip) + 8;
            graphics.fill(mouseX + 10, mouseY + 10, mouseX + 10 + tw, mouseY + 24, 0xFF111111);
            graphics.outline(mouseX + 10, mouseY + 10, tw, 14, 0xFF454545);
            graphics.text(this.font, activeTooltip, mouseX + 14, mouseY + 13, 0xFFCCCCCC);
        }
    }

    private String drawToggleIcon(GuiGraphicsExtractor g, String text, int x, int y, boolean isActive, int mx, int my, String currentTooltip, String tooltipStr) {
        int w = this.font.width(text) + 4; int h = 18; boolean isHovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (isActive) { g.fill(x, y, x + w, y + h, 0xFF094771); g.outline(x, y, w, h, 0xFF007ACC); } else if (isHovered) { g.fill(x, y, x + w, y + h, 0xFF333333); }
        g.centeredText(this.font, text, x + w / 2, y + 5, isActive ? 0xFFFFFFFF : 0xFFAAAAAA);
        return isHovered ? tooltipStr : currentTooltip;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (modal.isOpen()) {
            if (modal.mouseClicked(event, this.width, this.height)) return true;
            return true;
        }

        if (activeContextMenu != null) {
            if (activeContextMenu.mouseClicked(event.x(), event.y(), event.button())) {
                activeContextMenu = null; return true;
            }
            activeContextMenu = null; return true;
        }

        int titleW = this.font.width("NEKOJS Workspace") + 30;
        if (this.menuBar.mouseClicked(event.x(), event.y(), event.button(), this.font, this.width, this.height, titleW, 3)) {
            return true;
        }

        if (event.y() >= 4 && event.y() <= 16) { int closeX = this.width - 20; if (event.x() >= closeX && event.x() <= closeX + 10) { this.onClose(); return true; } }

        if (event.x() >= 0 && event.x() <= actBarW && event.y() >= topY) {
            if (event.y() < topY + actBarW) { if (activeActivity == 0 && isSidebarOpen) isSidebarOpen = false; else { activeActivity = 0; isSidebarOpen = true; } buildWorkspaceLayout(); return true; }
            else if (event.y() < topY + actBarW * 2) { if (activeActivity == 1 && isSidebarOpen) isSidebarOpen = false; else { activeActivity = 1; isSidebarOpen = true; } buildWorkspaceLayout(); return true; }
        }

        if (isSidebarOpen && activeActivity == 1) {
            int inputWidth = sidebarW - 12 - 50;
            int iconStartX = actBarW + 6 + inputWidth + 4;
            int sy = topY + 24; int ry = topY + 46;

            if (event.y() >= sy && event.y() <= sy + 18) {
                if (event.x() >= iconStartX && event.x() <= iconStartX + this.font.width("Aa") + 4) { isMatchCase = !isMatchCase; buildSearchTree(); refreshFileList(); return true; }
                if (event.x() >= iconStartX + 16 && event.x() <= iconStartX + 16 + this.font.width("\"\"") + 4) { isMatchWord = !isMatchWord; buildSearchTree(); refreshFileList(); return true; }
                if (event.x() >= iconStartX + 32 && event.x() <= iconStartX + 32 + this.font.width(".*") + 4) { isRegex = !isRegex; buildSearchTree(); refreshFileList(); return true; }
            }
            if (event.y() >= ry && event.y() <= ry + 18) {
                if (event.x() >= iconStartX && event.x() <= iconStartX + this.font.width("AB") + 4) { isPreserveCase = !isPreserveCase; return true; }
                if (event.x() >= iconStartX + 20 && event.x() <= iconStartX + 20 + this.font.width("ALL") + 4) { doGlobalReplaceAll(); return true; }
            }
        }

        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled && event.button() == 1 && isSidebarOpen && activeActivity == 0) {
            if (event.x() > actBarW && event.x() < leftW && event.y() > topY) {
                List<NekoContextMenu.MenuItem> items = List.of(
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.new_file"), () -> modal.openInput(I18n.get("nekojs.gui.modal.new_file.root"), name -> createNewItem("", name, false))),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.new_dir"), () -> modal.openInput(I18n.get("nekojs.gui.modal.new_dir.root"), name -> createNewItem("", name, true))),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.refresh"), () -> { scanLocalFiles(); refreshFileList(); toast.show(I18n.get("nekojs.gui.toast.refresh_success")); })
                );
                this.activeContextMenu = new NekoContextMenu(this.font, (int)event.x(), (int)event.y(), this.width, this.height, items);
                return true;
            }
        }
        if (tabbedEditor != null && tabbedEditor.mouseClicked(event.x(), event.y(), event.button())) return true;
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal.isOpen() || activeContextMenu != null || menuBar.isMenuOpen()) return true;
        if (tabbedEditor != null && tabbedEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        if (isSidebarOpen && mouseX >= actBarW && mouseX <= leftW && mouseY >= topY) {
            if (hasShiftDown()) {
                double delta = (scrollX != 0 ? scrollX : scrollY) * 20;
                int visibleW = sidebarW - 12;
                int maxScrollX = Math.max(0, maxListContentWidth - visibleW);

                listScrollX -= delta;
                listScrollX = Mth.clamp(listScrollX, 0, maxScrollX);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal.isOpen()) {
            if (modal.keyPressed(event)) return true;
            return super.keyPressed(event);
        }
        if (this.activeContextMenu != null && event.isEscape()) { this.activeContextMenu = null; return true; }
        if (this.menuBar.keyPressed(event)) return true;
        if (tabbedEditor != null && tabbedEditor.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal.isOpen()) return super.charTyped(event);
        if (this.activeContextMenu != null || this.menuBar.isMenuOpen()) return false;
        if (tabbedEditor != null && tabbedEditor.charTyped((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    private static class FileNode { String name; String path; boolean isDir; int depth; boolean isExpanded = false; List<FileNode> children = new ArrayList<>(); public FileNode(String name, String path, boolean isDir, int depth) { this.name = name; this.path = path; this.isDir = isDir; this.depth = depth; } }

    private static class SearchMatchNode extends FileNode {
        List<int[]> highlights; int globalStart; int globalEnd;
        public SearchMatchNode(String lineText, String path, List<int[]> highlights, int globalStart, int globalEnd) {
            super(lineText, path, false, 1);
            this.highlights = highlights; this.globalStart = globalStart; this.globalEnd = globalEnd;
        }
    }

    private class FileListWidget extends ObjectSelectionList<FileEntry> {
        public FileListWidget(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, ih); }
        @Override public int getRowWidth() { return this.width - 12; }
        @Override protected int scrollBarX() { return this.getX() + this.width - 6; }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
        @Override public int addEntry(FileEntry entry) { return super.addEntry(entry); }
        @Override public int getItemCount() { return super.getItemCount(); }
    }

    private class FileEntry extends ObjectSelectionList.Entry<FileEntry> {
        private final FileNode node; private final boolean isFlatSearch;
        public FileEntry(FileNode node, boolean isFlatSearch) { this.node = node; this.isFlatSearch = isFlatSearch; }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean isHovered, float pt) {
            boolean isSelected = false;

            if (activeActivity == 1) {
                if (node instanceof SearchMatchNode match) {
                    isSelected = node.path.equals(NekoWorkspaceScreen.this.selectedFilePath)
                            && match.globalStart == NekoWorkspaceScreen.this.selectedMatchStart;
                } else {
                    isSelected = node.path.equals(NekoWorkspaceScreen.this.selectedFilePath)
                            && NekoWorkspaceScreen.this.selectedMatchStart == -1;
                }
            } else {
                isSelected = node.path.equals(selectedFilePath);
                boolean isEditing = (tabbedEditor != null && tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().path.equals(node.path));
                if (isEditing) isSelected = true;
            }

            if (isSelected) g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF094771);
            else if (isHovered) g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x33FFFFFF);

            int scrollX = (int) NekoWorkspaceScreen.this.listScrollX;
            int baseRenderX = this.getX() - scrollX;

            int indent = (node.depth * 10 + 4);
            int color = isSelected ? 0xFFFFFFFF : 0xFFCCCCCC;

            if (node instanceof SearchMatchNode match) {
                int xOffset = baseRenderX + indent;
                int yOffset = this.getY() + 3;
                String text = match.name;
                int currentIndex = 0;

                for (int[] hl : match.highlights) {
                    int safeHl0 = Math.max(0, Math.min(hl[0], text.length()));
                    int safeHl1 = Math.max(0, Math.min(hl[1], text.length()));
                    int safeCurrent = Math.max(0, Math.min(currentIndex, text.length()));

                    if (safeHl0 > safeCurrent) {
                        String pre = text.substring(safeCurrent, safeHl0);
                        g.text(NekoWorkspaceScreen.this.font, pre, xOffset, yOffset, 0xFFCCCCCC);
                        xOffset += NekoWorkspaceScreen.this.font.width(pre);
                    }
                    if (safeHl1 > safeHl0) {
                        String hi = hi = text.substring(safeHl0, safeHl1);
                        g.fill(xOffset, yOffset - 1, xOffset + NekoWorkspaceScreen.this.font.width(hi), yOffset + NekoWorkspaceScreen.this.font.lineHeight, 0x66FFAA00);
                        g.text(NekoWorkspaceScreen.this.font, hi, xOffset, yOffset, 0xFFFFCC00);
                        xOffset += NekoWorkspaceScreen.this.font.width(hi);
                    }
                    currentIndex = Math.max(currentIndex, safeHl1);
                }

                int safeCurrentFinal = Math.max(0, Math.min(currentIndex, text.length()));
                if (safeCurrentFinal < text.length()) {
                    String post = text.substring(safeCurrentFinal);
                    g.text(NekoWorkspaceScreen.this.font, post, xOffset, yOffset, 0xFFCCCCCC);
                }
            } else if (node.isDir) {
                int btnX = baseRenderX + indent - 2;
                int btnY = this.getY() + 1;
                int btnW = 10;
                int btnH = 10;

                boolean hovBtn = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
                if (hovBtn) {
                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x33FFFFFF);
                    g.outline(btnX, btnY, btnW, btnH, 0x55FFFFFF);
                }

                String prefix = node.isExpanded ? "v" : ">";
                g.text(NekoWorkspaceScreen.this.font, prefix, btnX + 2, btnY + 1, hovBtn ? 0xFFFFFFFF : 0xFF888888);

                g.text(NekoWorkspaceScreen.this.font, node.name, baseRenderX + indent + 12, this.getY() + 3, color);
            } else {
                g.text(NekoWorkspaceScreen.this.font, node.name, baseRenderX + indent, this.getY() + 3, color);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0) {
                if (node.isDir) {
                    int indent = (node.depth * 10 + 4);
                    if (event.x() < this.getX() - (int) NekoWorkspaceScreen.this.listScrollX + indent + 10) {
                        node.isExpanded = !node.isExpanded;
                    } else {
                        node.isExpanded = !node.isExpanded;
                        NekoWorkspaceScreen.this.selectedFilePath = node.path;
                        NekoWorkspaceScreen.this.selectedMatchStart = -1;
                    }
                    NekoWorkspaceScreen.this.refreshFileList();
                    return true;
                } else {
                    NekoWorkspaceScreen.this.selectedFilePath = node.path;

                    if (node instanceof SearchMatchNode match) {
                        NekoWorkspaceScreen.this.selectedMatchStart = match.globalStart;
                        NekoWorkspaceScreen.this.openFileInEditor(node.path, match.globalStart, match.globalEnd);
                    } else {
                        NekoWorkspaceScreen.this.selectedMatchStart = -1;
                        if (doubleClick) NekoWorkspaceScreen.this.openFileInEditor(node.path);
                    }
                    NekoWorkspaceScreen.this.refreshFileList();
                    return true;
                }
            } else if (event.button() == 1) {
                String targetDir = node.isDir ? node.path : getParentDir(node.path);
                String displayDir = targetDir.isEmpty() ? I18n.get("nekojs.gui.workspace.root_dir") : targetDir;
                List<NekoContextMenu.MenuItem> items = List.of(
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.new_file"), () -> modal.openInput(I18n.get("nekojs.gui.modal.new_file.dir", displayDir), name -> NekoWorkspaceScreen.this.createNewItem(targetDir, name, false))),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.new_dir"), () -> modal.openInput(I18n.get("nekojs.gui.modal.new_dir.dir", displayDir), name -> NekoWorkspaceScreen.this.createNewItem(targetDir, name, true))),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.delete", node.name), () -> modal.openConfirm(I18n.get("nekojs.gui.modal.delete_confirm", node.name), () -> NekoWorkspaceScreen.this.deleteItem(node.path)))
                );
                NekoWorkspaceScreen.this.activeContextMenu = new NekoContextMenu(NekoWorkspaceScreen.this.font, (int)event.x(), (int)event.y(), NekoWorkspaceScreen.this.width, NekoWorkspaceScreen.this.height, items);
                return true;
            }
            return false;
        }
        @Override public Component getNarration() { return Component.literal(this.node.path); }
    }
}