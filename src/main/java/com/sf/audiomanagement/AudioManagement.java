package com.sf.audiomanagement;

import com.sf.audiomanagement.config.GlobalConfig;
import com.sf.audiomanagement.config.ReplacementStore;
import com.sf.audiomanagement.config.SoundCatalog;
import com.sf.audiomanagement.config.VolumeStore;

public final class AudioManagement {
    private static boolean ready;

    private AudioManagement() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static void init() {
        if (ready) {
            return;
        }
        GlobalConfig.load();
        SoundCatalog.load();
        VolumeStore.get().load();
        ReplacementStore.get().refresh();
        ready = true;
        AudioManagementMod.LOGGER.info("Audio Management loaded {} sound events", SoundCatalog.get().soundCount());
    }
}
