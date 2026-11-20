package com.omniarena.health;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** File backups with rotation (plain java.io, unit-testable). */
public final class BackupUtil {

    private BackupUtil() {}

    public static String timestamp(long millis) {
        return new SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.ROOT).format(new Date(millis));
    }

    /**
     * Copies {@code source} to {@code backupDir/<name-without-ext>-<timestamp>.yml} and keeps only the newest
     * {@code keep} backups of that file.
     * @return the backup file, or null if the copy failed
     */
    public static File backup(File source, File backupDir, int keep) {
        if (!source.isFile()) return null;
        if (!backupDir.isDirectory() && !backupDir.mkdirs()) return null;
        String base = baseName(source);
        File dest = freshName(backupDir, base);
        try {
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            return null;
        }
        prune(backupDir, base, keep);
        return dest;
    }

    /** Moves {@code source} into {@code dir} under a timestamped name. @return the new file or null. */
    public static File moveAside(File source, File dir) {
        if (!source.isFile()) return null;
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File dest = freshName(dir, baseName(source));
        try {
            Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (IOException e) {
            return null;
        }
    }

    /** Deletes the oldest backups of {@code base} so at most {@code keep} remain. */
    public static void prune(File backupDir, String base, int keep) {
        File[] all = backupDir.listFiles((d, name) -> name.startsWith(base + "-") && name.endsWith(".yml"));
        int limit = Math.max(1, keep);
        if (all == null || all.length <= limit) return;
        // Timestamped names sort chronologically
        Arrays.sort(all, Comparator.comparing(File::getName).reversed());
        for (int i = limit; i < all.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            all[i].delete();
        }
    }

    private static File freshName(File dir, String base) {
        String ts = timestamp(System.currentTimeMillis());
        File dest = new File(dir, base + "-" + ts + ".yml");
        int n = 1;
        while (dest.exists()) dest = new File(dir, base + "-" + ts + "-" + (n++) + ".yml");
        return dest;
    }

    private static String baseName(File f) {
        String n = f.getName();
        return n.endsWith(".yml") ? n.substring(0, n.length() - 4) : n;
    }
}
