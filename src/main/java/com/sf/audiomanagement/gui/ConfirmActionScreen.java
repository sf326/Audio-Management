package com.sf.audiomanagement.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class ConfirmActionScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable action;

    public ConfirmActionScreen(Screen parent, Component title, Component message, Runnable action) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.action = action;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, this.width - 40);
        int panelHeight = 120;
        int x = this.width / 2 - panelWidth / 2;
        int y = this.height / 2 - panelHeight / 2;
        int buttonWidth = (panelWidth - 30) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("audio_management.confirm.yes"), button -> {
            this.action.run();
            this.minecraft.setScreen(this.parent);
        }).bounds(x + 10, y + panelHeight - 28, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("audio_management.confirm.cancel"),
                        button -> this.onClose())
                .bounds(x + 20 + buttonWidth, y + panelHeight - 28, panelWidth - 30 - buttonWidth, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);
        int panelWidth = Math.min(420, this.width - 40);
        int panelHeight = 120;
        int x = this.width / 2 - panelWidth / 2;
        int y = this.height / 2 - panelHeight / 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF0101010);
        graphics.fill(x, y, x + panelWidth, y + 1, 0xFF555555);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF555555);
        graphics.drawCenteredString(this.font, this.getTitle(), this.width / 2, y + 10, 0xFFFFFF);
        List<FormattedCharSequence> lines = this.font.split(this.message, panelWidth - 20);
        for (int index = 0; index < lines.size() && index < 4; index++) {
            graphics.drawString(this.font, lines.get(index), x + 10, y + 34 + index * 10, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
