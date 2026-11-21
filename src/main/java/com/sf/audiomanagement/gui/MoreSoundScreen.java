package com.sf.audiomanagement.gui;

import com.sf.audiomanagement.config.ConfigPaths;
import com.sf.audiomanagement.config.GlobalConfig;
import com.sf.audiomanagement.config.ReplacementStore;
import com.sf.audiomanagement.config.SoundCatalog;
import com.sf.audiomanagement.config.VolumeStore;
import com.sf.audiomanagement.util.FileDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MoreSoundScreen extends Screen {
    private final Screen parent;
    private final Set<String> expandedMods = new HashSet<>();
    private final Set<String> expandedGroups = new HashSet<>();

    private SoundTreeList list;
    private EditBox search;
    private Button enabledButton;
    private Button excludeButton;
    private Button targetButton;
    private boolean rebuildQueued;
    private String query = "";
    private double savedScroll;
    private int lastReplacementRevision;

    public MoreSoundScreen(Screen parent) {
        super(Component.translatable("audio_management.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.rebuildQueued = false;
        if (this.search != null) {
            this.query = this.search.getValue();
        }
        if (this.list != null) {
            this.savedScroll = this.list.getScrollAmount();
        }
        this.lastReplacementRevision = ReplacementStore.get().revision();

        int contentWidth = Math.max(200, this.width - 20);
        int contentLeft = this.width / 2 - contentWidth / 2;
        boolean compact = this.height < 220;
        int topY = compact ? 6 : 18;
        int targetY = compact ? 26 : 40;
        int searchY = compact ? 46 : 62;
        int listTop = compact ? 66 : 84;
        int listBottom = Math.max(listTop + 1, this.height - 32);
        int half = (contentWidth - 10) / 2;
        int rightWidth = contentWidth - half - 10;

        this.enabledButton = Button.builder(enabledLabel(), button -> {
            GlobalConfig config = GlobalConfig.get();
            config.setEnabled(!config.enabled);
            button.setMessage(enabledLabel());
        }).bounds(contentLeft, topY, half, compact ? 18 : 20).build();
        this.addRenderableWidget(this.enabledButton);

        this.excludeButton = Button.builder(excludeLabel(), button -> {
            GlobalConfig config = GlobalConfig.get();
            config.setExcludeMusicAndRecords(!config.excludeMusicAndRecords);
            button.setMessage(excludeLabel());
        }).bounds(contentLeft + half + 10, topY, rightWidth, compact ? 18 : 20).build();
        this.addRenderableWidget(this.excludeButton);

        this.targetButton = Button.builder(targetLabel(), button -> {
                    if (this.list != null) {
                        this.savedScroll = this.list.getScrollAmount();
                    }
                    Minecraft.getInstance().setScreen(new TargetSelectScreen(this));
                })
                .bounds(contentLeft, targetY, contentWidth, compact ? 18 : 20).build();
        this.addRenderableWidget(this.targetButton);

        this.search = new EditBox(this.font, contentLeft, searchY, contentWidth, 18,
                Component.translatable("audio_management.gui.search"));
        this.search.setValue(this.query);
        this.search.setHint(Component.translatable("audio_management.gui.search"));
        this.search.setResponder(value -> {
            this.query = value;
            this.rebuildLater();
        });
        this.addRenderableWidget(this.search);

        this.list = new SoundTreeList(this, this.minecraft, this.width, this.height, listTop, listBottom);
        this.addRenderableWidget(this.list);

        int doneWidth = Math.min(200, contentWidth);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - doneWidth / 2, this.height - 27, doneWidth, 20).build());

        this.rebuild();
        this.list.setScrollAmount(this.savedScroll);
    }

    private Component enabledLabel() {
        boolean shortLabel = this.width < 280;
        String key;
        if (GlobalConfig.get().enabled) {
            key = shortLabel ? "audio_management.gui.enabled.short" : "audio_management.gui.enabled";
        } else {
            key = shortLabel ? "audio_management.gui.disabled.short" : "audio_management.gui.disabled";
        }
        return Component.translatable(key);
    }

    private Component excludeLabel() {
        boolean shortLabel = this.width < 280;
        String key;
        if (GlobalConfig.get().excludeMusicAndRecords) {
            key = shortLabel ? "audio_management.gui.exclude_on.short" : "audio_management.gui.exclude_on";
        } else {
            key = shortLabel ? "audio_management.gui.exclude_off.short" : "audio_management.gui.exclude_off";
        }
        return Component.translatable(key);
    }

    private Component targetLabel() {
        GlobalConfig config = GlobalConfig.get();
        if (GlobalConfig.EMPTY_TARGET.equals(config.target)) {
            return Component.translatable("audio_management.gui.target.empty");
        }
        String id = config.target;
        int max = Math.max(40, Math.min(420, this.width - 120));
        if (this.font.width(id) > max) {
            id = this.font.plainSubstrByWidth(id, Math.max(20, max - 6)) + "…";
        }
        return Component.translatable(config.targetType == GlobalConfig.TargetType.GROUP
                ? "audio_management.gui.target.group" : "audio_management.gui.target.sound", id);
    }

    private void rebuildLater() {
        if (this.rebuildQueued) {
            return;
        }
        this.rebuildQueued = true;
        Minecraft.getInstance().execute(() -> {
            if (this.rebuildQueued && Minecraft.getInstance().screen == this) {
                this.rebuildQueued = false;
                this.rebuild();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rebuildQueued) {
            this.rebuildQueued = false;
            this.rebuild();
        }
        int revision = ReplacementStore.get().revision();
        if (revision != this.lastReplacementRevision) {
            this.lastReplacementRevision = revision;
            this.rebuildLater();
        }
    }

    private void rebuild() {
        if (this.list == null) {
            return;
        }
        double scroll = this.list.getScrollAmount();
        this.list.clearRows();
        String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        boolean searching = !query.isEmpty();

        for (SoundCatalog.ModInfo mod : SoundCatalog.get().mods()) {
            boolean modMatch = mod.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || mod.namespace().toLowerCase(Locale.ROOT).contains(query);

            if (searching) {
                List<SearchView> views = new ArrayList<>();
                boolean visible = false;
                for (SoundCatalog.GroupInfo group : mod.groups()) {
                    SearchView view = this.searchView(group, query);
                    views.add(view);
                    visible |= view.visible();
                }
                if (visible) {
                    this.list.addRow(new SoundTreeList.ModRow(this, mod));
                    for (SearchView view : views) {
                        this.addSearchRows(view);
                    }
                } else if (modMatch) {
                    this.list.addRow(new SoundTreeList.ModRow(this, mod));
                    for (SoundCatalog.GroupInfo group : mod.groups()) {
                        this.list.addRow(new SoundTreeList.GroupRow(this, group));
                    }
                }
            } else {
                this.list.addRow(new SoundTreeList.ModRow(this, mod));
                if (this.isModExpanded(mod.namespace())) {
                    for (SoundCatalog.GroupInfo group : mod.groups()) {
                        this.addExpandedGroup(group);
                    }
                }
            }
        }
        this.list.setScrollAmount(scroll);
    }

    private SearchView searchView(SoundCatalog.GroupInfo group, String query) {
        String path = group.path().toLowerCase(Locale.ROOT);
        String name = group.name().toLowerCase(Locale.ROOT);
        boolean selfMatch = path.contains(query) || name.contains(query);
        List<SoundCatalog.SoundInfo> sounds = new ArrayList<>();
        for (SoundCatalog.SoundInfo sound : group.sounds()) {
            if (sound.fullId().toLowerCase(Locale.ROOT).contains(query)
                    || sound.name().toLowerCase(Locale.ROOT).contains(query)) {
                sounds.add(sound);
            }
        }
        List<SearchView> children = new ArrayList<>();
        boolean visible = selfMatch || !sounds.isEmpty();
        for (SoundCatalog.GroupInfo child : group.children()) {
            SearchView view = this.searchView(child, query);
            children.add(view);
            visible |= view.visible();
        }
        return new SearchView(group, selfMatch, sounds, children, visible);
    }

    private void addSearchRows(SearchView view) {
        if (!view.visible()) {
            return;
        }
        this.list.addRow(new SoundTreeList.GroupRow(this, view.group()));
        if (view.selfMatch()) {
            this.addSubtree(view.group());
            return;
        }
        for (SoundCatalog.SoundInfo sound : view.sounds()) {
            this.list.addRow(new SoundTreeList.SoundRow(this, sound, view.group().depth() + 1));
        }
        for (SearchView child : view.children()) {
            this.addSearchRows(child);
        }
    }

    private void addExpandedGroup(SoundCatalog.GroupInfo group) {
        this.list.addRow(new SoundTreeList.GroupRow(this, group));
        if (!this.isGroupExpanded(group)) {
            return;
        }
        for (SoundCatalog.SoundInfo sound : group.sounds()) {
            this.list.addRow(new SoundTreeList.SoundRow(this, sound, group.depth() + 1));
        }
        for (SoundCatalog.GroupInfo child : group.children()) {
            this.addExpandedGroup(child);
        }
    }

    private void addSubtree(SoundCatalog.GroupInfo group) {
        for (SoundCatalog.SoundInfo sound : group.sounds()) {
            this.list.addRow(new SoundTreeList.SoundRow(this, sound, group.depth() + 1));
        }
        for (SoundCatalog.GroupInfo child : group.children()) {
            this.list.addRow(new SoundTreeList.GroupRow(this, child));
            this.addSubtree(child);
        }
    }

    private record SearchView(SoundCatalog.GroupInfo group, boolean selfMatch,
                              List<SoundCatalog.SoundInfo> sounds,
                              List<SearchView> children, boolean visible) {
    }

    public boolean isSearching() {
        return this.search != null && !this.search.getValue().trim().isEmpty();
    }

    public boolean isModExpanded(String namespace) {
        return expandedMods.contains(namespace);
    }

    public boolean isGroupExpanded(SoundCatalog.GroupInfo group) {
        return expandedGroups.contains(group.namespace() + ":" + group.path());
    }

    public void toggleMod(String namespace) {
        if (!expandedMods.remove(namespace)) {
            expandedMods.add(namespace);
        }
        this.rebuildLater();
    }

    public void toggleGroup(SoundCatalog.GroupInfo group) {
        String key = group.namespace() + ":" + group.path();
        if (!expandedGroups.remove(key)) {
            expandedGroups.add(key);
        }
        this.rebuildLater();
    }

    public void setModVolume(String namespace, int volume) {
        VolumeStore.get().setMod(namespace, volume);
    }

    public void setGroupVolume(SoundCatalog.GroupInfo group, int volume) {
        VolumeStore.get().setGroup(group.namespace(), group.path(), volume);
    }

    public void setSoundVolume(SoundCatalog.SoundInfo sound, int volume) {
        VolumeStore.get().setSound(sound.id(), volume);
    }

    public void toggleBlacklist(List<String> ids) {
        GlobalConfig config = GlobalConfig.get();
        config.setBlacklisted(ids, !config.areAllBlacklisted(ids));
        this.rebuildLater();
    }

    public void toggleWhitelist(List<String> ids) {
        GlobalConfig config = GlobalConfig.get();
        config.setWhitelisted(ids, !config.areAllWhitelisted(ids));
        this.rebuildLater();
    }

    public void replaceGroup(SoundCatalog.GroupInfo group) {
        this.pickAndCopy(ConfigPaths.groupOgg(group.namespace(), group.path()));
    }

    public void replaceSound(SoundCatalog.SoundInfo sound) {
        this.pickAndCopy(ConfigPaths.soundOgg(sound.namespace(), sound.group(), sound.name()));
    }

    public void confirmRevertGroup(SoundCatalog.GroupInfo group) {
        this.openConfirm("audio_management.confirm.delete",
                () -> this.delete(ConfigPaths.groupOgg(group.namespace(), group.path())));
    }

    public void confirmRevertSound(SoundCatalog.SoundInfo sound) {
        this.openConfirm("audio_management.confirm.delete",
                () -> this.delete(ConfigPaths.soundOgg(sound.namespace(), sound.group(), sound.name())));
    }

    public void confirmResetMod(SoundCatalog.ModInfo mod) {
        this.openConfirm("audio_management.confirm.reset", () -> this.resetMod(mod));
    }

    public void confirmResetGroup(SoundCatalog.GroupInfo group) {
        this.openConfirm("audio_management.confirm.reset", () -> this.resetGroup(group));
    }

    public void confirmResetSound(SoundCatalog.SoundInfo sound) {
        this.openConfirm("audio_management.confirm.reset", () -> this.resetSound(sound));
    }

    private void openConfirm(String messageKey, Runnable action) {
        this.minecraft.setScreen(new ConfirmActionScreen(this,
                Component.translatable("audio_management.confirm.title"),
                Component.translatable(messageKey), action));
    }

    private void resetMod(SoundCatalog.ModInfo mod) {
        VolumeStore store = VolumeStore.get();
        store.setMod(mod.namespace(), 100);
        for (SoundCatalog.GroupInfo group : mod.allGroups()) {
            store.setGroup(group.namespace(), group.path(), 100);
            for (SoundCatalog.SoundInfo sound : group.sounds()) {
                store.setSound(sound.id(), 100);
            }
        }
        store.flushIfDirty();
        this.rebuildLater();
    }

    private void resetGroup(SoundCatalog.GroupInfo group) {
        VolumeStore store = VolumeStore.get();
        this.resetGroup(store, group);
        store.flushIfDirty();
        this.rebuildLater();
    }

    private void resetGroup(VolumeStore store, SoundCatalog.GroupInfo group) {
        store.setGroup(group.namespace(), group.path(), 100);
        for (SoundCatalog.SoundInfo sound : group.sounds()) {
            store.setSound(sound.id(), 100);
        }
        for (SoundCatalog.GroupInfo child : group.children()) {
            this.resetGroup(store, child);
        }
    }

    private void resetSound(SoundCatalog.SoundInfo sound) {
        VolumeStore store = VolumeStore.get();
        store.setSound(sound.id(), 100);
        store.flushIfDirty();
        this.rebuildLater();
    }

    public void revertGroup(SoundCatalog.GroupInfo group) {
        this.delete(ConfigPaths.groupOgg(group.namespace(), group.path()));
    }

    public void revertSound(SoundCatalog.SoundInfo sound) {
        this.delete(ConfigPaths.soundOgg(sound.namespace(), sound.group(), sound.name()));
    }

    private void pickAndCopy(Path target) {
        FileDialogs.pickOgg(this, source -> {
            if (!ReplacementStore.isOgg(source)) {
                this.message("audio_management.error.ogg");
                return;
            }
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                ReplacementStore.get().refresh();
                this.rebuildLater();
            } catch (IOException exception) {
                this.message("audio_management.error.copy");
            }
        });
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
            ReplacementStore.get().refresh();
            this.rebuildLater();
        } catch (IOException exception) {
            this.message("audio_management.error.delete");
        }
    }

    private void message(String key) {
        this.minecraft.gui.setOverlayMessage(Component.translatable(key), false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        //pass
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        if (this.list != null) {
            graphics.fill(this.list.getLeft(), this.list.getTop(),
                    this.list.getRight(), this.list.getBottom(), 0xF0101010);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.height >= 220) {
            graphics.drawCenteredString(this.font, this.getTitle(), this.width / 2, 5, 0xFFFFFF);
        }
        Component tooltip = null;
        if (this.targetButton != null && this.targetButton.visible && this.targetButton.active
                && this.targetButton.isMouseOver(mouseX, mouseY)) {
            tooltip = Component.translatable("audio_management.gui.target.tooltip");
        }
        if (tooltip == null && this.list != null) {
            tooltip = this.list.getTooltipAt(mouseX, mouseY);
        }
        if (tooltip != null) {
            this.setTooltipForNextRenderPass(tooltip);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        VolumeStore.get().flushIfDirty();
        this.minecraft.setScreen(this.parent);
    }
}
