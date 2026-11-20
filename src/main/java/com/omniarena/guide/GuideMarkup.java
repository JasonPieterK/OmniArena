package com.omniarena.guide;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helper that expands the guide shorthand for clickable commands into MiniMessage.
 *
 * <ul>
 *   <li>{@code {{/oa setup <arena>}}}: shows the command, clicking puts {@code /oa setup } in the chat box.</li>
 *   <li>{@code {{!/oa menu}}}: shows the command, clicking runs it at once (use for harmless commands only).</li>
 * </ul>
 * Everything else in a guide is plain MiniMessage.
 */
public final class GuideMarkup {

    private static final Pattern CMD = Pattern.compile("\\{\\{(!?)(/[^}]*?)\\}\\}");
    private static final String COLOR = "dark_aqua";

    private GuideMarkup() {}

    public static String expand(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = CMD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            boolean run = !m.group(1).isEmpty();
            String shown = m.group(2).trim();
            String base = base(shown);
            String action = run ? "run_command" : "suggest_command";
            String target = run ? base : base + " ";
            String hover = run ? "Click to run " + base.trim() : "Click to type this command";
            String repl = "<click:" + action + ":'" + quote(target) + "'><hover:show_text:'" + quote(hover) + "'><"
                    + COLOR + ">" + escape(shown) + "</" + COLOR + "></hover></click>";
            m.appendReplacement(out, Matcher.quoteReplacement(repl));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The part of a command before its first placeholder ({@code <x>} or {@code [x]}), trimmed. */
    static String base(String shown) {
        int cut = shown.length();
        for (int i = 0; i < shown.length(); i++) {
            char c = shown.charAt(i);
            if (c == '<' || c == '[') { cut = i; break; }
        }
        return shown.substring(0, cut).trim();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static String quote(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
