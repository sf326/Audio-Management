package com.sf.audiomanagement.client;

import com.sf.audiomanagement.AudioManagement;
import com.sf.audiomanagement.AudioManagementMod;
import com.sf.audiomanagement.config.ReplacementStore;
import com.sf.audiomanagement.config.VolumeStore;
import com.sf.audiomanagement.gui.MoreSoundScreen;
import com.sf.audiomanagement.sound.SoundReplacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AudioManagementMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundReplacer.play(event);
    }

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        SoundReplacer.boost(event);
    }

    @SubscribeEvent
    public static void onPlayStreamingSource(PlayStreamingSourceEvent event) {
        SoundReplacer.boost(event);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SoundOptionsScreen screen)) {
            return;
        }
        Component moreLabel = Component.translatable("audio_management.button.more");
        Button done = null;
        Button existing = null;
        for (GuiEventListener listener : event.getListenersList()) {
            if (!(listener instanceof Button button)) {
                continue;
            }
            if (button.getMessage().equals(CommonComponents.GUI_DONE)) {
                done = button;
            }
            if (button.getMessage().equals(moreLabel)) {
                existing = button;
            }
        }

        int total = Math.max(120, Math.min(310, screen.width - 20));
        int half = (total - 10) / 2;
        int left = screen.width / 2 - total / 2;
        if (done != null) {
            done.setX(left);
            done.setWidth(half);
        }
        int moreX = done != null ? left + half + 10 : screen.width / 2 - 75;
        int moreWidth = done != null ? half : 150;
        if (existing != null) {
            existing.setX(moreX);
            existing.setY(screen.height - 27);
            existing.setWidth(moreWidth);
            return;
        }
        Button more = Button.builder(moreLabel,
                        button -> Minecraft.getInstance().setScreen(new MoreSoundScreen(screen)))
                .bounds(moreX, screen.height - 27, moreWidth, 20)
                .build();
        event.addListener(more);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !AudioManagement.isReady()) {
            return;
        }
        VolumeStore.get().flushIfDirty();
        ReplacementStore.get().tick();
    }
}
