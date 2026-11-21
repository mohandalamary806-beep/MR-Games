package com.winlator;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Imports complete Windows game folders and creates native Winlator shortcuts. */
public final class MRGameLibrary {
    private static final String RUNTIME_NAME = "MR Games Runtime";
    private static final String PROFILE_PREFIX = "MR PlayStation - ";
    private static final int MAX_IMPORT_FILES = 30000;
    private static final long MAX_IMPORT_BYTES = 20L * 1024L * 1024L * 1024L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface ImportCallback {
        void onFinished(Shortcut shortcut, String message, boolean needsGameFolder);
    }

    private static final class StagedGame {
        final File exe;
        final String title;
        final boolean importedFolder;
        StagedGame(File exe, String title, boolean importedFolder) {
            this.exe = exe;
            this.title = title;
            this.importedFolder = importedFolder;
        }
    }

    private static final class CopyState {
        final String selectedDocumentId;
        final String selectedName;
        final ArrayList<File> exactMatches = new ArrayList<>();
        final ArrayList<File> nameMatches = new ArrayList<>();
        int files;
        long bytes;
        CopyState(String selectedDocumentId, String selectedName) {
            this.selectedDocumentId = selectedDocumentId;
            this.selectedName = selectedName;
        }
    }

    private static final class NeedsFolderSelectionException extends Exception {
        NeedsFolderSelectionException() { super("Choose the folder containing the game so its DLL and data files can be imported too."); }
    }

    private MRGameLibrary() {}

    /** Link directly only to an EXE inside a verified, complete MR Games import. */
    public static void importGame(Activity activity, Uri exeUri, ImportCallback callback) {
        IO.execute(() -> {
            try {
                StagedGame game = stageSelectedExe(activity, exeUri);
                prepareRuntime(activity, game, callback);
            }
            catch (NeedsFolderSelectionException e) {
                postResult(callback, null, e.getMessage(), true);
            }
            catch (Exception e) {
                postResult(callback, null, "Couldn't read this EXE file: " + safeMessage(e), false);
            }
        });
    }

    /** Import the selected EXE together with every file and subfolder in its SAF game folder. */
    public static void importGameFromFolder(Activity activity, Uri exeUri, Uri treeUri, ImportCallback callback) {
        IO.execute(() -> {
            try {
                StagedGame game = stageSelectedFolder(activity, exeUri, treeUri);
                prepareRuntime(activity, game, callback);
            }
            catch (Exception e) {
                postResult(callback, null, "Couldn't import the game folder: " + safeMessage(e), false);
            }
        });
    }

    private static StagedGame stageSelectedExe(Activity activity, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("No file was selected.");
        String displayName = getDisplayName(activity.getContentResolver(), uri);
        if (displayName == null || !displayName.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
            throw new IllegalArgumentException("Please select a Windows .exe file.");
        }

        String resolvedPath = FileUtils.getFilePathFromUri(uri);
        File source = resolvedPath != null ? new File(resolvedPath) : null;
        File libraryRoot = new File(AppUtils.INTERNAL_STORAGE, "MR Games Library");
        // Only skip the folder-import prompt for games already imported by MR Games.
        // Downloads and arbitrary shared folders are imported as a complete tree so
        // the executable never gets separated from its DLLs and data files.
        if (source != null && source.isFile() && source.canRead()
                && isInside(source.getAbsolutePath(), libraryRoot.getAbsolutePath())) {
            String relative = source.getAbsolutePath().substring(libraryRoot.getAbsolutePath().length());
            String relativePath = relative.startsWith(File.separator) ? relative.substring(1) : relative;
            int separator = relativePath.indexOf(File.separatorChar);
            String folderName = separator >= 0 ? relativePath.substring(0, separator) : relativePath;
            File importedFolder = new File(libraryRoot, folderName);
            File importMarker = new File(importedFolder, ".mr_games_import_complete");
            if (importMarker.isFile()) {
                String title = safeFileName(folderName.isEmpty()
                        ? displayName.substring(0, displayName.length() - 4).trim() : folderName);
                return new StagedGame(source, title, false);
            }
        }

        // A single EXE is not a complete game import. Always ask for its containing
        // folder unless it is inside a verified complete MR Games import.
        throw new NeedsFolderSelectionException();
    }

    private static StagedGame stageSelectedFolder(Activity activity, Uri exeUri, Uri treeUri) throws Exception {
        if (exeUri == null || treeUri == null || !DocumentsContract.isTreeUri(treeUri)) {
            throw new IllegalArgumentException("Select the game folder in the Android folder picker.");
        }
        ContentResolver resolver = activity.getContentResolver();
        String exeName = getDisplayName(resolver, exeUri);
        if (exeName == null || !exeName.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
            throw new IllegalArgumentException("The selected file is not a Windows .exe.");
        }

        String exeTitle = safeFileName(exeName.substring(0, exeName.length() - 4).trim());
        String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        if (rootDocumentId == null || rootDocumentId.trim().isEmpty() || rootDocumentId.endsWith(":")) {
            throw new IllegalArgumentException("Choose the actual game folder, not the storage root.");
        }
        Uri rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId);
        String folderDisplayName = queryDisplayName(resolver, rootDocumentUri);
        String title = safeFileName(folderDisplayName != null && !folderDisplayName.trim().isEmpty()
                ? folderDisplayName : exeTitle);

        String selectedDocumentId = null;
        try {
            if (exeUri.getAuthority() != null && exeUri.getAuthority().equals(treeUri.getAuthority())) {
                String candidate = DocumentsContract.getDocumentId(exeUri);
                if (candidate.startsWith(rootDocumentId + "/")) selectedDocumentId = candidate;
            }
        }
        catch (Exception ignored) {}

        File libraryRoot = new File(AppUtils.INTERNAL_STORAGE, "MR Games Library");
        if (!libraryRoot.isDirectory() && !libraryRoot.mkdirs()) {
            throw new IllegalStateException("Couldn't create the shared MR Games Library folder.");
        }

        // Stage the import separately. A failed copy is deleted instead of leaving
        // a half-imported game that can later launch to a black screen.
        File stagingDir = new File(libraryRoot, ".mr_games_import_" + System.currentTimeMillis());
        if (!stagingDir.mkdirs()) throw new IllegalStateException("Couldn't prepare the game import folder.");
        boolean committed = false;
        try {
            CopyState state = new CopyState(selectedDocumentId, exeName);
            copyDocumentChildren(resolver, treeUri, rootDocumentId, stagingDir, state, 0);

            File selectedExe = null;
            if (state.exactMatches.size() == 1) selectedExe = state.exactMatches.get(0);
            else if (state.exactMatches.size() > 1) {
                throw new IllegalArgumentException("The selected EXE appears more than once in the chosen folder.");
            }
            else if (state.nameMatches.size() == 1) selectedExe = state.nameMatches.get(0);
            else if (state.nameMatches.size() > 1) {
                throw new IllegalArgumentException("More than one file has this EXE name in the selected folder. Select the folder closer to the game executable.");
            }
            if (selectedExe == null || !selectedExe.isFile()) {
                throw new IllegalArgumentException("The selected folder does not contain the EXE you chose. Select the correct game folder.");
            }

            File importMarker = new File(stagingDir, ".mr_games_import_complete");
            if (!FileUtils.writeString(importMarker, "MR Games complete folder import v0.8.0\n")) {
                throw new IllegalStateException("Couldn't verify the completed game-folder import.");
            }
            String relativeExePath = selectedExe.getAbsolutePath().substring(stagingDir.getAbsolutePath().length() + 1);
            File destinationDir = createUniqueGameDirectory(libraryRoot, title);
            if (!stagingDir.renameTo(destinationDir)) {
                throw new IllegalStateException("Couldn't finish importing the game into MR Games Library.");
            }
            committed = true;
            File finalExe = new File(destinationDir, relativeExePath);
            if (!finalExe.isFile()) throw new IllegalStateException("The imported game EXE could not be found after copying.");
            FileUtils.chmod(finalExe, 0666);
            return new StagedGame(finalExe, destinationDir.getName(), true);
        }
        finally {
            if (!committed) deleteRecursively(stagingDir);
        }
    }

    private static File createUniqueGameDirectory(File libraryRoot, String title) {
        File candidate = new File(libraryRoot, title);
        int suffix = 2;
        while (candidate.exists()) candidate = new File(libraryRoot, title + " (" + suffix++ + ")");
        return candidate;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    private static void copyDocumentChildren(ContentResolver resolver, Uri treeUri, String parentDocumentId,
                                             File targetDir, CopyState state, int depth) throws Exception {
        if (depth > 32) throw new IllegalStateException("The selected game folder contains too many nested folder levels.");
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Android couldn't read the selected game folder.");
            int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                throw new IllegalStateException("The selected file provider returned incomplete folder information.");
            }

            while (cursor.moveToNext()) {
                String documentId = cursor.getString(idColumn);
                String displayName = cursor.getString(nameColumn);
                String mimeType = cursor.getString(mimeColumn);
                if (documentId == null || displayName == null || displayName.trim().isEmpty()) continue;
                String safeName = safeDocumentName(displayName);
                File destination = new File(targetDir, safeName);
                if (destination.exists()) {
                    throw new IllegalStateException("Two items in the selected folder have the same safe name: " + safeName + ". Rename one item and import again.");
                }

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    if (!destination.mkdirs()) {
                        throw new IllegalStateException("Couldn't create folder " + safeName);
                    }
                    copyDocumentChildren(resolver, treeUri, documentId, destination, state, depth + 1);
                }
                else {
                    state.files++;
                    if (state.files > MAX_IMPORT_FILES) throw new IllegalStateException("The selected folder contains too many files to import safely.");
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    try (InputStream input = resolver.openInputStream(documentUri)) {
                        if (input == null) throw new IllegalStateException("Android couldn't read " + displayName);
                        try (FileOutputStream output = new FileOutputStream(destination)) {
                            byte[] buffer = new byte[128 * 1024];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                state.bytes += count;
                                if (state.bytes > MAX_IMPORT_BYTES) throw new IllegalStateException("The selected game folder is larger than the 20 GB import limit.");
                                output.write(buffer, 0, count);
                            }
                            output.flush();
                        }
                    }
                    FileUtils.chmod(destination, 0666);
                    if (state.selectedDocumentId != null && state.selectedDocumentId.equals(documentId)) state.exactMatches.add(destination);
                    if (displayName.equalsIgnoreCase(state.selectedName)) state.nameMatches.add(destination);
                }
            }
        }
    }

    private static boolean isInside(String path, String root) {
        if (path == null || root == null || root.isEmpty()) return false;
        String normalizedRoot = root.endsWith(File.separator) ? root : root + File.separator;
        return path.equals(root) || path.startsWith(normalizedRoot);
    }

    private static void prepareRuntime(Activity activity, StagedGame game, ImportCallback callback) {
        MAIN.post(() -> {
            if (activity.isFinishing()) {
                postResult(callback, null, "The import screen was closed before the game could be added.", false);
                return;
            }
            try {
                ContainerManager manager = new ContainerManager(activity);
                Container runtime = findRuntime(manager.getContainers());
                if (runtime != null) {
                    createShortcut(activity, runtime, game, callback);
                    return;
                }

                JSONObject data = new JSONObject();
                data.put("name", RUNTIME_NAME);
                data.put("screenSize", MRGamePerformance.getDefaultScreenSize(activity));
                data.put("box64Preset", MRGamePerformance.getDefaultBox64Preset(activity));
                manager.createContainerAsync(data, container -> {
                    if (container == null) {
                        postResult(callback, null, "Couldn't create the MR Games runtime. Open Containers once and try again.", false);
                    }
                    else createShortcut(activity, container, game, callback);
                });
            }
            catch (Exception e) {
                postResult(callback, null, "Couldn't prepare the game runtime: " + safeMessage(e), false);
            }
        });
    }

    private static void createShortcut(Activity activity, Container container, StagedGame game, ImportCallback callback) {
        try {
            String dosPath = WineUtils.unixToDOSPath(game.exe.getAbsolutePath(), container);
            if (dosPath.isEmpty() || !dosPath.contains(":")) {
                postResult(callback, null, "The selected EXE is outside the runtime's shared drives.", false);
                return;
            }

            int profileId = ensurePlayStationProfile(activity, game.title);
            File desktopDir = new File(container.getUserDir(), "Desktop");
            if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) {
                postResult(callback, null, "Couldn't create the game shortcut folder.", false);
                return;
            }
            File shortcutFile = new File(desktopDir, safeFileName(game.title) + ".desktop");
            String content = "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=" + game.title.replace("\n", " ").replace("\r", " ") + "\n" +
                    "Exec=wine " + escapeShortcutExecPath(dosPath) + "\n" +
                    "Terminal=false\n" +
                    "\n[Extra Data]\n" +
                    "controlsProfile=" + profileId + "\n" +
                    "mrGamesShowFPS=1\n" +
                    "envVars=WINEDEBUG=+err\n";
            if (!FileUtils.writeString(shortcutFile, content)) throw new IllegalStateException("Could not save the game shortcut.");
            Shortcut shortcut = new Shortcut(container, shortcutFile);
            String message = game.importedFolder
                    ? "Game folder imported with its DLL, data and subfolders. PlayStation controls and the FPS overlay are enabled for this game."
                    : "Game added from a previously imported complete game folder. PlayStation controls and the FPS overlay are enabled.";
            postResult(callback, shortcut, message, false);
        }
        catch (Exception e) {
            postResult(callback, null, "Couldn't create the game shortcut: " + safeMessage(e), false);
        }
    }

    private static int ensurePlayStationProfile(Activity activity, String gameTitle) throws JSONException {
        InputControlsManager controlsManager = new InputControlsManager(activity);
        String profileName = PROFILE_PREFIX + gameTitle;
        ArrayList<ControlsProfile> profiles = controlsManager.getProfiles(false);
        for (ControlsProfile profile : profiles) {
            if (profileName.equals(profile.getName())) return profile.id;
        }

        ControlsProfile profile = controlsManager.createProfile(profileName);
        JSONObject data = new JSONObject();
        data.put("id", profile.id);
        data.put("name", profileName);
        data.put("cursorSpeed", 1.0);
        data.put("disableMouseInput", true);
        data.put("hideMouseCursor", true);
        JSONArray elements = new JSONArray();
        elements.put(element("D_PAD", "CIRCLE", 0.12, 0.67, 1.18, "", "GAMEPAD_DPAD_UP", "GAMEPAD_DPAD_RIGHT", "GAMEPAD_DPAD_DOWN", "GAMEPAD_DPAD_LEFT"));
        elements.put(element("STICK", "CIRCLE", 0.29, 0.82, 1.18, "", "GAMEPAD_LEFT_THUMB_UP", "GAMEPAD_LEFT_THUMB_RIGHT", "GAMEPAD_LEFT_THUMB_DOWN", "GAMEPAD_LEFT_THUMB_LEFT"));
        elements.put(element("STICK", "CIRCLE", 0.69, 0.82, 1.12, "", "GAMEPAD_RIGHT_THUMB_UP", "GAMEPAD_RIGHT_THUMB_RIGHT", "GAMEPAD_RIGHT_THUMB_DOWN", "GAMEPAD_RIGHT_THUMB_LEFT"));
        elements.put(element("BUTTON", "CIRCLE", 0.86, 0.64, 0.92, "□", "GAMEPAD_BUTTON_X"));
        elements.put(element("BUTTON", "CIRCLE", 0.93, 0.53, 0.92, "△", "GAMEPAD_BUTTON_Y"));
        elements.put(element("BUTTON", "CIRCLE", 0.93, 0.75, 0.92, "○", "GAMEPAD_BUTTON_B"));
        elements.put(element("BUTTON", "CIRCLE", 0.95, 0.64, 0.92, "✕", "GAMEPAD_BUTTON_A"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.10, 0.12, 0.84, "L1", "GAMEPAD_BUTTON_L1"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.24, 0.12, 0.84, "L2", "GAMEPAD_BUTTON_L2"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.76, 0.12, 0.84, "R2", "GAMEPAD_BUTTON_R2"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.90, 0.12, 0.84, "R1", "GAMEPAD_BUTTON_R1"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.45, 0.91, 0.72, "SELECT", "GAMEPAD_BUTTON_SELECT"));
        elements.put(element("BUTTON", "ROUND_RECT", 0.55, 0.91, 0.72, "START", "GAMEPAD_BUTTON_START"));
        elements.put(element("BUTTON", "CIRCLE", 0.36, 0.91, 0.72, "L3", "GAMEPAD_BUTTON_L3"));
        elements.put(element("BUTTON", "CIRCLE", 0.70, 0.91, 0.72, "R3", "GAMEPAD_BUTTON_R3"));

        data.put("elements", elements);
        if (!FileUtils.writeString(ControlsProfile.getProfileFile(activity, profile.id), data.toString())) {
            throw new IllegalStateException("Could not save the game controller profile.");
        }
        return profile.id;
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
        intent.putExtra("mr_games_session", true);
        activity.startActivity(intent);
    }

    private static Container findRuntime(ArrayList<Container> containers) {
        for (Container container : containers) if (RUNTIME_NAME.equals(container.getName())) return container;
        return null;
    }

    private static String getDisplayName(ContentResolver resolver, Uri uri) {
        String displayName = queryDisplayName(resolver, uri);
        if (displayName != null && !displayName.trim().isEmpty()) return displayName;
        String last = uri.getLastPathSegment();
        return last != null ? last : "WindowsGame.exe";
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
                index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        }
        catch (Exception ignored) {}
        return null;
    }

    private static String escapeShortcutExecPath(String path) {
        return path.replace("\\", "\\\\\\\\").replace(" ", "\\\\ ");
    }

    private static String safeFileName(String value) {
        String safe = StringUtils.clearReservedChars(value).replaceAll("[\\r\\n]+", " ").trim();
        safe = safe.replaceAll("\\s+", " ");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "Windows Game";
        return safe.length() > 72 ? safe.substring(0, 72).trim() : safe;
    }

    private static String safeDocumentName(String value) {
        String safe = value == null ? "" : value.replaceAll("[\\\\/\\r\\n<>:\"|?*]", "_").trim();
        safe = safe.replaceAll("[. ]+$", "");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) return "unnamed_file";
        return safe;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message != null && !message.trim().isEmpty() ? message : error.getClass().getSimpleName();
    }

    private static void postResult(ImportCallback callback, Shortcut shortcut, String message, boolean needsFolder) {
        MAIN.post(() -> {
            if (callback != null) callback.onFinished(shortcut, message, needsFolder);
        });
    }
}
