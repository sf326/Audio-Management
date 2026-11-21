package com.sf.audiomanagement.gui;

import com.sf.audiomanagement.config.GlobalConfig;
import com.sf.audiomanagement.config.ReplacementStore;
import com.sf.audiomanagement.config.SoundCatalog;
import com.sf.audiomanagement.config.VolumeStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SoundTreeList extends ContainerObjectSelectionList<SoundTreeList.Row> {
    public SoundTreeList(MoreSoundScreen screen, Minecraft minecraft, int width, int height, int top, int bottom) {
        super(minecraft, width, height, top, bottom, 22);
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

    public void addRow(Row row) {
        this.addEntry(row);
    }

    public Component getTooltipAt(double mouseX, double mouseY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return null;
        }
        Row row = this.getEntryAtPosition(mouseX, mouseY);
        return row == null ? null : row.tooltipAt(mouseX, mouseY);
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        protected final MoreSoundScreen screen;
        protected final List<AbstractWidget> widgets = new ArrayList<>();
        private final Map<AbstractWidget, Component> tooltips = new HashMap<>();

        protected Row(MoreSoundScreen screen) {
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
            this.draw(graphics, top, left, width, height);
            for (AbstractWidget widget : widgets) {
                if (widget.visible) {
                    widget.render(graphics, mouseX, mouseY, partialTick);
                }
            }
        }

        protected abstract void layout(int top, int left, int width);

        protected abstract void draw(GuiGraphics graphics, int top, int left, int width, int height);

        protected void place(AbstractWidget widget, int x, int top) {
            widget.setX(x);
            widget.setY(top + 3);
        }

        protected void placeSlider(VolumeSlider slider, int x, int top) {
            slider.setX(x);
            slider.setY(top + 1);
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
            graphics.drawString(font, text, x, top + 7, color);
        }

        protected Button resetButton(Runnable action) {
            Button button = Button.builder(Component.translatable("audio_management.short.reset"), pressed -> action.run())
                    .bounds(0, 0, 40, 16).build();
            tooltip(button, Component.translatable("audio_management.reset.tooltip"));
            return button;
        }

        protected Button listButton(Component label, String tooltipKey, Runnable action) {
            Button button = Button.builder(label, pressed -> action.run())
                    .bounds(0, 0, 16, 16).build();
            tooltip(button, Component.translatable(tooltipKey));
            return button;
        }

        protected Button listButton(List<String> ids, boolean black, boolean batch) {
            GlobalConfig config = GlobalConfig.get();
            boolean active = black ? config.areAllBlacklisted(ids) : config.areAllWhitelisted(ids);
            Component message = Component.translatable(black
                            ? "audio_management.short.black" : "audio_management.short.white")
                    .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            String tooltipKey;
            if (batch) {
                tooltipKey = black ? "audio_management.black.batch.tooltip" : "audio_management.white.batch.tooltip";
            } else {
                tooltipKey = black ? "audio_management.black.tooltip" : "audio_management.white.tooltip";
            }
            Button button = Button.builder(message, pressed -> {
                if (black) {
                    screen.toggleBlacklist(ids);
                } else {
                    screen.toggleWhitelist(ids);
                }
            }).bounds(0, 0, 16, 16).build();
            tooltip(button, Component.translatable(tooltipKey));
            return button;
        }
    }

    public static final class ModRow extends Row {
        private final SoundCatalog.ModInfo mod;
        private final Button expand;
        private final VolumeSlider slider;
        private final Button reset;
        private final Button black;
        private final Button white;

        public ModRow(MoreSoundScreen screen, SoundCatalog.ModInfo mod) {
            super(screen);
            this.mod = mod;
            boolean searching = screen.isSearching();
            this.expand = Button.builder(Component.literal(searching || screen.isModExpanded(mod.namespace()) ? "v" : ">"),
                    pressed -> screen.toggleMod(mod.namespace())).bounds(0, 0, 12, 16).build();
            this.expand.active = !searching;
            this.expand.visible = !searching;
            this.slider = new VolumeSlider(0, 0, 100, 20, VolumeStore.get().mod(mod.namespace()),
                    value -> screen.setModVolume(mod.namespace(), value));
            tooltip(this.slider, Component.literal(mod.displayName() + " - "
                    + Component.translatable("audio_management.volume.tooltip").getString()));
            this.reset = resetButton(() -> screen.confirmResetMod(mod));
            List<String> ids = GlobalConfig.get().idsOf(mod);
            this.black = listButton(ids, true, true);
            this.white = listButton(ids, false, true);
            widgets.add(expand);
            widgets.add(slider);
            widgets.add(reset);
            widgets.add(black);
            widgets.add(white);
        }

        @Override
        protected void layout(int top, int left, int width) {
            int right = left + width;
            place(expand, left + 2, top);
            placeSlider(slider, right - 218, top);
            place(reset, right - 114, top);
            place(black, right - 34, top);
            place(white, right - 16, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width, int height) {
            int right = left + width;
            drawName(graphics, mod.displayName(), left + 18, top, right - 218 - (left + 18) - 4, true, 0xFFFFFF);
        }
    }

    public static final class GroupRow extends Row {
        private final SoundCatalog.GroupInfo group;
        private final Button expand;
        private final VolumeSlider slider;
        private final Button reset;
        private final Button replace;
        private final Button revert;
        private final Button black;
        private final Button white;

        public GroupRow(MoreSoundScreen screen, SoundCatalog.GroupInfo group) {
            super(screen);
            this.group = group;
            boolean searching = screen.isSearching();
            this.expand = Button.builder(Component.literal(searching || screen.isGroupExpanded(group) ? "v" : ">"),
                    pressed -> screen.toggleGroup(group)).bounds(0, 0, 12, 16).build();
            this.expand.active = !searching;
            this.expand.visible = !searching;
            this.slider = new VolumeSlider(0, 0, 100, 20, VolumeStore.get().group(group.namespace(), group.path()),
                    value -> screen.setGroupVolume(group, value));
            tooltip(this.slider, Component.literal(group.namespace() + ":" + group.path()));
            this.reset = resetButton(() -> screen.confirmResetGroup(group));
            this.replace = listButton(Component.translatable("audio_management.short.replace"),
                    "audio_management.replace.tooltip", () -> screen.replaceGroup(group));
            this.revert = listButton(Component.translatable("audio_management.short.revert"),
                    "audio_management.revert.tooltip", () -> screen.confirmRevertGroup(group));
            boolean hasReplacement = ReplacementStore.get().hasGroupOgg(group.namespace(), group.path());
            this.replace.visible = !hasReplacement;
            this.replace.active = !hasReplacement;
            this.revert.visible = hasReplacement;
            this.revert.active = hasReplacement;
            List<String> ids = GlobalConfig.get().idsOf(group);
            this.black = listButton(ids, true, true);
            this.white = listButton(ids, false, true);
            widgets.add(expand);
            widgets.add(slider);
            widgets.add(reset);
            widgets.add(replace);
            widgets.add(revert);
            widgets.add(black);
            widgets.add(white);
        }

        @Override
        protected void layout(int top, int left, int width) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 218 - (left + 26) - 24);
            int offset = indent(group.depth(), maxOffset);
            place(expand, left + 12 + offset, top);
            placeSlider(slider, right - 218, top);
            place(reset, right - 114, top);
            place(replace, right - 70, top);
            place(revert, right - 70, top);
            place(black, right - 34, top);
            place(white, right - 16, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width, int height) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 218 - (left + 26) - 24);
            int offset = indent(group.depth(), maxOffset);
            drawName(graphics, group.name(), left + 26 + offset, top,
                    right - 218 - (left + 26 + offset) - 4, false, 0xFFFFFF);
        }
    }

    public static final class SoundRow extends Row {
        private final SoundCatalog.SoundInfo sound;
        private final int depth;
        private final VolumeSlider slider;
        private final Button reset;
        private final Button replace;
        private final Button revert;
        private final Button black;
        private final Button white;

        public SoundRow(MoreSoundScreen screen, SoundCatalog.SoundInfo sound, int depth) {
            super(screen);
            this.sound = sound;
            this.depth = depth;
            this.slider = new VolumeSlider(0, 0, 100, 20, VolumeStore.get().sound(sound.id()),
                    value -> screen.setSoundVolume(sound, value));
            tooltip(this.slider, Component.literal(sound.fullId()));
            this.reset = resetButton(() -> screen.confirmResetSound(sound));
            this.replace = listButton(Component.translatable("audio_management.short.replace"),
                    "audio_management.replace.tooltip", () -> screen.replaceSound(sound));
            this.revert = listButton(Component.translatable("audio_management.short.revert"),
                    "audio_management.revert.tooltip", () -> screen.confirmRevertSound(sound));
            boolean hasReplacement = ReplacementStore.get().hasSoundOgg(sound.id());
            this.replace.visible = !hasReplacement;
            this.replace.active = !hasReplacement;
            this.revert.visible = hasReplacement;
            this.revert.active = hasReplacement;
            List<String> ids = List.of(sound.fullId());
            this.black = listButton(ids, true, false);
            this.white = listButton(ids, false, false);
            widgets.add(slider);
            widgets.add(reset);
            widgets.add(replace);
            widgets.add(revert);
            widgets.add(black);
            widgets.add(white);
        }

        @Override
        protected void layout(int top, int left, int width) {
            int right = left + width;
            placeSlider(slider, right - 218, top);
            place(reset, right - 114, top);
            place(replace, right - 70, top);
            place(revert, right - 70, top);
            place(black, right - 34, top);
            place(white, right - 16, top);
        }

        @Override
        protected void draw(GuiGraphics graphics, int top, int left, int width, int height) {
            int right = left + width;
            int maxOffset = Math.max(0, right - 218 - (left + 36) - 24);
            int offset = indent(this.depth - 1, maxOffset);
            int color = GlobalConfig.get().isManaged(sound.fullId()) ? 0xFFFFFF : 0x777777;
            drawName(graphics, sound.name(), left + 36 + offset, top,
                    right - 218 - (left + 36 + offset) - 4, false, color);
        }
    }
}
