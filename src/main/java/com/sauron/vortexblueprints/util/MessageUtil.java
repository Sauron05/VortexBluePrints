package com.sauron.vortexblueprints.util;

import java.text.DecimalFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_TITLE = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static String prefix = "";

    private MessageUtil() {
    }

    public static void setPrefix(String configuredPrefix) {
        prefix = configuredPrefix == null ? "" : configuredPrefix;
    }

    public static Component parse(String miniMessage, String... replacements) {
        String resolved = miniMessage == null ? "" : miniMessage.replace("<prefix>", prefix);
        TagResolver.Builder builder = TagResolver.builder();
        for (int replacementIndex = 0; replacementIndex < replacements.length - 1; replacementIndex += 2) {
            builder.resolver(Placeholder.unparsed(replacements[replacementIndex], replacements[replacementIndex + 1]));
        }
        return MINI_MESSAGE.deserialize(resolved, builder.build());
    }

    public static void send(CommandSender sender, String miniMessage, String... replacements) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return;
        }
        sender.sendMessage(parse(miniMessage, replacements));
    }

    public static String title(String miniMessage, String... replacements) {
        return LEGACY_TITLE.serialize(parse(miniMessage, replacements));
    }

    public static String number(double amount) {
        return DECIMAL_FORMAT.format(amount);
    }

    public static String percent(double value) {
        return DECIMAL_FORMAT.format(value * 100.0D);
    }

    public static String formatLocation(Location location) {
        if (location.getWorld() == null) {
            return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
        }
        return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}