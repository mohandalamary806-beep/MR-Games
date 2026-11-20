package com.winlator;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;

import java.util.Locale;

/** Device-aware defaults for new game containers. Existing games keep their own settings. */
public final class MRGamePerformance {
    public static final String PROFILE_AUTO = "AUTO";
    public static final String PROFILE_LOW = "LOW";
    public static final String PROFILE_BALANCED = "BALANCED";
    public static final String PROFILE_QUALITY = "QUALITY";
    public static final String PROFILE_STABILITY = "STABILITY";
    private static final String PREF_PROFILE = "mr_games_profile";
    private static final String PREF_RESOLUTION = "mr_games_resolution";

    private MRGamePerformance() {}

    public static String getSelectedProfile(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_PROFILE, PROFILE_AUTO);
    }

    public static String getEffectiveProfile(Context context) {
        String selected = getSelectedProfile(context);
        return PROFILE_AUTO.equals(selected) ? detectRecommendedProfile(context) : selected;
    }

    public static String detectRecommendedProfile(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        long ramBytes = 0L;
        if (manager != null) {
            manager.getMemoryInfo(memoryInfo);
            ramBytes = memoryInfo.totalMem;
        }
        double ramGb = ramBytes / (1024.0d * 1024.0d * 1024.0d);
        int cores = Runtime.getRuntime().availableProcessors();
        boolean vulkan = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);

        // Heuristic recommendation only; it does not benchmark or guarantee a game's FPS.
        // If RAM is unavailable, still use the CPU/Vulkan signals instead of ignoring them.
        if (cores <= 4 || !vulkan || (ramGb > 0 && ramGb < 5.5d)) return PROFILE_LOW;
        if (ramGb <= 0 || ramGb < 7.5d || cores <= 6) return PROFILE_BALANCED;
        return PROFILE_QUALITY;
    }

    public static String getDefaultScreenSize(Context context) {
        String saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_RESOLUTION, "AUTO");
        if (saved != null && !saved.equals("AUTO") && saved.matches("[0-9]{3,4}x[0-9]{3,4}")) return saved;
        switch (getEffectiveProfile(context)) {
            case PROFILE_LOW: return "640x360";
            case PROFILE_QUALITY: return "1280x720";
            case PROFILE_STABILITY: return "854x480";
            case PROFILE_BALANCED:
            default: return "960x544";
        }
    }

    public static String getDefaultBox64Preset(Context context) {
        switch (getEffectiveProfile(context)) {
            case PROFILE_LOW: return Box64Preset.MR_LOW_END;
            case PROFILE_QUALITY: return Box64Preset.MR_HIGH_VISUAL;
            case PROFILE_STABILITY: return Box64Preset.MR_STABILITY;
            case PROFILE_BALANCED:
            default: return Box64Preset.MR_BALANCED;
        }
    }

    public static String getDeviceSummary(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) manager.getMemoryInfo(memoryInfo);
        double ramGb = memoryInfo.totalMem / (1024.0d * 1024.0d * 1024.0d);
        int cores = Runtime.getRuntime().availableProcessors();
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null;
        if (soc == null || soc.trim().isEmpty()) soc = "Not exposed by Android";
        String hardware = Build.HARDWARE == null || Build.HARDWARE.trim().isEmpty() ? "Unavailable" : Build.HARDWARE;
        boolean vulkan = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL);
        String ram = ramGb > 0 ? String.format(Locale.US, "%.1f GB RAM", ramGb) : "RAM info unavailable";
        String vulkanText = vulkan ? "Vulkan hardware feature declared" : "Vulkan hardware feature not reported";
        return model + "\nSoC: " + soc + "\nHardware ID: " + hardware + "\n" + ram + "  •  " + cores + " CPU cores\n" + vulkanText + "  •  Android " + Build.VERSION.RELEASE;
    }

    public static String getRecommendedProfileLabel(Context context) {
        switch (detectRecommendedProfile(context)) {
            case PROFILE_LOW: return "Low-End";
            case PROFILE_QUALITY: return "High Quality";
            default: return "Balanced";
        }
    }

    public static String getProfileLabel(Context context) {
        switch (getEffectiveProfile(context)) {
            case PROFILE_LOW: return "Low-End";
            case PROFILE_QUALITY: return "High Quality";
            case PROFILE_STABILITY: return "Stability";
            default: return "Balanced";
        }
    }
}
