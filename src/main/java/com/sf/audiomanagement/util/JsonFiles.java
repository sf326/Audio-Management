package com.sf.audiomanagement.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() {
    }

    public static int readVolume(Path path) {
        if (!Files.isRegularFile(path)) {
            return 100;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonPrimitive()) {
                return element.getAsInt();
            }
            if (element.isJsonObject()) {
                JsonElement volume = element.getAsJsonObject().get("volume");
                if (volume != null) {
                    return volume.getAsInt();
                }
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    public static void writeVolume(Path path, int volume) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject object = new JsonObject();
        object.addProperty("volume", volume);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(object, writer);
        }
    }
}
