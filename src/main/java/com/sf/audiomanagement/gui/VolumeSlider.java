package com.sf.audiomanagement.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public final class VolumeSlider extends AbstractSliderButton {
    private final IntConsumer listener;

    public VolumeSlider(int x, int y, int width, int height, int percent, IntConsumer listener) {
        super(x, y, width, height, Component.empty(), clamp(percent) / 200.0);
        this.listener = listener;
        this.updateMessage();
    }

    public int percent() {
        return (int) Math.round(this.value * 200.0);
    }

    public void setPercent(int percent) {
        this.value = clamp(percent) / 200.0;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(percent() + "%"));
    }

    @Override
    protected void applyValue() {
        this.listener.accept(percent());
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(200, percent));
    }
}
