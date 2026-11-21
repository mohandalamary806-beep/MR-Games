package com.winlator;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MR Games landscape launcher. The runtime remains the native Winlator engine;
 * this screen supplies a console-style horizontal library with best-effort
 * Steam capsule artwork for shortcuts whose names match Steam catalogue entries.
 */
public class MRGamesHomeFragment extends Fragment {
    private static final int BG = Color.rgb(4, 9, 18);
    private static final int GLASS = Color.argb(224, 13, 25, 42);
    private static final int GLASS_ALT = Color.argb(232, 11, 34, 57);
    private static final int BLUE = Color.rgb(0, 147, 240);
    private static final int CYAN = Color.rgb(34, 205, 255);
    private static final int WHITE = Color.rgb(239, 248, 255);
    private static final int MUTED = Color.rgb(142, 170, 197);
    private static final int REQUEST_ADD_GAME_EXE = 5104;
    private static final ExecutorService ARTWORK_IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final String[] profileIds = {"AUTO", "LOW", "BALANCED", "QUALITY", "STABILITY"};
    private final String[] profileNames = {"Auto", "Low-End", "Balanced", "High Quality", "Stability"};
    private final String[] resolutionIds = {"AUTO", "640x360", "854x480", "960x544", "1280x720", "1920x1080"};
    private final String[] resolutionNames = {"Auto", "640 × 360", "854 × 480", "960 × 544", "1280 × 720", "1920 × 1080"};
    private Spinner profileSpinner;
    private Spinner resolutionSpinner;
    private TextView recommendationText;
    private LinearLayout gamesShelf;
    private TextView gameCountText;

    private static final class GameItem {
        final Shortcut shortcut;
        final String title;
        GameItem(Shortcut shortcut, String title) {
            this.shortcut = shortcut;
            this.title = title;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity)getActivity();
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().setTitle("MR Games");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(context, 22), dp(context, 14), dp(context, 22), dp(context, 12));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(buildHeader(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = dp(context, 14);
        page.addView(content, contentParams);

        MaterialCardView libraryPanel = glassCard(context, GLASS_ALT, CYAN);
        LinearLayout libraryContent = column(context, 10);
        libraryContent.addView(buildLibraryHeading(context));
        gamesShelf = new LinearLayout(context);
        gamesShelf.setOrientation(LinearLayout.HORIZONTAL);
        gamesShelf.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView horizontalGames = new HorizontalScrollView(context);
        horizontalGames.setHorizontalScrollBarEnabled(false);
        horizontalGames.setFillViewport(false);
        horizontalGames.addView(gamesShelf, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        libraryContent.addView(horizontalGames, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 214)));
        TextView note = text(context, "Tap a cover to launch • Hold the original game folder in Download or MR Games shared storage", 10, MUTED, false);
        libraryContent.addView(note);
        libraryPanel.addView(libraryContent);
        LinearLayout.LayoutParams libraryParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.15f);
        libraryParams.rightMargin = dp(context, 12);
        content.addView(libraryPanel, libraryParams);

        LinearLayout rightColumn = new LinearLayout(context);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rightColumn.addView(buildDeviceCard(context), margin(context, 0, 0, 0, 10));
        rightColumn.addView(buildPerformanceCard(context));
        content.addView(rightColumn);

        page.addView(buildNavigationBar(context), margin(context, 0, 12, 0, 0));
        TextView footer = text(context, "MR GAMES  •  WINE + BOX64 COMPATIBILITY", 9, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, margin(context, 0, 8, 0, 0));

        populateGames(context);
        return scroll;
    }

    private View buildHeader(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(logo, new LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(context, 12), 0, 0, 0);
        labels.addView(text(context, "MR GAMES", 23, WHITE, true));
        labels.addView(text(context, "YOUR WINDOWS GAME DECK  •  GLASS EDITION", 10, CYAN, true));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = primaryButton(context, "+  ADD GAME");
        add.setOnClickListener(v -> chooseGameExe());
        row.addView(add, new LinearLayout.LayoutParams(dp(context, 142), dp(context, 46)));
        Button settings = outlineButton(context, "⚙");
        settings.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(context, 48), dp(context, 46));
        settingsParams.leftMargin = dp(context, 8);
        row.addView(settings, settingsParams);
        return row;
    }

    private View buildLibraryHeading(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(context, "MY GAMES", 17, WHITE, true));
        labels.addView(text(context, "Your installed PC games", 10, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        gameCountText = text(context, "0 GAMES", 10, CYAN, true);
        row.addView(gameCountText);
        return row;
    }

    private View buildDeviceCard(Context context) {
        MaterialCardView card = glassCard(context, GLASS, BLUE);
        LinearLayout content = column(context, 5);
        content.addView(text(context, "DEVICE STATUS", 12, CYAN, true));
        TextView details = text(context, MRGamePerformance.getDeviceSummary(context), 10, MUTED, false);
        details.setMaxLines(4);
        details.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(details);
        recommendationText = text(context, "Recommended: " + MRGamePerformance.getRecommendedProfileLabel(context), 10, WHITE, true);
        content.addView(recommendationText);
        card.addView(content);
        return card;
    }

    private View buildPerformanceCard(Context context) {
        MaterialCardView card = glassCard(context, GLASS, BLUE);
        LinearLayout content = column(context, 7);
        content.addView(text(context, "PERFORMANCE", 13, CYAN, true));
        content.addView(text(context, "Default profile", 10, MUTED, true));
        profileSpinner = makeSpinner(context, profileNames);
        content.addView(profileSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)));
        content.addView(text(context, "Resolution", 10, MUTED, true));
        resolutionSpinner = makeSpinner(context, resolutionNames);
        content.addView(resolutionSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        selectValue(profileSpinner, prefs.getString("mr_games_profile", "AUTO"), profileIds);
        selectValue(resolutionSpinner, prefs.getString("mr_games_resolution", "AUTO"), resolutionIds);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button auto = outlineButton(context, "AUTO");
        auto.setTextSize(10);
        auto.setOnClickListener(v -> {
            selectValue(profileSpinner, "AUTO", profileIds);
            selectValue(resolutionSpinner, "AUTO", resolutionIds);
            savePerformance(context);
        });
        Button save = primaryButton(context, "SAVE");
        save.setTextSize(10);
        save.setOnClickListener(v -> savePerformance(context));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(context, 38), 1f);
        half.rightMargin = dp(context, 4);
        actions.addView(auto, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(context, 38), 1f);
        half2.leftMargin = dp(context, 4);
        actions.addView(save, half2);
        content.addView(actions);
        card.addView(content);
        return card;
    }

    private View buildNavigationBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5));
        GradientDrawable background = rounded(Color.argb(210, 7, 18, 31), Color.argb(130, 0, 150, 220), 20, context);
        bar.setBackground(background);
        bar.addView(navItem(context, "⌂", "HOME", true, v -> {}), new LinearLayout.LayoutParams(0, dp(context, 42), 1f));
        bar.addView(navItem(context, "▦", "LIBRARY", false, v -> openLibrary()), new LinearLayout.LayoutParams(0, dp(context, 42), 1f));
        bar.addView(navItem(context, "⚙", "SETTINGS", false, v -> openSettings()), new LinearLayout.LayoutParams(0, dp(context, 42), 1f));
        bar.addView(navItem(context, "▣", "DESKTOP", false, v -> openContainers()), new LinearLayout.LayoutParams(0, dp(context, 42), 1f));
        return bar;
    }

    private View navItem(Context context, String icon, String label, boolean selected, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        TextView mark = text(context, icon, 20, selected ? CYAN : MUTED, true);
        item.addView(mark);
        TextView name = text(context, label, 10, selected ? CYAN : WHITE, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dp(context, 6);
        item.addView(name, nameParams);
        item.setOnClickListener(listener);
        return item;
    }

    private void populateGames(Context context) {
        if (gamesShelf == null) return;
        gamesShelf.removeAllViews();
        ArrayList<GameItem> games = readGameShortcuts(context);
        if (gameCountText != null) gameCountText.setText(games.size() + (games.size() == 1 ? " GAME" : " GAMES"));
        if (games.isEmpty()) {
            MaterialCardView empty = glassCard(context, Color.argb(130, 8, 26, 44), Color.rgb(39, 103, 145));
            empty.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 230), dp(context, 182)));
            LinearLayout content = column(context, 8);
            TextView plus = text(context, "+", 40, CYAN, true);
            plus.setGravity(Gravity.CENTER);
            content.addView(plus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 70)));
            TextView title = text(context, "YOUR LIBRARY IS EMPTY", 12, WHITE, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title);
            Button add = primaryButton(context, "ADD YOUR FIRST GAME");
            add.setOnClickListener(v -> chooseGameExe());
            content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 38)));
            empty.addView(content);
            gamesShelf.addView(empty);
            return;
        }

        for (GameItem game : games) {
            gamesShelf.addView(buildGameTile(context, game), new LinearLayout.LayoutParams(dp(context, 218), dp(context, 184)));
        }
    }

    private ArrayList<GameItem> readGameShortcuts(Context context) {
        ArrayList<GameItem> result = new ArrayList<>();
        try {
            ContainerManager manager = new ContainerManager(context);
            for (Container container : manager.getContainers()) {
                File desktop = new File(container.getUserDir(), "Desktop");
                File[] files = desktop.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".desktop"));
                if (files == null) continue;
                for (File file : files) {
                    String name = readDesktopName(file);
                    if (name == null || name.trim().isEmpty()) name = file.getName().replaceFirst("(?i)\\.desktop$", "");
                    if (!name.trim().isEmpty()) {
                        result.add(new GameItem(new Shortcut(container, file), name.trim()));
                    }
                }
            }
        }
        catch (Exception ignored) {}
        Collections.sort(result, new Comparator<GameItem>() {
            @Override public int compare(GameItem first, GameItem second) {
                return first.title.compareToIgnoreCase(second.title);
            }
        });
        return result;
    }

    private String readDesktopName(File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Name=")) return line.substring(5).trim();
            }
        }
        catch (Exception ignored) {}
        return null;
    }

    private View buildGameTile(Context context, GameItem game) {
        MaterialCardView card = glassCard(context, Color.argb(220, 8, 21, 36), Color.argb(190, 31, 137, 194));
        card.setRadius(dp(context, 16));
        card.setCardElevation(dp(context, 4));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);

        FrameLayout artworkFrame = new FrameLayout(context);
        GradientDrawable placeholder = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accentFor(game.title), Color.rgb(4, 16, 31), Color.rgb(5, 36, 59)});
        ImageView artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(placeholder);
        artwork.setContentDescription(game.title + " cover art");
        artworkFrame.addView(artwork, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView fallback = text(context, initials(game.title), 28, Color.WHITE, true);
        fallback.setGravity(Gravity.CENTER);
        artworkFrame.addView(fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(artworkFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 128)));
        loadSteamArtwork(context, game.title, artwork, fallback);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        TextView title = text(context, game.title, 12, WHITE, true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView play = text(context, "▶ PLAY", 10, CYAN, true);
        meta.addView(play);
        body.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)));
        card.addView(body);
        card.setOnClickListener(v -> MRGameLibrary.launchShortcut(requireActivity(), game.shortcut));
        return card;
    }

    private void loadSteamArtwork(Context context, String title, ImageView image, TextView fallback) {
        File cacheDir = new File(context.getCacheDir(), "mr_game_covers");
        String cacheName = Integer.toHexString(title.toLowerCase(Locale.ENGLISH).hashCode()) + ".jpg";
        File cached = new File(cacheDir, cacheName);
        image.setTag(title);
        if (cached.isFile() && cached.length() > 1024) {
            Bitmap bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                fallback.setVisibility(View.GONE);
                return;
            }
        }

        ARTWORK_IO.execute(() -> {
            Bitmap bitmap = null;
            try {
                String query = URLEncoder.encode(title, "UTF-8");
                URL searchUrl = new URL("https://store.steampowered.com/api/storesearch/?term=" + query + "&l=english&cc=us");
                HttpURLConnection search = (HttpURLConnection)searchUrl.openConnection();
                search.setConnectTimeout(4500);
                search.setReadTimeout(4500);
                search.setRequestProperty("User-Agent", "MR-Games-Android/1.0");
                int id = 0;
                try (InputStream stream = new BufferedInputStream(search.getInputStream())) {
                    byte[] bytes = readAll(stream);
                    JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    JSONArray items = json.optJSONArray("items");
                    if (items != null && items.length() > 0) id = items.getJSONObject(0).optInt("id", 0);
                }
                finally { search.disconnect(); }
                if (id > 0) {
                    URL coverUrl = new URL("https://cdn.cloudflare.steamstatic.com/steam/apps/" + id + "/header.jpg");
                    HttpURLConnection cover = (HttpURLConnection)coverUrl.openConnection();
                    cover.setConnectTimeout(4500);
                    cover.setReadTimeout(4500);
                    cover.setRequestProperty("User-Agent", "MR-Games-Android/1.0");
                    try (InputStream stream = new BufferedInputStream(cover.getInputStream())) {
                        bitmap = BitmapFactory.decodeStream(stream);
                    }
                    finally { cover.disconnect(); }
                }
                if (bitmap != null) {
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    try (FileOutputStream output = new FileOutputStream(cached)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
                    }
                }
            }
            catch (Exception ignored) {}

            final Bitmap result = bitmap;
            if (result != null) MAIN.post(() -> {
                if (title.equals(image.getTag())) {
                    image.setImageBitmap(result);
                    fallback.setVisibility(View.GONE);
                }
            });
        });
    }

    private byte[] readAll(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private int accentFor(String title) {
        int[] accents = {Color.rgb(0, 116, 190), Color.rgb(31, 79, 148), Color.rgb(0, 132, 142), Color.rgb(73, 59, 151), Color.rgb(25, 93, 127)};
        return accents[Math.floorMod(title.hashCode(), accents.length)];
    }

    private String initials(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) return "MR";
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ENGLISH);
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ENGLISH);
    }

    private void savePerformance(Context context) {
        int p = profileSpinner.getSelectedItemPosition();
        int r = resolutionSpinner.getSelectedItemPosition();
        String profile = profileIds[Math.max(0, Math.min(p, profileIds.length - 1))];
        String resolution = resolutionIds[Math.max(0, Math.min(r, resolutionIds.length - 1))];
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("mr_games_profile", profile)
                .putString("mr_games_resolution", resolution)
                .apply();
        recommendationText.setText("Saved: " + MRGamePerformance.getProfileLabel(context));
        Toast.makeText(context, "MR Games defaults saved", Toast.LENGTH_SHORT).show();
    }

    private void chooseGameExe() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "Choose a Windows game EXE");
        try { startActivityForResult(intent, REQUEST_ADD_GAME_EXE); }
        catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "No file picker is available on this device", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ADD_GAME_EXE || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        ProgressDialog progress = new ProgressDialog(requireContext());
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.setMessage("Adding game to your library...");
        progress.show();

        MRGameLibrary.importGame(requireActivity(), uri, (shortcut, message) -> {
            if (progress.isShowing()) progress.dismiss();
            if (!isAdded()) return;
            if (shortcut == null) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Couldn't add game").setMessage(message).setPositiveButton("OK", null).show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Game added")
                    .setMessage(message)
                    .setNegativeButton("LIBRARY", (dialog, which) -> openLibrary())
                    .setNeutralButton("ADD MORE", (dialog, which) -> chooseGameExe())
                    .setPositiveButton("PLAY NOW", (dialog, which) -> MRGameLibrary.launchShortcut(requireActivity(), shortcut))
                    .show();
            MAIN.postDelayed(() -> { if (isAdded()) populateGames(requireContext()); }, 350);
        });
    }

    private void openLibrary() {
        if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showFragment(new ShortcutsFragment());
    }
    private void openContainers() {
        if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showFragment(new ContainersFragment());
    }
    private void openSettings() {
        if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showFragment(new SettingsFragment());
    }

    private MaterialCardView glassCard(Context context, int color, int strokeColor) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(color);
        card.setRadius(dp(context, 22));
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(strokeColor);
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(context, 3));
        card.setPreventCornerOverlap(true);
        return card;
    }

    private LinearLayout column(Context context, int spacingDp) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
        return layout;
    }

    private TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button primaryButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(0, 91, 166), BLUE, Color.rgb(0, 190, 248)});
        bg.setCornerRadius(dp(context, 15));
        bg.setStroke(dp(context, 1), Color.argb(190, 80, 220, 255));
        button.setBackground(bg);
        return button;
    }

    private Button outlineButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(CYAN);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(Color.argb(155, 7, 24, 42), Color.argb(185, 30, 149, 197), 15, context));
        return button;
    }

    private GradientDrawable rounded(int color, int strokeColor, int radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    private Spinner makeSpinner(Context context, String[] values) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void selectValue(Spinner spinner, String value, String[] ids) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(value)) { spinner.setSelection(i); return; }
        spinner.setSelection(0);
    }

    private LinearLayout.LayoutParams margin(Context context, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    private int dp(Context context, float value) {
        return (int)(value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
