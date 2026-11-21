package com.sf.audiomanagement.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class OggFilePickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Path> onSelect;
    private Path directory;
    private EditBox pathField;
    private FileList list;

    public OggFilePickerScreen(Screen parent, Consumer<Path> onSelect) {
        super(Component.translatable("audio_management.file.title"));
        this.parent = parent;
        this.onSelect = onSelect;
        this.directory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int contentWidth = Math.max(200, this.width - 20);
        int contentLeft = this.width / 2 - contentWidth / 2;
        boolean compact = this.height < 200;
        int pathY = compact ? 6 : 20;
        int listTop = compact ? 30 : 46;
        int listBottom = Math.max(listTop + 1, this.height - 32);
        int goWidth = 50;
        int fieldWidth = contentWidth - goWidth - 6;

        this.pathField = new EditBox(this.font, contentLeft, pathY, fieldWidth, 18,
                Component.translatable("audio_management.file.path"));
        this.pathField.setMaxLength(1024);
        this.pathField.setValue(this.directory.toString());
        this.pathField.setHint(Component.translatable("audio_management.file.path"));
        this.addRenderableWidget(this.pathField);
        this.addRenderableWidget(Button.builder(Component.translatable("audio_management.file.go"),
                        button -> this.goTo(this.pathField.getValue()))
                .bounds(contentLeft + fieldWidth + 6, pathY, goWidth, 18).build());

        this.list = new FileList(this.minecraft, this.width, this.height, listTop, listBottom);
        this.addRenderableWidget(this.list);

        int cancelWidth = Math.min(200, contentWidth);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(this.width / 2 - cancelWidth / 2, this.height - 27, cancelWidth, 20).build());
        this.rebuild();
    }

    private void rebuild() {
        this.list.clearRows();
        Path parentPath = this.directory.getParent();
        if (parentPath != null) {
            this.list.addRow(new FileRow(this, parentPath, true, ".."));
        }
        List<Path> directories = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    directories.add(entry);
                } else if (Files.isRegularFile(entry)
                        && nameOf(entry).toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                    files.add(entry);
                }
            }
        } catch (Exception exception) {
            this.message("audio_management.file.error");
            return;
        }
        Comparator<Path> order = Comparator.comparing(path -> nameOf(path).toLowerCase(Locale.ROOT));
        directories.sort(order);
        files.sort(order);
        for (Path path : directories) {
            this.list.addRow(new FileRow(this, path, true, nameOf(path) + "/"));
        }
        for (Path path : files) {
            this.list.addRow(new FileRow(this, path, false, nameOf(path)));
        }
        this.list.setScrollAmount(0);
    }

    private void goTo(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(raw.trim());
            if (!path.isAbsolute()) {
                path = this.directory.resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                this.directory = path;
                this.pathField.setValue(path.toString());
                this.rebuild();
            } else if (Files.isRegularFile(path)
                    && nameOf(path).toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                this.selectFile(path);
            } else {
                this.message("audio_management.file.invalid");
            }
        } catch (Exception exception) {
            this.message("audio_management.file.invalid");
        }
    }

    public void openDirectory(Path path) {
        if (Files.isDirectory(path)) {
            this.directory = path.toAbsolutePath().normalize();
            this.pathField.setValue(this.directory.toString());
            this.rebuild();
        }
    }

    public void selectFile(Path path) {
        this.onSelect.accept(path);
        this.minecraft.setScreen(this.parent);
    }

    private void message(String key) {
        this.minecraft.gui.setOverlayMessage(Component.translatable(key), false);
    }

    private static String nameOf(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
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
        if (this.height >= 200) {
            graphics.drawCenteredString(this.font, this.getTitle(), this.width / 2, 5, 0xFFFFFF);
        }
        Component tooltip = null;
        if (this.list != null) {
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

    private static final class FileList extends ContainerObjectSelectionList<FileRow> {
        private FileList(Minecraft minecraft, int width, int height, int top, int bottom) {
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

        public void addRow(FileRow row) {
            this.addEntry(row);
        }

        public Component getTooltipAt(double mouseX, double mouseY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return null;
            }
            FileRow row = this.getEntryAtPosition(mouseX, mouseY);
            return row == null ? null : row.tooltipAt(mouseX, mouseY);
        }
    }

    private static final class FileRow extends ContainerObjectSelectionList.Entry<FileRow> {
        private final Button button;
        private final Component tooltip;
        private final String label;

        private FileRow(OggFilePickerScreen screen, Path path, boolean directory, String label) {
            this.tooltip = Component.literal(path.toString());
            this.label = label;
            this.button = Button.builder(Component.literal(label), pressed -> {
                if (directory) {
                    screen.openDirectory(path);
                } else {
                    screen.selectFile(path);
                }
            }).bounds(0, 0, 100, 18).build();
        }

        private Component tooltipAt(double mouseX, double mouseY) {
            return this.button.visible && this.button.active && this.button.isMouseOver(mouseX, mouseY)
                    ? this.tooltip : null;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int max = Math.max(8, width - 12);
            String text = this.label;
            if (Minecraft.getInstance().font.width(text) > max) {
                text = Minecraft.getInstance().font.plainSubstrByWidth(text, Math.max(2, max - 6)) + "…";
            }
            this.button.setMessage(Component.literal(text));
            this.button.setX(left);
            this.button.setY(top + 1);
            this.button.setWidth(width);
            this.button.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
