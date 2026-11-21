package com.winlator;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Links existing Windows game EXEs into the native Winlator library without copying game data. */
public final class MRGameLibrary {
    private static final String RUNTIME_NAME = "MR Games Runtime";
    private static final String PROFILE_PREFIX = "MR PlayStation - ";
    private static final int PROFILE_LAYOUT_VERSION = 2;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface ImportCallback {
        void onFinished(Shortcut shortcut, String message);
    }

    private static final class StagedGame {
        final File exe;
        final String title;
        StagedGame(File exe, String title) {
            this.exe = exe;
            this.title = title;
        }
    }

    private MRGameLibrary() {}

    public static void importGame(Activity activity, Uri uri, ImportCallback callback) {
        IO.execute(() -> {
            StagedGame game;
            try {
                game = stageSelectedExe(activity, uri);
            }
            catch (Exception e) {
                postResult(callback, null, "Couldn't read this EXE file: " + safeMessage(e));
                return;
            }

            MAIN.post(() -> {
                if (activity.isFinishing()) {
                    postResult(callback, null, "The import screen was closed before the game could be added.");
                    return;
                }
                try {
                    ContainerManager manager = new ContainerManager(activity);
                    Container runtime = findRuntime(manager.getContainers());
                    if (runtime != null) {
                        createShortcut(activity, manager, runtime, game, callback);
                        return;
                    }

                    JSONObject data = new JSONObject();
                    data.put("name", RUNTIME_NAME);
                    data.put("screenSize", MRGamePerformance.getDefaultScreenSize(activity));
                    data.put("box64Preset", MRGamePerformance.getDefaultBox64Preset(activity));
                    manager.createContainerAsync(data, container -> {
                        if (container == null) {
                            postResult(callback, null, "Couldn't create the MR Games runtime. Open Containers once and try again.");
                        }
                        else createShortcut(activity, manager, container, game, callback);
                    });
                }
                catch (Exception e) {
                    postResult(callback, null, "Couldn't prepare the game runtime: " + safeMessage(e));
                }
            });
        });
    }

    private static StagedGame stageSelectedExe(Activity activity, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("No file was selected.");
        String displayName = getDisplayName(activity.getContentResolver(), uri);
        if (displayName == null || !displayName.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
            throw new IllegalArgumentException("Please select a Windows .exe file.");
        }

        // Direct-launch mode: never duplicate a large game folder or copy only
        // the EXE. The complete original directory stays where the user installed it.
        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            activity.getContentResolver().takePersistableUriPermission(uri, flags);
        }
        catch (SecurityException | UnsupportedOperationException ignored) {
            // Some document providers do not offer persistable grants. A real
            // filesystem path is still usable when it belongs to a mapped drive.
        }

        String resolvedPath = FileUtils.getFilePathFromUri(uri);
        File source = resolvedPath != null ? new File(resolvedPath) : null;
        if (source == null || !source.isFile() || !source.canRead()) {
            throw new IllegalArgumentException(
                    "Android did not expose a filesystem path for this EXE. Move the complete game folder to the phone's Download folder, then select the EXE again.");
        }
        if (!isMappedDrivePath(source)) {
            throw new IllegalArgumentException(
                    "This folder is not visible to the Winlator shared drives. Move the complete game folder to Download or MR Games shared storage, then select the EXE again.");
        }

        File gameFolder = source.getParentFile();
        if (gameFolder == null || !gameFolder.isDirectory()) {
            throw new IllegalArgumentException("The original game folder could not be found.");
        }

        String title = displayName.substring(0, displayName.length() - 4).trim();
        title = safeFileName(title);
        return new StagedGame(source, title);
    }

    private static boolean isMappedDrivePath(File file) {
        // Keep the app's configured path spelling: INTERNAL_STORAGE can resolve
        // through a /data/data -> /data/user/0 symlink, while WineUtils uses the
        // original configured prefix when translating Android paths to drive E:.
        String path = file.getAbsolutePath();
        return isInside(path, AppUtils.DIRECTORY_DOWNLOADS) || isInside(path, AppUtils.INTERNAL_STORAGE);
    }

    private static boolean isInside(String path, String root) {
        if (path == null || root == null || root.isEmpty()) return false;
        String normalizedRoot = root.endsWith(File.separator) ? root : root + File.separator;
        return path.equals(root) || path.startsWith(normalizedRoot);
    }

    private static void createShortcut(Activity activity, ContainerManager manager, Container container,
                                       StagedGame game, ImportCallback callback) {
        try {
            String dosPath = WineUtils.unixToDOSPath(game.exe.getAbsolutePath(), container);
            if (dosPath.isEmpty() || !dosPath.contains(":")) {
                postResult(callback, null, "The selected EXE is outside the runtime's shared drives.");
                return;
            }

            int profileId = ensurePlayStationProfile(activity, game.title);
            File desktopDir = new File(container.getUserDir(), "Desktop");
            if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) {
                postResult(callback, null, "Couldn't create the game shortcut folder.");
                return;
            }
            File shortcutFile = new File(desktopDir, safeFileName(game.title) + ".desktop");
            String content = "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=" + game.title.replace("\n", " ").replace("\r", " ") + "\n" +
                    "Exec=env WINEDEBUG=-all wine " + escapeShortcutExecPath(dosPath) + "\n" +
                    "Terminal=false\n" +
                    "\n[Extra Data]\n" +
                    "controlsProfile=" + profileId + "\n";
            if (!FileUtils.writeString(shortcutFile, content)) throw new IllegalStateException("Could not save the game shortcut.");
            Shortcut shortcut = new Shortcut(container, shortcutFile);
            String gameFolder = dosPath.contains("\\") ? dosPath.substring(0, dosPath.lastIndexOf('\\')) : dosPath;
            String message = "Game linked without copying. MR Games will run the EXE from its original folder: " + gameFolder + ".\nNo second copy of the game was created. Keep the original folder in place. The PlayStation-style touchscreen controls are linked to this game.";
            postResult(callback, shortcut, message);
        }
        catch (Exception e) {
            postResult(callback, null, "Couldn't create the game shortcut: " + safeMessage(e));
        }
    }

    private static int ensurePlayStationProfile(Activity activity, String gameTitle) throws JSONException {
        InputControlsManager controlsManager = new InputControlsManager(activity);
        String profileName = PROFILE_PREFIX + gameTitle;
        ArrayList<ControlsProfile> profiles = controlsManager.getProfiles(false);
        ControlsProfile profile = null;
        for (ControlsProfile candidate : profiles) {
            if (profileName.equals(candidate.getName())) {
                profile = candidate;
                break;
            }
        }
        if (profile == null) profile = controlsManager.createProfile(profileName);

        File profileFile = ControlsProfile.getProfileFile(activity, profile.id);
        if (profileFile.isFile()) {
            try {
                JSONObject previous = new JSONObject(readProfileText(profileFile));
                if (previous.optInt("mrLayoutVersion", 0) >= PROFILE_LAYOUT_VERSION) {
                    // Keep any layout customizations made by the player after this version.
                    return profile.id;
                }
            }
            catch (Exception ignored) {
                // Regenerate a malformed or older MR profile with the known-good layout.
            }
        }

        JSONObject data = new JSONObject();
        data.put("id", profile.id);
        data.put("name", profileName);
        data.put("mrLayoutVersion", PROFILE_LAYOUT_VERSION);
        data.put("cursorSpeed", 1.0);
        // The empty center area remains a mouse surface, so OK / Next dialogs can be tapped.
        data.put("disableMouseInput", false);
        data.put("hideMouseCursor", false);
        JSONArray elements = new JSONArray();

        // Left side: D-pad and analog stick, with enough horizontal space between them.
        elements.put(element("D_PAD", "CIRCLE", 0.115, 0.60, 0.96, "", "GAMEPAD_DPAD_UP", "GAMEPAD_DPAD_RIGHT", "GAMEPAD_DPAD_DOWN", "GAMEPAD_DPAD_LEFT"));
        elements.put(element("STICK", "CIRCLE", 0.295, 0.79, 0.94, "", "GAMEPAD_LEFT_THUMB_UP", "GAMEPAD_LEFT_THUMB_RIGHT", "GAMEPAD_LEFT_THUMB_DOWN", "GAMEPAD_LEFT_THUMB_LEFT"));

        // Right analog stick and PlayStation face buttons form two separate groups.
        elements.put(element("STICK", "CIRCLE", 0.685, 0.79, 0.94, "", "GAMEPAD_RIGHT_THUMB_UP", "GAMEPAD_RIGHT_THUMB_RIGHT", "GAMEPAD_RIGHT_THUMB_DOWN", "GAMEPAD_RIGHT_THUMB_LEFT"));
        elements.put(element("BUTTON", "CIRCLE", 0.790, 0.60, 0.78, "□", "GAMEPAD_BUTTON_X"));
        elements.put(element("BUTTON", "CIRCLE", 0.870, 0.495, 0.78, "△", "GAMEPAD_BUTTON_Y"));
        elements.put(element("BUTTON", "CIRCLE", 0.950, 0.60, 0.78, "○", "GAMEPAD_BUTTON_B"));
        elements.put(element("BUTTON", "CIRCLE", 0.870, 0.705, 0.78, "✕", "GAMEPAD_BUTTON_A"));

        // Shoulder row is evenly spaced and does not overlap the D-pad or face buttons.
        elements.put(element("BUTTON", "ROUND_RECT", 0.105, 0.12, 0.70, "L2", "GAMEPAD_BUTTON_L2"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.265, 0.12, 0.70, "L1", "GAMEPAD_BUTTON_L1"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.735, 0.12, 0.70, "R1", "GAMEPAD_BUTTON_R1"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.895, 0.12, 0.70, "R2", "GAMEPAD_BUTTON_R2"));

        // Bottom-center system buttons are compact and kept clear of both sticks.
        elements.put(element("BUTTON", "CIRCLE", 0.385, 0.94, 0.58, "L3", "GAMEPAD_BUTTON_L3"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.455, 0.94, 0.58, "SELECT", "GAMEPAD_BUTTON_SELECT"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.545, 0.94, 0.58, "START", "GAMEPAD_BUTTON_START"));
        elements.put(element("BUTTON", "CIRCLE", 0.615, 0.94, 0.58, "R3", "GAMEPAD_BUTTON_R3"));

        data.put("elements", elements);
        if (!FileUtils.writeString(profileFile, data.toString())) throw new IllegalStateException("Could not save the game controller profile.");
        return profile.id;
    }

    private static String readProfileText(File profileFile) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(profileFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static JSONObject element(String type, String shape, double x, double y, double scale,
                                      String text, String... bindings) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("type", type);
        value.put("shape", shape);
        JSONArray bindingArray = new JSONArray();
        for (int i = 0; i < 4; i++) bindingArray.put(i < bindings.length ? bindings[i] : "NONE");
        value.put("bindings", bindingArray);
        value.put("scale", scale);
        value.put("opacity", 0.66);
        value.put("x", x);
        value.put("y", y);
        value.put("toggleSwitch", false);
        value.put("text", text);
        value.put("iconId", 0);
        return value;
    }

    public static void launchShortcut(Activity activity, Shortcut shortcut) {
        if (activity == null || shortcut == null) return;
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        activity.startActivity(intent);
    }

    private static Container findRuntime(ArrayList<Container> containers) {
        for (Container container : containers) if (RUNTIME_NAME.equals(container.getName())) return container;
        return null;
    }

    private static String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        }
        catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? last : "WindowsGame.exe";
    }

    // Shortcut parses its Exec field with StringUtils.unescapeDOSPath(), which
    // consumes two escape layers. Encode separators fourfold and spaces twofold
    // so the native Shortcut parser reconstructs the exact DOS path.
    private static String escapeShortcutExecPath(String path) {
        return path.replace("\\", "\\\\\\\\").replace(" ", "\\\\ ");
    }

    private static String safeFileName(String value) {
        String safe = StringUtils.clearReservedChars(value).replaceAll("[\\r\\n]+", " ").trim();
        safe = safe.replaceAll("\\s+", " ");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "Windows Game";
        return safe.length() > 72 ? safe.substring(0, 72).trim() : safe;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message != null && !message.trim().isEmpty() ? message : error.getClass().getSimpleName();
    }

    private static void postResult(ImportCallback callback, Shortcut shortcut, String message) {
        MAIN.post(() -> callback.onFinished(shortcut, message));
    }
}
