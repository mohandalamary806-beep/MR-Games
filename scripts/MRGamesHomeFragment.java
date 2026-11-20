package com.winlator;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

/** MR Games front page. Winlator's native library, containers and desktop remain the runtime backend. */
public class MRGamesHomeFragment extends Fragment {
    private static final int BG = Color.rgb(7, 13, 22);
    private static final int CARD = Color.rgb(15, 29, 45);
    private static final int CARD_ALT = Color.rgb(18, 40, 62);
    private static final int BLUE = Color.rgb(0, 166, 245);
    private static final int CYAN = Color.rgb(48, 211, 255);
    private static final int WHITE = Color.rgb(238, 247, 255);
    private static final int MUTED = Color.rgb(143, 169, 192);
    private static final int REQUEST_ADD_GAME_EXE = 5104;

    private final String[] profileIds = {"AUTO", "LOW", "BALANCED", "QUALITY", "STABILITY"};
    private final String[] profileNames = {"Auto • Recommended", "Low-End • Best effort performance", "Balanced", "High Quality", "Stability"};
    private final String[] resolutionIds = {"AUTO", "640x360", "854x480", "960x544", "1280x720", "1920x1080"};
    private final String[] resolutionNames = {"Auto by device", "640 × 360", "854 × 480", "960 × 544", "1280 × 720", "1920 × 1080"};
    private Spinner profileSpinner;
    private Spinner resolutionSpinner;
    private TextView recommendationText;

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
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(context, 16), dp(context, 18), dp(context, 16), dp(context, 28));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(buildHeader(context));
        page.addView(buildHero(context), margin(context, 0, 16, 0, 14));
        page.addView(buildDeviceCard(context), margin(context, 0, 0, 0, 14));
        page.addView(buildPerformanceCard(context), margin(context, 0, 0, 0, 14));
        page.addView(buildLibraryCard(context), margin(context, 0, 0, 0, 14));
        page.addView(buildDesktopCard(context), margin(context, 0, 0, 0, 14));
        page.addView(buildPerGameNote(context));
        TextView footer = text(context, "MR Games • Windows compatibility powered by Wine / Box64", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, margin(context, 0, 18, 0, 0));
        return scroll;
    }

    private View buildHeader(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.mipmap.ic_launcher);
        row.addView(logo, new LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(context, "MR GAMES", 22, WHITE, true);
        TextView tag = text(context, "WINDOWS GAMING HUB", 10, CYAN, true);
        labels.addView(name);
        labels.addView(tag);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(context, 12);
        row.addView(labels, labelParams);
        Button settings = smallButton(context, "⚙", false);
        settings.setOnClickListener(v -> openSettings());
        row.addView(settings, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 44)));
        return row;
    }

    private View buildHero(Context context) {
        MaterialCardView card = card(context, CARD_ALT, CYAN);
        LinearLayout content = column(context, 16);
        TextView eyebrow = text(context, "YOUR PC GAMES • ON ANDROID", 10, CYAN, true);
        TextView title = text(context, "Play your Windows games", 22, WHITE, true);
        TextView subtitle = text(context, "One library for your games, with a separate Windows desktop for installers, files and mods.", 13, MUTED, false);
        content.addView(eyebrow);
        content.addView(title);
        content.addView(subtitle);
        Button addGame = primaryButton(context, "+  ADD GAME (.EXE)");
        addGame.setOnClickListener(v -> chooseGameExe());
        content.addView(addGame);
        Button open = smallButton(context, "OPEN GAME LIBRARY  →", false);
        open.setOnClickListener(v -> openLibrary());
        content.addView(open);
        card.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View buildDeviceCard(Context context) {
        MaterialCardView card = card(context, CARD, BLUE);
        LinearLayout content = column(context, 8);
        content.addView(sectionTitle(context, "DEVICE CHECK", "Your phone • Detected locally"));
        TextView details = text(context, MRGamePerformance.getDeviceSummary(context), 12, MUTED, false);
        content.addView(details);
        recommendationText = text(context, "Device recommendation: " + MRGamePerformance.getRecommendedProfileLabel(context) + "  •  starting point only", 12, CYAN, true);
        content.addView(recommendationText);
        card.addView(content);
        return card;
    }

    private View buildPerformanceCard(Context context) {
        MaterialCardView card = card(context, CARD, BLUE);
        LinearLayout content = column(context, 10);
        content.addView(sectionTitle(context, "PERFORMANCE", "Choose defaults for new game containers"));
        content.addView(text(context, "Device profile", 12, MUTED, true));
        profileSpinner = makeSpinner(context, profileNames);
        content.addView(profileSpinner);
        content.addView(text(context, "Default resolution", 12, MUTED, true));
        resolutionSpinner = makeSpinner(context, resolutionNames);
        content.addView(resolutionSpinner);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        selectValue(profileSpinner, prefs.getString("mr_games_profile", "AUTO"), profileIds);
        selectValue(resolutionSpinner, prefs.getString("mr_games_resolution", "AUTO"), resolutionIds);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button auto = smallButton(context, "AUTO DETECT", false);
        auto.setOnClickListener(v -> {
            selectValue(profileSpinner, "AUTO", profileIds);
            selectValue(resolutionSpinner, "AUTO", resolutionIds);
            savePerformance(context);
        });
        Button save = primaryButton(context, "SAVE DEFAULTS");
        save.setOnClickListener(v -> savePerformance(context));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(context, 46), 1f);
        half.rightMargin = dp(context, 6);
        actions.addView(auto, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(context, 46), 1f);
        half2.leftMargin = dp(context, 6);
        actions.addView(save, half2);
        content.addView(actions);
        content.addView(text(context, "These choices become defaults for new containers. Existing games keep their own settings.", 11, MUTED, false));
        card.addView(content);
        return card;
    }

    private View buildLibraryCard(Context context) {
        MaterialCardView card = card(context, CARD, BLUE);
        LinearLayout content = column(context, 10);
        content.addView(sectionTitle(context, "MY GAMES", "Your installed Windows shortcuts"));
        content.addView(text(context, "Open your game shortcuts and launch supported EXE games from the shared Wine / Box64 engine.", 12, MUTED, false));
        Button library = primaryButton(context, "OPEN GAME LIBRARY  →");
        library.setOnClickListener(v -> openLibrary());
        content.addView(library);
        Button add = primaryButton(context, "+  ADD GAME (.EXE)");
        add.setOnClickListener(v -> chooseGameExe());
        content.addView(add);
        card.addView(content);
        return card;
    }

    private View buildDesktopCard(Context context) {
        MaterialCardView card = card(context, CARD, CYAN);
        LinearLayout content = column(context, 10);
        content.addView(sectionTitle(context, "WINDOWS DESKTOP & MOD WORKSHOP", "Keep the Windows environment for modding"));
        content.addView(text(context, "Open a container to use the Windows desktop. Use its file manager to browse game folders, install tools, copy mods and back up files.", 12, MUTED, false));
        Button desktop = primaryButton(context, "OPEN WINDOWS DESKTOP  →");
        desktop.setOnClickListener(v -> openContainers());
        content.addView(desktop);
        card.addView(content);
        return card;
    }

    private View buildPerGameNote(Context context) {
        MaterialCardView card = card(context, Color.rgb(10, 24, 38), Color.rgb(35, 89, 128));
        LinearLayout content = column(context, 6);
        content.addView(text(context, "PER-GAME SETTINGS", 13, CYAN, true));
        content.addView(text(context, "In Game Library, tap a game's ⋮ menu → Settings to set its own resolution, Box64 profile, graphics driver, DirectX wrapper and controls. Those overrides are stored for that game.", 11, MUTED, false));
        card.addView(content);
        return card;
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
        recommendationText.setText("Active defaults: " + MRGamePerformance.getProfileLabel(context) + "  •  " + MRGamePerformance.getDefaultScreenSize(context));
        Toast.makeText(context, "MR Games defaults saved for new containers", Toast.LENGTH_LONG).show();
    }

    private void chooseGameExe() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "Choose a Windows game EXE");
        try {
            startActivityForResult(intent, REQUEST_ADD_GAME_EXE);
        }
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
        progress.setMessage("Adding your game to MR Games...");
        progress.show();

        MRGameLibrary.importGame(requireActivity(), uri, (shortcut, message) -> {
            if (progress.isShowing()) progress.dismiss();
            if (!isAdded()) return;
            if (shortcut == null) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Couldn't add game")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Game added")
                    .setMessage(message)
                    .setNegativeButton("GAME LIBRARY", (dialog, which) -> openLibrary())
                    .setPositiveButton("PLAY NOW", (dialog, which) -> MRGameLibrary.launchShortcut(requireActivity(), shortcut))
                    .show();
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

    private MaterialCardView card(Context context, int color, int strokeColor) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(color);
        card.setRadius(dp(context, 20));
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(strokeColor);
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(context, 2));
        return card;
    }

    private LinearLayout column(Context context, int spacingDp) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(Color.TRANSPARENT);
        divider.setSize(1, dp(context, spacingDp));
        layout.setDividerDrawable(divider);
        layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return layout;
    }

    private LinearLayout sectionTitle(Context context, String title, String subtitle) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(context, title, 15, WHITE, true);
        TextView subtitleView = text(context, subtitle, 11, MUTED, false);
        block.addView(titleView);
        block.addView(subtitleView);
        return block;
    }

    private TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
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
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(0, 109, 184), BLUE, Color.rgb(13, 190, 255)});
        bg.setCornerRadius(dp(context, 14));
        button.setBackground(bg);
        return button;
    }

    private Button smallButton(Context context, String label, boolean filled) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(filled ? Color.WHITE : CYAN);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(filled ? BLUE : Color.rgb(13, 36, 55));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), Color.rgb(26, 119, 169));
        button.setBackground(bg);
        return button;
    }

    private Spinner makeSpinner(Context context, String[] values) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void selectValue(Spinner spinner, String value, String[] ids) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
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
