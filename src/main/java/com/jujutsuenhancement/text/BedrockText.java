package com.jujutsuenhancement.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Turns a Bedrock formatted string ("§l§e+2 §4力量") into a chat component with Bedrock's rules, which differ from
 * Java's:
 * <ul>
 *   <li>a colour code does <b>not</b> clear bold / italic / obfuscated (Java clears them), only {@code §r} does;</li>
 *   <li>{@code §g, §h, §i, §j, §m, §n, §p, §q, §s, §t, §u, §v} are the "material" colours (minecoin gold, quartz,
 *       iron, netherite, redstone, copper, gold, emerald, diamond, lapis, amethyst, resin); {@code §m} and {@code §n}
 *       are not strikethrough / underline.</li>
 * </ul>
 */
public final class BedrockText {
    private BedrockText() {}

    private static int color(char c) {
        return switch (c) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xC6C6C6;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            case 'g' -> 0xDDD605;
            case 'h' -> 0xE3D4D1;
            case 'i' -> 0xCECACA;
            case 'j' -> 0x443A3B;
            case 'm' -> 0x971607;
            case 'n' -> 0xB4684D;
            case 'p' -> 0xDEB12D;
            case 'q' -> 0x47A036;
            case 's' -> 0x2CBAA8;
            case 't' -> 0x21497B;
            case 'u' -> 0x9A5CC6;
            case 'v' -> 0xEB7114;
            default -> -1;
        };
    }

    /** The component of the whole string (any "\n" stays inside the text, use {@link #lines} for tooltips). */
    public static MutableComponent parse(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int rgb = color(code);
                boolean known = rgb >= 0 || code == 'l' || code == 'o' || code == 'k' || code == 'r';
                if (!known) {
                    run.append(ch);
                    continue;
                }
                if (run.length() > 0) {
                    out.append(Component.literal(run.toString()).withStyle(style));
                    run.setLength(0);
                }
                i++;
                if (rgb >= 0) style = style.withColor(TextColor.fromRgb(rgb));
                else if (code == 'l') style = style.withBold(true);
                else if (code == 'o') style = style.withItalic(true);
                else if (code == 'k') style = style.withObfuscated(true);
                else style = Style.EMPTY;
            } else {
                run.append(ch);
            }
        }
        if (run.length() > 0) out.append(Component.literal(run.toString()).withStyle(style));
        return out;
    }

    /** One component per "\n" line; the style of a line continues into the next one like it does in Bedrock lore. */
    public static java.util.List<Component> lines(String text) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) lines.add(parse(line));
        return lines;
    }
}
