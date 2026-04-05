package com.petplugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ChatUtil {

    private ChatUtil() {}

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    /** Translate &-color codes into a Component. */
    public static Component color(String text) {
        return SERIALIZER.deserialize(text);
    }

    /** Translate &-color codes into a plain §-prefixed string (for legacy APIs). */
    public static String colorLegacy(String text) {
        return text.replace('&', '§');
    }

    /** Alias for colorLegacy — used by sendTitle which takes String, not Component. */
    public static String legacyColor(String text) {
        return text.replace('&', '§');
    }

    /** Strip all color codes from a string. */
    public static String strip(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "")
                   .replaceAll("&[0-9a-fk-or]", "");
    }

    /** Build a formatted action-bar / title message. */
    public static Component format(String prefix, String message) {
        return color("&7[&bPetPlugin&7] &r" + prefix + message);
    }
}
