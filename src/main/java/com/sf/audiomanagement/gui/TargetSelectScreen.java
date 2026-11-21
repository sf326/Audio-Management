package com.sf.audiomanagement.gui;

import com.sf.audiomanagement.config.GlobalConfig;
import com.sf.audiomanagement.config.SoundCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TargetSelectScreen extends Screen {
    private final Screen parent;
    private final Set<String> collapsedMods = new HashSet<>();
    private final Set<String> expandedGroups = new HashSet<>();
    private EditBox search;
    private TargetList list;
    private Button defaultButton;
    private boolean rebuildQueued;
    private String query = "";

    public TargetSelectScreen(Screen parent) {
        super(Component.translatable("audio_management.target.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.rebuildQueued = false;
        if (this.search != null) {
            this.query = this.search.getValue();
        }

        int contentWidth = Math.max(200, this.width - 20);
        int contentLeft = this.width / 2 - contentWidth / 2;
        boolean compact = this.height < 180;
        int searchY = compact ? 6 : 20;
        int listTop = compact ? 30 : 46;
        int listBottom = Math.max(listTop + 1, this.height - 32);
        int gap = 10;
        int buttonWidth = (contentWidth - gap * 2) / 3;

        this.search = new EditBox(this.font, contentLeft, searchY, contentWidth, 18,
                Component.translatable("audio_management.gui.search"));
        this.search.setValue(this.query);
        this.search.setHint(Component.translatable("audio_management.gui.search"));
        this.search.setResponder(value -> {
            this.query = value;
            this.rebuildLater();
        });
        this.addRenderableWidget(this.search);

        this.list = new TargetList(this.minecraft, this.width, this.height, listTop, listBottom);
        this.addRenderableWidget(this.list);

        this.defaultButton = Button.builder(Component.translatable(contentWidth < 300
                        ? "audio_management.target.default.short" : "audio_management.target.default"),
                button -> {
                    GlobalConfig.get().setTarget(GlobalConfig.DEFAULT_TARGET, GlobalConfig.TargetType.GROUP);
                    this.minecraft.setScreen(this.parent);
                }).bounds(contentLeft, this.height - 27, buttonWidth, 20).build();
        this.addRenderableWidget(this.defaultButton);
        this.addRenderableWidget(Button.builder(Component.translatable("audio_management.target.empty"),
                        button -> {
                            GlobalConfig.get().setTarget(GlobalConfig.EMPTY_TARGET, GlobalConfig.TargetType.GROUP);
                            this.minecraft.setScreen(this.parent);
                        })
                .bounds(contentLeft + buttonWidth + gap, this.height - 27, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(contentLeft + (buttonWidth + gap) * 2, this.height - 27, buttonWidth, 20).build());

        this.rebuild();
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
            if (!searching) {
                this.list.addRow(new ModTargetRow(this, mod, false));
                if (!this.collapsedMods.contains(mod.namespace())) {
                    for (SoundCatalog.GroupInfo group : mod.groups()) {
                        this.addGroup(group, false);
                    }
                }
                continue;
            }

            List<TargetView> views = new ArrayList<>();
            boolean visible = false;
            for (SoundCatalog.GroupInfo group : mod.groups()) {
                TargetView view = this.searchView(group, query);
                views.add(view);
                visible |= view.visible();
            }
            if (visible) {
                this.list.addRow(new ModTargetRow(this, mod, true));
                for (TargetView view : views) {
                    this.addSearchRows(view);
                }
            } else if (modMatch) {
                this.list.addRow(new ModTargetRow(this, mod, true));
                for (SoundCatalog.GroupInfo group : mod.groups()) {
                    this.list.addRow(new GroupTargetRow(this, group, true));
                }
            }
        }
        this.list.setScrollAmount(scroll);
    }

    private void addGroup(SoundCatalog.GroupInfo group, boolean searching) {
        this.list.addRow(new GroupTargetRow(this, group, searching));
        if (searching || this.isGroupExpanded(group)) {
            for (SoundCatalog.SoundInfo sound : group.sounds()) {
                this.list.addRow(new SoundTargetRow(this, sound));
            }
            for (SoundCatalog.GroupInfo child : group.children()) {
                this.addGroup(child, searching);
            }
        }
    }

    private TargetView searchView(SoundCatalog.GroupInfo group, String query) {
        String path = group.path().toLowerCase(Locale.ROOT);
        boolean selfMatch = path.contains(query) || group.name().toLowerCase(Locale.ROOT).contains(query);
        List<SoundCatalog.SoundInfo> sounds = new ArrayList<>();
        for (SoundCatalog.SoundInfo sound : group.sounds()) {
            if (sound.fullId().toLowerCase(Locale.ROOT).contains(query)
                    || sound.name().toLowerCase(Locale.ROOT).contains(query)) {
                sounds.add(sound);
            }
        }
        List<TargetView> children = new ArrayList<>();
        boolean visible = selfMatch || !sounds.isEmpty();
        for (SoundCatalog.GroupInfo child : group.children()) {
            TargetView view = this.searchView(child, query);
            children.add(view);
            visible |= view.visible();
        }
        return new TargetView(group, selfMatch, sounds, children, visible);
    }

    private void addSearchRows(TargetView view) {
        if (!view.visible()) {
            return;
        }
        this.list.addRow(new GroupTargetRow(this, view.group(), true));
        if (view.selfMatch()) {
            for (SoundCatalog.SoundInfo sound : view.group().sounds()) {
                this.list.addRow(new SoundTargetRow(this, sound));
            }
            for (SoundCatalog.GroupInfo child : view.group().children()) {
                this.addGroup(child, true);
            }
            return;
        }
        for (SoundCatalog.SoundInfo sound : view.sounds()) {
            this.list.addRow(new SoundTargetRow(this, sound));
        }
        for (TargetView child : view.children()) {
            this.addSearchRows(child);
        }
    }

    private record TargetView(SoundCatalog.GroupInfo group, boolean selfMatch,
                              List<SoundCatalog.SoundInfo> sounds,
                              List<TargetView> children, boolean visible) {
    }

    public boolean isModCollapsed(String namespace) {
        return this.collapsedMods.contains(namespace);
    }

    public boolean isGroupExpanded(SoundCatalog.GroupInfo group) {
        return this.expandedGroups.contains(group.namespace() + ":" + group.path());
    }

    public void toggleMod(String namespace) {
        if (!this.collapsedMods.remove(namespace)) {
            this.collapsedMods.add(namespace);
        }
        this.rebuildLater();
    }

    public void toggleGroup(SoundCatalog.GroupInfo group) {
        String key = group.namespace() + ":" + group.path();
        if (!this.expandedGroups.remove(key)) {
            this.expandedGroups.add(key);
        }
        this.rebuildLater();
    }

    public void selectTarget(String id, GlobalConfig.TargetType type) {
        GlobalConfig.get().setTarget(id, type);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
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
        if (this.height >= 180) {
            graphics.drawCenteredString(this.font, this.getTitle(), this.width / 2, 5, 0xFFFFFF);
        }
        Component tooltip = null;
        if (this.defaultButton != null && this.defaultButton.visible && this.defaultButton.active
                && this.defaultButton.isMouseOver(mouseX, mouseY)) {
            tooltip = Component.translatable("audio_management.target.default");
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
        this.minecraft.setScreen(this.parent);
    }

    private static final class TargetList extends ContainerObjectSelectionList<TargetRow> {
        private TargetList(Minecraft minecraft, int width, int height, int top, int bottom) {
            super(minecraft, width, height, top, bottom, 20);
            this.setRenderBackground(false);
        }

        @Override
        public int getRowWidth() {
            return Math.max(200, this.width - 24);
        }

        @Override
        public int getScrollbarPosition() {
            return this.width / 2 + this.getRowWidth() / 2 + 4;
        }

        public void clearRows() {
            this.setFocused((GuiEventListener) null);
            this.setSelected(null);
            this.clearEntries();
        }

        public void addRow(TargetRow row) {
            this.addEntry(row);
        }

        public Component getTooltipAt(double mouseX, double mouseY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return null;
            }
            TargetRow row = this.getEntryAtPosition(mouseX, mouseY);
            return row == null ? null : row.tooltipAt(mouseX, mouseY);
        }
    }

    private abstract static class TargetRow extends ContainerObjectSelectionList.Entry<TargetRow> {
        protected final TargetSelectScreen screen;
        protected final List<AbstractWidget> widgets = new ArrayList<>();
        private final Map<AbstractWidget, Component> tooltips = new HashMap<>();

        protected TargetRow(TargetSelectScreen screen) {
            this.screen = screen;
        }

        public Component tooltipAt(double mouseX, double mouseY) {
            for (Map.Entry<AbstractWidget, Component> entry : this.tooltips.entrySet()) {
                AbstractWidget widget = entry.getKey();
                if (widget.visible && widget.active && widget.isMouseOver(mouseX, mouseY)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        protected void tooltip(AbstractWidget widget, Component text) {
            this.tooltips.put(widget, text);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.fill(left, top, left + width, top + height,
                    index % 2 == 0 ? 0xE0202020 : 0xE0181818);
            this.layout(top, left, width);
            this.draw(graphics, top, left, width);
            for (AbstractWidget widget : widgets) {
                if (widget.visible) {
                    widget.render(graphics, mouseX, mouseY, partialTick);
                }
            }
        }

        protected abstract void layout(int top, int left, int width);

        protected abstract void draw(GuiGraphics graphics, int top, int left, int width);

        protected void place(AbstractWidget widget, int x, int top) {
            widget.setX(x);
            widget.setY(top + 2);
        }

        protected static int indent(int depth, int maxOffset) {
            return Math.min(Math.max(0, maxOffset), Math.max(0, depth) * 10);
        }

        protected void drawName(GuiGraphics graphics, String name, int x, int top, int maxWidth,
                                boolean bold, int color) {
            Font font = Minecraft.getInstance().font;
            maxWidth = Math.max(8, maxWidth);
            if (font.width(name) > maxWidth) {
                name = font.plainSubstrByWidth(name, Math.max(2, maxWidth - 6)) + "…";
            }
            Component text = bold
                    ? Component.literal(name).withStyle(ChatFormatting.BOLD)
                    : Component.literal(name);
            graphics.drawString(font, text, x, top + 6, color);
        }

        protected Button selectButton(String id, GlobalConfig.TargetType type, Component tooltip) {
            Button button = Button.builder(Component.translatable("audio_management.target.select"),
                            pressed -> screen.selectTarget(id, type))
                    .bounds(0, 0, 48, 16).build();
            tooltip(button, tooltip);
            return button;
        }
    }

    private static final class ModTargetRow extends TargetRow {
        private final SoundCatalog.ModInfo mod;
        private final Button expand;

        private ModTargetRow(TargetSelectScreen screen, SoundCatalog.ModInfo mod, boolean searching) {
            super(screen);
            this.mod = mod;
            this.expand = Button.builder(Component.literal(screen.isModCollapsed(mod.namespace()) ? ">" : "v"),
                            pressed -> screen.toggleMod(mod.namespace()))
                    .bounds(0, 0, 12, 16).build();
            this.expand.active = !searching;
            this.expand.visible = !searching;
            widgets.add(this.expand);
        }

        @Override
        protected void layout(int top, int left, int width) {
            place(this.expand, left + 2, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width) {
            drawName(graphics, this.mod.displayName(), left + 18, top, width - 22, true, 0xFFFFFF);
        }
    }

    private static final class GroupTargetRow extends TargetRow {
        private final SoundCatalog.GroupInfo group;
        private final Button expand;
        private final Button select;

        private GroupTargetRow(TargetSelectScreen screen, SoundCatalog.GroupInfo group, boolean searching) {
            super(screen);
            this.group = group;
            this.expand = Button.builder(Component.literal(screen.isGroupExpanded(group) ? "v" : ">"),
                            pressed -> screen.toggleGroup(group))
                    .bounds(0, 0, 12, 16).build();
            this.expand.active = !searching;
            this.expand.visible = !searching;
            this.select = selectButton(group.namespace() + ":" + group.path(),
                    GlobalConfig.TargetType.GROUP, Component.literal(group.namespace() + ":" + group.path()));
            widgets.add(this.expand);
            widgets.add(this.select);
        }

        @Override
        protected void layout(int top, int left, int width) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 52 - (left + 26) - 24);
            int offset = indent(this.group.depth(), maxOffset);
            place(this.expand, left + 12 + offset, top);
            place(this.select, right - 52, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 52 - (left + 26) - 24);
            int offset = indent(this.group.depth(), maxOffset);
            drawName(graphics, this.group.name(), left + 26 + offset, top,
                    right - 52 - (left + 26 + offset) - 4, false, 0xFFFFFF);
        }
    }

    private static final class SoundTargetRow extends TargetRow {
        private final SoundCatalog.SoundInfo sound;
        private final int depth;
        private final Button select;

        private SoundTargetRow(TargetSelectScreen screen, SoundCatalog.SoundInfo sound) {
            super(screen);
            this.sound = sound;
            SoundCatalog.GroupInfo group = SoundCatalog.get().group(sound.namespace(), sound.group());
            this.depth = (group == null ? 0 : group.depth()) + 2;
            this.select = selectButton(sound.fullId(), GlobalConfig.TargetType.SOUND,
                    Component.literal(sound.fullId()));
            widgets.add(this.select);
        }

        @Override
        protected void layout(int top, int left, int width) {
            place(this.select, left + width - 52, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 52 - (left + 36) - 24);
            int offset = indent(this.depth - 2, maxOffset);
            drawName(graphics, this.sound.name(), left + 36 + offset, top,
                    right - 52 - (left + 36 + offset) - 4, false, 0xFFFFFF);
        }
    }
}
