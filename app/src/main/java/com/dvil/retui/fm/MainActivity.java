package com.dvil.retui.fm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    public static final String ACTION_OPEN_CONSOLE = "com.dvil.retui.fm.OPEN_CONSOLE";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_THEME_BG = "theme_bg";
    public static final String EXTRA_THEME_TEXT = "theme_text";
    public static final String EXTRA_THEME_BORDER = "theme_border";
    public static final String EXTRA_TERMINAL_BG = "terminal_bg";
    public static final String EXTRA_MODULE_BG_COLOR = "module_bg_color";
    public static final String EXTRA_MODULE_TEXT_COLOR = "module_text_color";
    public static final String EXTRA_MODULE_BORDER_COLOR = "module_border_color";
    public static final String EXTRA_MODULE_HEADER_BG_COLOR = "module_header_bg_color";
    public static final String EXTRA_MODULE_HEADER_TEXT_COLOR = "module_header_text_color";
    public static final String EXTRA_MODULE_BUTTON_BG_COLOR = "module_button_bg_color";
    public static final String EXTRA_MODULE_BUTTON_TEXT_COLOR = "module_button_text_color";
    public static final String EXTRA_MODULE_BUTTON_BORDER_COLOR = "module_button_border_color";
    public static final String EXTRA_INPUT_BG_COLOR = "input_bg_color";
    public static final String EXTRA_INPUT_TEXT_COLOR = "input_text_color";
    public static final String EXTRA_OUTPUT_BG_COLOR = "output_bg_color";
    public static final String EXTRA_OUTPUT_TEXT_COLOR = "output_text_color";
    public static final String EXTRA_OUTPUT_BORDER_COLOR = "output_border_color";
    public static final String EXTRA_TOP_MARGIN = "top_margin";
    public static final String EXTRA_INPUT_FONT_SIZE = "input_font_size";
    public static final String EXTRA_DISPLAY_MARGIN_MM = "display_margin_mm";
    public static final String EXTRA_FONT_PATH = "font_path";
    public static final String EXTRA_FONT_NAME = "font_name";
    public static final String EXTRA_MODULE_CORNER_RADIUS = "module_corner_radius";
    public static final String EXTRA_OUTPUT_CORNER_RADIUS = "output_corner_radius";
    public static final String EXTRA_HEADER_CORNER_RADIUS = "header_corner_radius";
    public static final String EXTRA_HEADER_TEXT_SIZE = "header_text_size";
    public static final String EXTRA_OUTPUT_TEXT_SIZE = "output_text_size";
    public static final String EXTRA_MODULE_HEADER_TEXT_SIZE = "module_header_text_size";
    public static final String EXTRA_OUTPUT_HEADER_TEXT_SIZE = "output_header_text_size";
    public static final String EXTRA_TERMINAL_BG_IMAGE = "terminal_bg_image";

    private static final int LIST_TREE_DEPTH = 0;
    private static final int EXPANDED_TREE_DEPTH = 2;
    private static final int TREE_MAX_ITEMS = 320;
    private static final String ICON_FOLDER = "\uea83";
    private static final String ICON_FOLDER_OPEN = "\ueaf7";
    private static final String ICON_IMAGE = "\uf1c5";
    private static final String ICON_DOCUMENT = "\udb82\uddee";
    private static final String ICON_CODE = "\udb81\uddc0";
    private static final String ICON_FILE = "\uf15b";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final String PREFS_NAME = "retui_fm";
    private static final String PREF_FAVORITES = "favorites";

    private int bgColor = Color.rgb(38, 40, 40);
    private int panelColor = Color.rgb(48, 50, 50);
    private int textColor = Color.rgb(195, 139, 150);
    private int borderColor = Color.rgb(103, 64, 71);
    private int modulePanelColor = panelColor;
    private int moduleTextColor = textColor;
    private int moduleBorderColor = borderColor;
    private int headerPanelColor = panelColor;
    private int headerTextColor = textColor;
    private int moduleButtonBgColor = Color.rgb(103, 64, 83);
    private int moduleButtonTextColor = textColor;
    private int moduleButtonBorderColor = borderColor;
    private int inputBgColor = Color.TRANSPARENT;
    private int inputTextColor = textColor;
    private int outputPanelColor = panelColor;
    private int outputTextColor = textColor;
    private int outputBorderColor = borderColor;
    private int topMarginDp = 18;
    private int inputFontSizeSp = 14;
    private int headerTextSizeSp = 14;
    private int outputTextSizeSp = 13;
    private int outputHeaderTextSizeSp = 14;
    private int moduleCornerRadiusDp = 0;
    private int outputCornerRadiusDp = 0;
    private int headerCornerRadiusDp = 0;
    private int[] displayMarginsMm = new int[]{0, 0, 0, 0};
    private String terminalBackgroundImage;
    private Typeface appTypeface = Typeface.MONOSPACE;
    private Typeface iconTypeface = Typeface.MONOSPACE;

    private File currentDirectory;
    private FrameLayout stage;
    private RelativeLayout root;
    private FrameLayout.LayoutParams rootLayoutParams;
    private LinearLayout contentFrame;
    private TextView titleView;
    private TextView closeView;
    private LinearLayout bottomDock;
    private LinearLayout inputGroup;
    private LinearLayout toolsView;
    private TextView pathView;
    private TextView outputView;
    private LinearLayout outputContainer;
    private LinearLayout fileRowsView;
    private FrameLayout alphaRailHost;
    private ScrollView alphaRailScroll;
    private LinearLayout alphaRail;
    private SideRailView alphaRailView;
    private FrameLayout pinnedRailHost;
    private ScrollView pinnedRailScroll;
    private LinearLayout pinnedRail;
    private SideRailView pinnedRailView;
    private TextView inputPrefixView;
    private EditText inputView;
    private HorizontalScrollView suggestionsScroll;
    private LinearLayout suggestionsGroup;
    private ScrollView outputScroll;
    private final ArrayList<String> history = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private TreeOptions activeTreeOptions = TreeOptions.defaultListing();
    private int historyIndex = -1;
    private OperationOverlay operationOverlay;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean preserveInputAfterCommand;
    private final ArrayList<String> visibleSections = new ArrayList<>();
    private final ArrayList<Integer> visibleSectionRows = new ArrayList<>();
    private String selectedSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        applyIntentTheme(getIntent());
        currentDirectory = resolveStartDirectory(getIntent());
        buildUi();
        ensureStorageAccess();
        renderListing();
        handleIncomingCommand(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntentTheme(intent);
        currentDirectory = resolveStartDirectory(intent);
        applyStagePadding();
        applyWindowMargins();
        styleUi();
        renderListing();
        handleIncomingCommand(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentDirectory != null && outputView != null) {
            renderListing();
        }
    }

    private void buildUi() {
        stage = new FrameLayout(this);
        applyStagePadding();
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        applyWallpaperBackground();
        setContentView(stage);

        root = new RelativeLayout(this);
        root.setPadding(dp(14), dp(30), dp(14), dp(14));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        rootLayoutParams = new FrameLayout.LayoutParams(-1, -1);
        rootLayoutParams.setMargins(0, dp(topMarginDp), 0, dp(2));
        stage.addView(root, rootLayoutParams);

        bottomDock = new LinearLayout(this);
        bottomDock.setId(View.generateViewId());
        bottomDock.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams dockParams = new RelativeLayout.LayoutParams(-1, -2);
        dockParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        root.addView(bottomDock, dockParams);

        contentFrame = new LinearLayout(this);
        contentFrame.setId(View.generateViewId());
        contentFrame.setOrientation(LinearLayout.VERTICAL);
        contentFrame.setPadding(0, 0, 0, 0);
        RelativeLayout.LayoutParams contentParams = new RelativeLayout.LayoutParams(-1, -1);
        contentParams.addRule(RelativeLayout.ABOVE, bottomDock.getId());
        root.addView(contentFrame, contentParams);

        titleView = label("FILES", headerTextSizeSp, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMinWidth(dp(160));
        titleView.setPadding(dp(12), dp(2), dp(12), dp(2));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, -2);
        titleParams.leftMargin = dp(44);
        titleParams.topMargin = dp(8);
        stage.addView(titleView, titleParams);

        closeView = label("X", 15, true);
        closeView.setGravity(Gravity.CENTER);
        closeView.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(36), Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(8);
        stage.addView(closeView, closeParams);

        pathView = label("", outputHeaderTextSizeSp, true);
        pathView.setSingleLine(true);
        pathView.setPadding(dp(34), 0, dp(34), 0);
        contentFrame.addView(pathView, new LinearLayout.LayoutParams(-1, -2));

        outputScroll = new ScrollView(this);
        outputScroll.setId(View.generateViewId());
        outputScroll.setFillViewport(true);
        outputContainer = new LinearLayout(this);
        outputContainer.setOrientation(LinearLayout.VERTICAL);
        outputContainer.setPadding(dp(34), 0, dp(34), 0);
        outputView = label("", outputTextSizeSp, false);
        outputView.setTextIsSelectable(false);
        outputView.setClickable(true);
        outputView.setLinksClickable(true);
        outputView.setHighlightColor(Color.TRANSPARENT);
        outputView.setMovementMethod(LinkMovementMethod.getInstance());
        outputContainer.addView(outputView, new LinearLayout.LayoutParams(-1, -2));
        fileRowsView = new LinearLayout(this);
        fileRowsView.setOrientation(LinearLayout.VERTICAL);
        outputContainer.addView(fileRowsView, new LinearLayout.LayoutParams(-1, -2));
        outputScroll.addView(outputContainer, new ScrollView.LayoutParams(-1, -2));
        contentFrame.addView(outputScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        buildSideRails();

        inputGroup = new LinearLayout(this);
        inputGroup.setGravity(Gravity.CENTER_VERTICAL);
        inputGroup.setPadding(dp(8), 0, dp(8), 0);
        inputPrefixView = label("$ ", inputFontSizeSp, true);
        inputPrefixView.setGravity(Gravity.CENTER);
        inputPrefixView.setIncludeFontPadding(false);
        inputGroup.addView(inputPrefixView, new LinearLayout.LayoutParams(-2, -1));
        inputView = new EditText(this);
        inputView.setSingleLine(true);
        inputView.setTextSize(inputFontSizeSp);
        inputView.setTypeface(appTypeface);
        inputView.setGravity(Gravity.CENTER_VERTICAL);
        inputView.setIncludeFontPadding(false);
        inputView.setPadding(0, 0, 0, 0);
        inputView.setBackgroundColor(Color.TRANSPARENT);
        inputView.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        inputView.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                runInput(inputView.getText().toString());
                return true;
            }
            return false;
        });
        inputView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSuggestions(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        inputGroup.addView(inputView, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(38));
        inputParams.topMargin = dp(10);
        bottomDock.addView(inputGroup, inputParams);

        suggestionsScroll = new HorizontalScrollView(this);
        suggestionsScroll.setHorizontalScrollBarEnabled(false);
        suggestionsScroll.setVisibility(View.GONE);
        suggestionsGroup = new LinearLayout(this);
        suggestionsGroup.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsScroll.addView(suggestionsGroup, new HorizontalScrollView.LayoutParams(-2, dp(34)));
        bottomDock.addView(suggestionsScroll, new LinearLayout.LayoutParams(-1, dp(34)));

        toolsView = new LinearLayout(this);
        toolsView.setGravity(Gravity.CENTER);
        addTool(toolsView, "REFRESH", v -> renderListing());
        addTool(toolsView, "UP", v -> changeDirectory(".."));
        addTool(toolsView, "OPEN", v -> seed("open "));
        addTool(toolsView, "SHARE", v -> seed("share "));
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, dp(28));
        toolsParams.topMargin = dp(4);
        bottomDock.addView(toolsView, toolsParams);

        styleUi();
        installKeyboardInsetWatcher();
    }

    private void buildSideRails() {
        alphaRailHost = new FrameLayout(this);
        alphaRailHost.setClipChildren(true);
        alphaRailHost.setClipToPadding(true);
        alphaRailView = new SideRailView(this);
        alphaRailHost.addView(alphaRailView, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(alphaRailHost, new FrameLayout.LayoutParams(dp(34), dp(80)));

        pinnedRailHost = new FrameLayout(this);
        pinnedRailHost.setClipChildren(true);
        pinnedRailHost.setClipToPadding(true);
        pinnedRailView = new SideRailView(this);
        pinnedRailHost.addView(pinnedRailView, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(pinnedRailHost, new FrameLayout.LayoutParams(dp(38), dp(80)));
        installRailBoundsWatcher();
    }

    private void installRailBoundsWatcher() {
        if (root == null || outputScroll == null || bottomDock == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::updateRailBounds);
        root.post(this::updateRailBounds);
    }

    private void updateRailBounds() {
        if (alphaRailHost == null || pinnedRailHost == null || outputScroll == null || bottomDock == null || root == null || stage == null) return;
        int[] stageLoc = new int[2];
        int[] rootLoc = new int[2];
        int[] outputLoc = new int[2];
        int[] bottomLoc = new int[2];
        stage.getLocationInWindow(stageLoc);
        root.getLocationInWindow(rootLoc);
        outputScroll.getLocationInWindow(outputLoc);
        View bottomAnchor = inputGroup != null ? inputGroup : bottomDock;
        bottomAnchor.getLocationInWindow(bottomLoc);
        int contentLeft = stage.getPaddingLeft();
        int contentTop = stage.getPaddingTop();
        int contentWidth = stage.getWidth() - stage.getPaddingLeft() - stage.getPaddingRight();
        int rootLeft = rootLoc[0] - stageLoc[0] - contentLeft;
        int rootRight = rootLeft + root.getWidth();
        int top = outputLoc[1] - stageLoc[1] - contentTop + dp(42);
        int bottom = bottomLoc[1] - stageLoc[1] - contentTop - dp(8);
        int height = Math.max(dp(80), bottom - top);
        int alphaWidth = dp(34);
        int pinnedWidth = dp(38);
        int minLeft = -contentLeft;
        int maxLeft = contentWidth + stage.getPaddingRight() - pinnedWidth;
        int alphaLeft = clamp(rootLeft - alphaWidth / 2, minLeft, maxLeft);
        int pinnedLeft = clamp(rootRight - pinnedWidth / 2, minLeft, maxLeft);
        applyRailBounds(alphaRailHost, alphaLeft, top, alphaWidth, height);
        applyRailBounds(pinnedRailHost, pinnedLeft, top, pinnedWidth, height);
    }

    private void applyRailBounds(View rail, int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rail.getLayoutParams();
        if (params == null) return;
        if (params.leftMargin != left || params.topMargin != top || params.width != width || params.height != height) {
            params.leftMargin = left;
            params.topMargin = top;
            params.width = width;
            params.height = height;
            rail.setLayoutParams(params);
        }
    }

    private void styleUi() {
        if (root == null) return;
        applyWallpaperBackground();
        applyWindowMargins();
        stylePanel(root, false);
        if (contentFrame != null) {
            contentFrame.setBackgroundColor(Color.TRANSPARENT);
        }
        if (titleView != null) {
            titleView.setTextColor(headerTextColor);
            titleView.setTextSize(headerTextSizeSp);
            stylePanel(titleView, PanelRole.HEADER);
        }
        if (closeView != null) {
            closeView.setTextColor(headerTextColor);
            stylePanel(closeView, PanelRole.HEADER);
        }
        if (pathView != null) {
            pathView.setTextColor(outputTextColor);
            pathView.setTextSize(outputHeaderTextSizeSp);
        }
        if (outputView != null) {
            outputView.setTextColor(outputTextColor);
            outputView.setTextSize(outputTextSizeSp);
            outputView.setTypeface(appTypeface);
        }
        if (inputView != null) {
            inputView.setTextColor(inputTextColor);
            inputView.setHintTextColor(Color.argb(150, Color.red(inputTextColor), Color.green(inputTextColor), Color.blue(inputTextColor)));
            inputView.setTypeface(appTypeface);
            inputView.setTextSize(inputFontSizeSp);
            inputView.setGravity(Gravity.CENTER_VERTICAL);
            inputView.setIncludeFontPadding(false);
            stylePanel(inputGroup, PanelRole.INPUT);
        }
        if (inputPrefixView != null) {
            inputPrefixView.setTextColor(inputTextColor);
            inputPrefixView.setTypeface(appTypeface, Typeface.BOLD);
            inputPrefixView.setTextSize(inputFontSizeSp);
            inputPrefixView.setGravity(Gravity.CENTER);
            inputPrefixView.setIncludeFontPadding(false);
        }
        if (toolsView != null) toolsView.setBackgroundColor(Color.TRANSPARENT);
        if (alphaRailView != null) alphaRailView.invalidate();
        if (pinnedRailView != null) pinnedRailView.invalidate();
    }

    private void installKeyboardInsetWatcher() {
        if (stage == null || rootLayoutParams == null) return;
        stage.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            stage.getWindowVisibleDisplayFrame(visible);
            int screenHeight = stage.getRootView().getHeight();
            int keyboardHeight = Math.max(0, screenHeight - visible.bottom);
            int newBottom = keyboardHeight > dp(120) ? keyboardHeight + dp(8) : dp(2);
            if (rootLayoutParams.bottomMargin != newBottom) {
                rootLayoutParams.bottomMargin = newBottom;
                root.setLayoutParams(rootLayoutParams);
            }
        });
    }

    private void runInput(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.length() == 0) return;
        history.add(command);
        historyIndex = history.size();
        preserveInputAfterCommand = false;
        execute(command);
        if (!preserveInputAfterCommand) inputView.setText("");
        refocusInput();
    }

    private void execute(String command) {
        String lower = command.toLowerCase(Locale.US);
        if ("help".equals(lower)) {
            print("Commands:\ncd [folder]\ncd ..\nls\npwd\nfind [path] -name [pattern]\nsearch [pattern]\ntree [-a -d -f -h -s -D -F -i -r --dirsfirst --ignore-case --noreport -L n -P pattern -I pattern --sort name|size|mtime]\nopen [file]\nshare [file]\nmkdir [folder]\ncp [-r] [source] [destination]\nmv [source] [destination]\nrm [-r] [file]\nzip -r [archive.zip] [folder]\nfav add [label] [path]\nfav rm [label]\nfav rename [old] [new]\nfav list\npermission\nrefresh\nexit");
        } else if ("exit".equals(lower) || "close".equals(lower)) {
            finish();
        } else if ("permission".equals(lower) || "permissions".equals(lower) || "permit".equals(lower)) {
            openStorageAccessSettings();
        } else if ("tree".equals(lower) || lower.startsWith("tree ")) {
            renderTree(command);
        } else if ("find".equals(lower) || lower.startsWith("find ") || lower.startsWith("search ")) {
            runFind(command);
        } else if ("fav".equals(lower) || lower.startsWith("fav ")) {
            runFavoriteCommand(splitArgs(command));
        } else if ("ls".equals(lower) || lower.startsWith("ls ") || "refresh".equals(lower)) {
            renderListing();
        } else if ("pwd".equals(lower)) {
            print(currentDirectory.getAbsolutePath());
        } else if (lower.equals("cd") || lower.startsWith("cd ")) {
            changeDirectory(command.length() > 2 ? command.substring(2).trim() : "");
        } else if (lower.startsWith("open ")) {
            openFile(command.substring(5).trim());
        } else if (lower.startsWith("share ")) {
            shareFile(command.substring(6).trim());
        } else if (lower.startsWith("mkdir ")) {
            runShellFileCommand(command);
        } else if (lower.startsWith("rm ") || lower.startsWith("cp ") || lower.startsWith("mv ") || lower.startsWith("zip ")) {
            runShellFileCommand(command);
        } else {
            preserveInputAfterCommand = true;
            showTerminalPopup("error", "Command not found: " + command + "\nType help.");
        }
    }

    private void changeDirectory(String target) {
        File dir = resolve(target);
        if (dir == null || !dir.exists()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("cd", "Not found: " + target);
            return;
        }
        if (!dir.isDirectory()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("cd", "Not a directory: " + target);
            return;
        }
        currentDirectory = dir;
        expandedPaths.clear();
        renderListing();
    }

    private void renderListing() {
        renderListing(TreeOptions.defaultListing());
    }

    private void renderListing(TreeOptions options) {
        activeTreeOptions = options;
        if (pathView != null) pathView.setText(currentDirectory.getAbsolutePath());
        if (!hasStorageAccess()) {
            showTerminalPopup("permission", "Storage access required.\nType: permission\nOr grant All files access in Android settings.");
            return;
        }
        File[] children = currentDirectory.listFiles();
        if (children == null) {
            showTerminalPopup("error", "Cannot read: " + currentDirectory.getAbsolutePath());
            return;
        }
        StringBuilder report = new StringBuilder();
        ArrayList<TreeRow> rows = new ArrayList<>();
        int[] remaining = new int[]{TREE_MAX_ITEMS};
        TreeStats stats = new TreeStats();
        appendTreeRows(rows, currentDirectory, "", 0, options, remaining, stats);
        if (remaining[0] <= 0) {
            report.append("...\n");
            report.append("Output capped. Use cd to narrow the surface.");
        }
        if (!options.noReport) {
            if (report.length() > 0) report.append("\n\n");
            report.append(stats.directories).append(stats.directories == 1 ? " directory" : " directories");
            if (!options.dirsOnly) {
                report.append(", ").append(stats.files).append(stats.files == 1 ? " file" : " files");
            }
        }
        renderFileRows(rows, report.toString());
        updateSuggestions(inputView == null ? "" : inputView.getText().toString());
    }

    private void renderTree(String command) {
        TreeOptions options = parseTreeOptions(command);
        if (options.error != null) {
            preserveInputAfterCommand = true;
            showTerminalPopup("tree", options.error);
            return;
        }
        renderListing(options);
    }

    private void runFind(String command) {
        List<String> args = splitArgs(command);
        if (args.isEmpty()) return;
        boolean searchAlias = "search".equalsIgnoreCase(args.get(0));
        File rootDir = currentDirectory;
        String pattern = null;
        boolean allFiles = false;
        int limit = 400;

        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-a".equals(arg)) {
                allFiles = true;
            } else if ("-name".equals(arg) || "--name".equals(arg)) {
                if (i + 1 >= args.size()) {
                    preserveInputAfterCommand = true;
                    showTerminalPopup("find", "find: -name requires a pattern");
                    return;
                }
                pattern = args.get(++i);
            } else if ("-max".equals(arg) || "--limit".equals(arg)) {
                if (i + 1 >= args.size()) {
                    preserveInputAfterCommand = true;
                    showTerminalPopup("find", "find: " + arg + " requires a number");
                    return;
                }
                int parsed = parsePositiveInt(args.get(++i), "find: invalid limit");
                if (parsed <= 0) {
                    preserveInputAfterCommand = true;
                    showTerminalPopup("find", "find: invalid limit");
                    return;
                }
                limit = parsed;
            } else if (pattern == null && (searchAlias || arg.contains("*") || arg.contains("?"))) {
                pattern = arg;
            } else if (pattern == null && args.size() == 2) {
                pattern = arg;
            } else {
                File maybeRoot = resolve(arg);
                if (maybeRoot != null && maybeRoot.exists() && maybeRoot.isDirectory()) {
                    rootDir = maybeRoot;
                } else if (pattern == null) {
                    pattern = arg;
                } else {
                    preserveInputAfterCommand = true;
                    showTerminalPopup("find", "find: unsupported argument: " + arg);
                    return;
                }
            }
        }

        if (pattern == null || pattern.length() == 0) {
            preserveInputAfterCommand = true;
            showTerminalPopup("find", "find: usage: find [path] -name [pattern]\nsearch [pattern]");
            return;
        }
        Pattern compiled = compileFindPattern(pattern);
        ArrayList<TreeRow> rows = new ArrayList<>();
        int[] remaining = new int[]{limit};
        findRows(rootDir, compiled, allFiles, rows, remaining);
        String report = rows.size() + (rows.size() == 1 ? " match" : " matches");
        if (remaining[0] <= 0) report += "\nOutput capped. Use a narrower pattern.";
        renderFileRows(rows, report);
    }

    private Pattern compileFindPattern(String pattern) {
        if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
            return compileGlob(pattern, true);
        }
        return Pattern.compile(".*" + Pattern.quote(pattern.toLowerCase(Locale.US)) + ".*");
    }

    private void findRows(File directory, Pattern pattern, boolean allFiles, List<TreeRow> rows, int[] remaining) {
        if (directory == null || remaining[0] <= 0) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (remaining[0] <= 0) return;
            String name = child.getName();
            if (!allFiles && name.startsWith(".")) continue;
            if (pattern.matcher(name.toLowerCase(Locale.US)).matches()) {
                String display = pathKey(child).startsWith(pathKey(currentDirectory))
                        ? pathKey(child).substring(pathKey(currentDirectory).length()).replaceFirst("^/", "")
                        : child.getAbsolutePath();
                rows.add(new TreeRow("|-- ", iconFor(child, false), display, child, child.isDirectory(), false, hasChildren(child)));
                remaining[0]--;
            }
            if (child.isDirectory()) findRows(child, pattern, allFiles, rows, remaining);
        }
    }

    private void appendTree(SpannableStringBuilder out, File directory, String prefix, int depth, TreeOptions options, int[] remaining, TreeStats stats) {
        File[] children = directory.listFiles();
        if (children == null) {
            if (out.length() == 0) out.append("[..]");
            return;
        }
        List<File> visible = filterChildren(children, options);
        visible.sort((left, right) -> compareFilesForTree(left, right, options));
        if (depth == 0) {
            int parentStart = out.length();
            out.append("[..]");
            out.setSpan(new FileClickSpan(directory.getParentFile(), true), parentStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int index = 0; index < visible.size() && remaining[0] > 0; index++) {
            File child = visible.get(index);
            boolean last = index == visible.size() - 1;
            out.append('\n');
            appendTreeLine(out, child, prefix, last, options);
            remaining[0]--;
            if (child.isDirectory()) {
                stats.directories++;
            } else {
                stats.files++;
            }
            boolean manuallyExpanded = child.isDirectory() && expandedPaths.contains(pathKey(child));
            if (child.isDirectory() && (depth < options.maxDepth || manuallyExpanded) && remaining[0] > 0) {
                appendTree(out, child, prefix + branchPadding(last, options), depth + 1, options, remaining, stats);
            }
        }
    }

    private void appendTreeRows(List<TreeRow> rows, File directory, String prefix, int depth, TreeOptions options, int[] remaining, TreeStats stats) {
        File[] children = directory.listFiles();
        if (depth == 0) {
            rows.add(new TreeRow("", "", "[..]", directory.getParentFile(), true, false, directory.getParentFile() != null));
        }
        if (children == null) return;
        List<File> visible = filterChildren(children, options);
        visible.sort((left, right) -> compareFilesForTree(left, right, options));
        for (int index = 0; index < visible.size() && remaining[0] > 0; index++) {
            File child = visible.get(index);
            boolean last = index == visible.size() - 1;
            boolean manuallyExpanded = child.isDirectory() && expandedPaths.contains(pathKey(child));
            boolean expanded = child.isDirectory() && (depth < options.maxDepth || manuallyExpanded);
            String rowPrefix = treePrefixText(prefix, last, options);
            String icon = iconFor(child, expanded);
            rows.add(new TreeRow(rowPrefix, icon, treeNameText(child, options), child, child.isDirectory(), expanded, hasChildren(child)));
            remaining[0]--;
            if (child.isDirectory()) {
                stats.directories++;
            } else {
                stats.files++;
            }
            if (expanded && remaining[0] > 0) {
                appendTreeRows(rows, child, prefix + branchPadding(last, options), depth + 1, options, remaining, stats);
            }
        }
    }

    private List<File> filterChildren(File[] children, TreeOptions options) {
        ArrayList<File> visible = new ArrayList<>();
        for (File child : children) {
            if (!options.allFiles && child.getName().startsWith(".")) continue;
            if (options.dirsOnly && !child.isDirectory()) continue;
            if (options.includePattern != null && !matchesPattern(child, options.includePattern, options.ignoreCase, options.matchDirs)) continue;
            if (options.excludePattern != null && matchesPattern(child, options.excludePattern, options.ignoreCase, true)) continue;
            visible.add(child);
        }
        return visible;
    }

    private boolean matchesPattern(File file, Pattern pattern, boolean ignoreCase, boolean includeDirectories) {
        if (file.isDirectory() && !includeDirectories) return false;
        String value = file.getName();
        return pattern.matcher(ignoreCase ? value.toLowerCase(Locale.US) : value).matches();
    }

    private void appendTreeLine(SpannableStringBuilder out, File file, String prefix, boolean last, TreeOptions options) {
        int lineStart = out.length();
        if (!options.fullPath) {
            out.append(prefix);
            if (!options.noIndentLines) out.append(last ? "`-- " : "|-- ");
        }
        if (options.humanSize || options.byteSize) {
            out.append('[').append(options.humanSize ? humanSize(file.length()) : String.valueOf(file.length())).append("] ");
        }
        if (options.date) {
            out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified())).append(' ');
        }
        if (!options.fullPath && file.isDirectory()) out.append("[D] ");
        out.append(options.fullPath ? file.getAbsolutePath() : file.getName());
        if (options.typeSuffix) out.append(typeSuffix(file));
        out.setSpan(new FileClickSpan(file, file.isDirectory()), lineStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String treeLineText(File file, String prefix, boolean last, TreeOptions options) {
        StringBuilder out = new StringBuilder();
        if (!options.fullPath) {
            out.append(prefix);
            if (!options.noIndentLines) out.append(last ? "`-- " : "|-- ");
        }
        if (options.humanSize || options.byteSize) {
            out.append('[').append(options.humanSize ? humanSize(file.length()) : String.valueOf(file.length())).append("] ");
        }
        if (options.date) {
            out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified())).append(' ');
        }
        if (!options.fullPath && file.isDirectory()) out.append("[D] ");
        out.append(options.fullPath ? file.getAbsolutePath() : file.getName());
        if (options.typeSuffix) out.append(typeSuffix(file));
        return out.toString();
    }

    private String treePrefixText(String prefix, boolean last, TreeOptions options) {
        if (options.fullPath) return "";
        StringBuilder out = new StringBuilder(prefix);
        if (!options.noIndentLines) out.append(last ? "`-- " : "|-- ");
        return out.toString();
    }

    private String treeNameText(File file, TreeOptions options) {
        StringBuilder out = new StringBuilder();
        if (options.humanSize || options.byteSize) {
            out.append('[').append(options.humanSize ? humanSize(file.length()) : String.valueOf(file.length())).append("] ");
        }
        if (options.date) {
            out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified())).append(' ');
        }
        out.append(options.fullPath ? file.getAbsolutePath() : file.getName());
        if (options.typeSuffix) out.append(typeSuffix(file));
        return out.toString();
    }

    private String iconFor(File file, boolean expanded) {
        if (file == null) return "";
        if (file.isDirectory()) return expanded ? ICON_FOLDER_OPEN : ICON_FOLDER;
        String ext = extension(file.getName());
        if (isOneOf(ext, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")) return ICON_IMAGE;
        if (isOneOf(ext, "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "md")) return ICON_DOCUMENT;
        if (isOneOf(ext, "json", "xml", "html", "css", "js", "ts", "java", "kt", "kts", "lua", "sh", "py", "rb", "go", "rs", "c", "cpp", "h", "gradle", "yml", "yaml")) return ICON_CODE;
        return ICON_FILE;
    }

    private String extension(String name) {
        int index = name == null ? -1 : name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) return "";
        return name.substring(index + 1).toLowerCase(Locale.US);
    }

    private boolean isOneOf(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) return true;
        }
        return false;
    }

    private boolean hasChildren(File directory) {
        if (directory == null || !directory.isDirectory()) return false;
        File[] children = directory.listFiles();
        return children != null && children.length > 0;
    }

    private String branchPadding(boolean last, TreeOptions options) {
        if (options.noIndentLines) return "    ";
        return last ? "    " : "|   ";
    }

    private int compareFilesForTree(File left, File right, TreeOptions options) {
        if (options.dirsFirst && left.isDirectory() != right.isDirectory()) {
            return left.isDirectory() ? -1 : 1;
        }
        int result;
        if ("size".equals(options.sort)) {
            result = Long.compare(left.length(), right.length());
        } else if ("mtime".equals(options.sort)) {
            result = Long.compare(left.lastModified(), right.lastModified());
        } else {
            result = String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
        }
        return options.reverse ? -result : result;
    }

    private TreeOptions parseTreeOptions(String command) {
        TreeOptions options = TreeOptions.expanded();
        List<String> args = splitArgs(command);
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--".equals(arg)) break;
            if ("-a".equals(arg)) options.allFiles = true;
            else if ("-d".equals(arg)) options.dirsOnly = true;
            else if ("-f".equals(arg)) options.fullPath = true;
            else if ("-h".equals(arg)) options.humanSize = true;
            else if ("-s".equals(arg)) options.byteSize = true;
            else if ("-D".equals(arg)) options.date = true;
            else if ("-F".equals(arg)) options.typeSuffix = true;
            else if ("-i".equals(arg)) options.noIndentLines = true;
            else if ("-r".equals(arg)) options.reverse = true;
            else if ("--dirsfirst".equals(arg)) options.dirsFirst = true;
            else if ("--ignore-case".equals(arg)) options.ignoreCase = true;
            else if ("--matchdirs".equals(arg)) options.matchDirs = true;
            else if ("--noreport".equals(arg)) options.noReport = true;
            else if ("-L".equals(arg)) {
                if (i + 1 >= args.size()) return TreeOptions.error("tree: -L requires a level");
                options.maxDepth = parsePositiveInt(args.get(++i), "tree: invalid level");
                if (options.maxDepth < 0) return TreeOptions.error("tree: invalid level");
            } else if ("-P".equals(arg)) {
                if (i + 1 >= args.size()) return TreeOptions.error("tree: -P requires a pattern");
                options.includePattern = compileGlob(args.get(++i), options.ignoreCase);
            } else if ("-I".equals(arg)) {
                if (i + 1 >= args.size()) return TreeOptions.error("tree: -I requires a pattern");
                options.excludePattern = compileGlob(args.get(++i), options.ignoreCase);
            } else if ("--sort".equals(arg)) {
                if (i + 1 >= args.size()) return TreeOptions.error("tree: --sort requires name, size, or mtime");
                String sort = args.get(++i).toLowerCase(Locale.US);
                if (!"name".equals(sort) && !"size".equals(sort) && !"mtime".equals(sort)) {
                    return TreeOptions.error("tree: unsupported sort: " + sort);
                }
                options.sort = sort;
            } else {
                return TreeOptions.error("tree: unsupported option: " + arg);
            }
        }
        return options;
    }

    private int parsePositiveInt(String raw, String error) {
        try {
            int value = Integer.parseInt(raw);
            return value >= 0 ? value : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private Pattern compileGlob(String glob, boolean ignoreCase) {
        String value = ignoreCase ? glob.toLowerCase(Locale.US) : glob;
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.toString());
    }

    private List<String> splitArgs(String raw) {
        ArrayList<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        if (current.length() > 0) args.add(current.toString());
        return args;
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value = value / 1024.0;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.US, "%.1f%s", value, units[unit]);
    }

    private String typeSuffix(File file) {
        if (file.isDirectory()) return "/";
        if (file.canExecute()) return "*";
        return "";
    }

    private void openFile(String target) {
        File file = resolve(target);
        if (file == null || !file.exists()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("open", "Not found: " + target);
            return;
        }
        if (file.isDirectory()) {
            changeDirectory(target);
            return;
        }
        openFile(file);
    }

    private void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriFor(file), mimeFor(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Open with"));
            print("Opening: " + file.getName());
        } catch (Exception e) {
            showTerminalPopup("open", "No app can open: " + file.getName());
        }
    }

    private void toggleDirectory(File directory) {
        if (directory == null || !directory.exists()) return;
        if (!directory.isDirectory()) {
            openFile(directory);
            return;
        }
        String key = pathKey(directory);
        if (expandedPaths.contains(key)) {
            collapseDirectory(key);
        } else {
            expandedPaths.add(key);
        }
        renderListing(activeTreeOptions == null ? TreeOptions.defaultListing() : activeTreeOptions);
    }

    private void collapseDirectory(String key) {
        ArrayList<String> removals = new ArrayList<>();
        for (String path : expandedPaths) {
            if (path.equals(key) || path.startsWith(key + File.separator)) {
                removals.add(path);
            }
        }
        expandedPaths.removeAll(removals);
    }

    private void shareFile(String target) {
        File file = resolve(target);
        if (file == null || !file.exists() || file.isDirectory()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("share", "Not a file: " + target);
            return;
        }
        Uri uri = uriFor(file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeFor(file));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share with"));
        print("Sharing: " + file.getName());
    }

    private void makeDirectory(String name) {
        File dir = resolve(name);
        if (dir == null) {
            showTerminalPopup("mkdir", "Could not create: " + name);
            return;
        }
        if (dir.exists()) {
            showTerminalPopup("mkdir", "mkdir: already exists: " + dir.getAbsolutePath());
            return;
        }
        if (dir.mkdirs()) {
            renderListing();
            showTerminalPopup("mkdir", "created directory: " + dir.getAbsolutePath());
        } else {
            showTerminalPopup("mkdir", "Could not create: " + name);
        }
    }

    private void remove(String target) {
        runShellFileCommand("rm " + target);
    }

    private void runShellFileCommand(String command) {
        List<String> args = splitArgs(command);
        if (args.isEmpty()) return;
        String verb = args.get(0).toLowerCase(Locale.US);
        if ("mkdir".equals(verb)) {
            runMkdir(args);
        } else if ("rm".equals(verb)) {
            runRemove(args);
        } else if ("cp".equals(verb)) {
            runCopyMove(args, false);
        } else if ("mv".equals(verb)) {
            runCopyMove(args, true);
        } else if ("zip".equals(verb)) {
            runZip(args);
        }
    }

    private void runFavoriteCommand(List<String> args) {
        if (args == null || args.size() < 2 || "list".equalsIgnoreCase(args.get(1))) {
            showTerminalPopup("fav", favoriteListText());
            return;
        }
        String op = args.get(1).toLowerCase(Locale.US);
        if ("add".equals(op)) {
            addFavoriteFromArgs(args);
        } else if ("rm".equals(op) || "remove".equals(op) || "del".equals(op)) {
            if (args.size() < 3) {
                preserveInputAfterCommand = true;
                showTerminalPopup("fav", "fav rm requires a label");
                return;
            }
            removeFavorite(args.get(2));
        } else if ("rename".equals(op)) {
            if (args.size() < 4) {
                preserveInputAfterCommand = true;
                showTerminalPopup("fav", "fav rename requires old and new labels");
                return;
            }
            renameFavorite(args.get(2), args.get(3));
        } else {
            preserveInputAfterCommand = true;
            showTerminalPopup("fav", "fav: usage\nfav add [label] [path]\nfav rm [label]\nfav rename [old] [new]\nfav list");
        }
    }

    private void addFavoriteFromArgs(List<String> args) {
        File target = currentDirectory;
        String label = null;
        if (args.size() == 3) {
            File candidate = resolve(args.get(2));
            if (candidate != null && candidate.exists() && candidate.isDirectory()) {
                target = candidate;
            } else {
                label = args.get(2);
            }
        } else if (args.size() >= 4) {
            label = args.get(2);
            target = resolve(args.get(3));
        }
        if (target == null || !target.exists() || !target.isDirectory()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("fav", "favorite path is not a directory");
            return;
        }
        ArrayList<FavoritePath> favorites = loadFavorites();
        String cleanLabel = normalizeFavoriteLabel(label == null ? autoFavoriteLabel(target, favorites) : label);
        if (cleanLabel.length() == 0) cleanLabel = autoFavoriteLabel(target, favorites);
        upsertFavorite(favorites, new FavoritePath(cleanLabel, pathKey(target)));
        saveFavorites(favorites);
        buildPinnedRail();
        showTerminalPopup("fav", "favorite added: " + cleanLabel + "\n" + pathKey(target));
    }

    private void removeFavorite(String label) {
        String key = normalizeFavoriteLabel(label);
        ArrayList<FavoritePath> favorites = loadFavorites();
        boolean removed = false;
        for (int i = favorites.size() - 1; i >= 0; i--) {
            if (favorites.get(i).label.equalsIgnoreCase(key)) {
                favorites.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            preserveInputAfterCommand = true;
            showTerminalPopup("fav", "favorite not found: " + label);
            return;
        }
        saveFavorites(favorites);
        buildPinnedRail();
        showTerminalPopup("fav", "favorite removed: " + key);
    }

    private void renameFavorite(String oldLabel, String newLabel) {
        String oldKey = normalizeFavoriteLabel(oldLabel);
        String newKey = normalizeFavoriteLabel(newLabel);
        if (newKey.length() == 0) {
            preserveInputAfterCommand = true;
            showTerminalPopup("fav", "new label is empty");
            return;
        }
        ArrayList<FavoritePath> favorites = loadFavorites();
        for (FavoritePath favorite : favorites) {
            if (favorite.label.equalsIgnoreCase(oldKey)) {
                favorite.label = newKey;
                saveFavorites(favorites);
                buildPinnedRail();
                showTerminalPopup("fav", "favorite renamed: " + oldKey + " -> " + newKey);
                return;
            }
        }
        preserveInputAfterCommand = true;
        showTerminalPopup("fav", "favorite not found: " + oldLabel);
    }

    private void runZip(List<String> args) {
        boolean recursive = false;
        ArrayList<String> operands = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-r".equals(arg) || "-R".equals(arg) || "--recursive".equals(arg)) recursive = true;
            else operands.add(arg);
        }
        if (operands.size() < 2) {
            preserveInputAfterCommand = true;
            showTerminalPopup("zip", "zip: usage: zip -r archive_name.zip directory_name");
            return;
        }
        File archive = resolve(operands.remove(0));
        if (archive == null) {
            preserveInputAfterCommand = true;
            showTerminalPopup("zip", "zip: invalid archive path");
            return;
        }
        if (archive.exists() && archive.isDirectory()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("zip", "zip: archive path is a directory:\n" + archive.getAbsolutePath() + "\n\nUse: zip -r archive_name.zip directory_name");
            return;
        }
        ArrayList<SourceSpec> sources = expandSourceArgs(operands);
        if (sources.isEmpty()) {
            preserveInputAfterCommand = true;
            showTerminalPopup("zip", "zip: no matches");
            return;
        }
        for (SourceSpec source : sources) {
            if (source.file.isDirectory() && !recursive && !source.copyContents) {
                preserveInputAfterCommand = true;
                showTerminalPopup("zip", "zip: directory requires -r:\n" + source.file.getAbsolutePath());
                return;
            }
        }
        ZipPlan plan = buildZipPlan(archive, sources, recursive);
        if (plan.error != null) {
            preserveInputAfterCommand = true;
            showTerminalPopup("zip", plan.error);
            return;
        }
        Runnable start = () -> startZipOperation(plan);
        if (archive.exists()) {
            confirmTerminal("overwrite existing archive?\n" + archive.getAbsolutePath(), "zip", start);
        } else {
            start.run();
        }
    }

    private void runMkdir(List<String> args) {
        boolean parents = false;
        ArrayList<String> paths = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-p".equals(arg)) parents = true;
            else paths.add(arg);
        }
        if (paths.isEmpty()) {
            showTerminalPopup("mkdir", "mkdir: missing operand");
            return;
        }
        OperationResult result = new OperationResult();
        for (String path : paths) {
            File dir = resolve(path);
            if (dir == null) {
                result.failures.add(path + ": invalid path");
                continue;
            }
            if (dir.exists()) {
                if (dir.isDirectory() && parents) result.skipped++;
                else result.failures.add(dir.getAbsolutePath() + ": already exists");
                continue;
            }
            if (dir.mkdirs()) result.created++;
            else result.failures.add(dir.getAbsolutePath() + ": could not create");
        }
        renderListing();
        showTerminalPopup("mkdir", result.summary("created directories", "deleted files", "copied files"));
    }

    private void runRemove(List<String> args) {
        boolean recursive = false;
        ArrayList<String> patterns = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-r".equals(arg) || "-R".equals(arg) || "--recursive".equals(arg)) recursive = true;
            else patterns.add(arg);
        }
        if (patterns.isEmpty()) {
            showTerminalPopup("rm", "rm: missing operand");
            return;
        }
        ArrayList<File> targets = expandTargetArgs(patterns, true);
        if (targets.isEmpty()) {
            showTerminalPopup("rm", "rm: no matches");
            return;
        }
        for (File target : targets) {
            if (target.isDirectory() && !recursive) {
                showTerminalPopup("rm", "rm: cannot remove directory without -r:\n" + target.getAbsolutePath());
                return;
            }
        }
        DeletePlan plan = buildDeletePlan(targets);
        if (plan.items.isEmpty()) {
            showTerminalPopup("rm", "rm: nothing to delete");
            return;
        }
        Runnable start = () -> startDeleteOperation(plan);
        if (recursive || targets.size() > 1 || plan.items.size() > 1) {
            confirmTerminal("delete " + plan.items.size() + " item(s)?", "rm", start);
        } else {
            start.run();
        }
    }

    private void runCopyMove(List<String> args, boolean move) {
        boolean recursive = move;
        ArrayList<String> operands = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-r".equals(arg) || "-R".equals(arg) || "--recursive".equals(arg)) recursive = true;
            else operands.add(arg);
        }
        if (operands.size() < 2) {
            showTerminalPopup(move ? "mv" : "cp", (move ? "mv" : "cp") + ": missing operand");
            return;
        }
        String destArg = operands.remove(operands.size() - 1);
        File destination = resolve(destArg);
        boolean destAsDirectory = destArg.endsWith("/") || destArg.endsWith(File.separator);
        ArrayList<SourceSpec> sources = expandSourceArgs(operands);
        if (sources.isEmpty()) {
            showTerminalPopup(move ? "mv" : "cp", (move ? "mv" : "cp") + ": no matches");
            return;
        }
        if (sources.size() > 1 && (destination == null || (destination.exists() && !destination.isDirectory()))) {
            showTerminalPopup(move ? "mv" : "cp", (move ? "mv" : "cp") + ": destination must be directory for multiple sources");
            return;
        }
        for (SourceSpec source : sources) {
            if (source.file.isDirectory() && !recursive && !source.copyContents) {
                showTerminalPopup("cp", "cp: omitting directory:\n" + source.file.getAbsolutePath());
                return;
            }
        }
        CopyPlan plan = buildCopyPlan(sources, destination, recursive, move, destAsDirectory);
        if (plan.error != null) {
            showTerminalPopup(move ? "mv" : "cp", plan.error);
            return;
        }
        Runnable start = () -> startCopyMoveOperation(plan);
        if (plan.overwriteCount > 0) {
            confirmTerminal("overwrite " + plan.overwriteCount + " existing file(s)?", move ? "mv" : "cp", start);
        } else {
            start.run();
        }
    }

    private ArrayList<File> expandTargetArgs(List<String> patterns, boolean includeDotHidden) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<File> out = new ArrayList<>();
        for (String pattern : patterns) {
            List<File> expanded = expandGlob(pattern, includeDotHidden);
            for (File file : expanded) {
                String key = pathKey(file);
                if (seen.add(key)) out.add(file);
            }
        }
        return out;
    }

    private ArrayList<SourceSpec> expandSourceArgs(List<String> patterns) {
        ArrayList<SourceSpec> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String pattern : patterns) {
            if (pattern.endsWith("/.") || ".".equals(pattern)) {
                File dir = resolve(pattern.endsWith("/.") ? pattern.substring(0, pattern.length() - 2) : pattern);
                if (dir != null && dir.exists()) out.add(new SourceSpec(dir, true));
                continue;
            }
            List<File> expanded = expandGlob(pattern, false);
            for (File file : expanded) {
                String key = pathKey(file);
                if (seen.add(key)) out.add(new SourceSpec(file, false));
            }
        }
        return out;
    }

    private List<File> expandGlob(String raw, boolean includeDotHidden) {
        if (raw.indexOf('*') < 0) {
            File file = resolve(raw);
            return file != null && file.exists() ? Collections.singletonList(file) : Collections.emptyList();
        }
        int slash = raw.lastIndexOf(File.separatorChar);
        String dirPart = slash >= 0 ? raw.substring(0, slash) : "";
        String nameGlob = slash >= 0 ? raw.substring(slash + 1) : raw;
        File dir = resolve(dirPart.length() == 0 ? "." : dirPart);
        File[] children = dir == null ? null : dir.listFiles();
        if (children == null) return Collections.emptyList();
        Pattern pattern = compileGlob(nameGlob, false);
        boolean dotPattern = nameGlob.startsWith(".");
        ArrayList<File> matches = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (name.startsWith(".") && !dotPattern && !includeDotHidden) continue;
            if (pattern.matcher(name).matches()) matches.add(child);
        }
        matches.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private CopyPlan buildCopyPlan(List<SourceSpec> sources, File destination, boolean recursive, boolean move, boolean destAsDirectory) {
        CopyPlan plan = new CopyPlan(move);
        if (destination == null) {
            plan.error = (move ? "mv" : "cp") + ": invalid destination";
            return plan;
        }
        boolean multiple = sources.size() > 1;
        for (SourceSpec source : sources) {
            if (move) plan.moveSources.add(source.file);
            if (source.file.isDirectory()) {
                if (!recursive && !source.copyContents) {
                    plan.error = "cp: omitting directory: " + source.file.getAbsolutePath();
                    return plan;
                }
                File baseDest = destinationForSource(source, destination, multiple);
                if (source.copyContents) {
                    addDirectoryContentsToPlan(plan, source.file, baseDest);
                } else if (destination.exists() && destination.isDirectory()) {
                    addDirectoryContentsToPlan(plan, source.file, new File(destination, source.file.getName()));
                } else {
                    addDirectoryContentsToPlan(plan, source.file, destination);
                }
            } else {
                if (destAsDirectory) plan.directories.add(destination);
                File target = (multiple || destination.isDirectory() || destAsDirectory) ? new File(destination, source.file.getName()) : destination;
                addFileToPlan(plan, source.file, target);
            }
        }
        for (CopyItem item : plan.items) {
            plan.totalBytes += Math.max(0, item.source.length());
            if (item.destination.exists()) plan.overwriteCount++;
        }
        return plan;
    }

    private File destinationForSource(SourceSpec source, File destination, boolean multiple) {
        if (source.copyContents) return destination;
        if (multiple || destination.isDirectory()) return new File(destination, source.file.getName());
        return destination;
    }

    private void addDirectoryContentsToPlan(CopyPlan plan, File sourceDir, File destDir) {
        plan.directories.add(destDir);
        File[] children = sourceDir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            File dest = new File(destDir, child.getName());
            if (child.isDirectory()) addDirectoryContentsToPlan(plan, child, dest);
            else addFileToPlan(plan, child, dest);
        }
    }

    private void addFileToPlan(CopyPlan plan, File source, File destination) {
        plan.items.add(new CopyItem(source, destination));
    }

    private DeletePlan buildDeletePlan(List<File> targets) {
        DeletePlan plan = new DeletePlan();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (File target : targets) {
            addDeleteTarget(plan, target, seen);
        }
        plan.totalItems = plan.items.size();
        return plan;
    }

    private void addDeleteTarget(DeletePlan plan, File target, Set<String> seen) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File child : children) addDeleteTarget(plan, child, seen);
            }
        }
        String key = pathKey(target);
        if (seen.add(key)) plan.items.add(target);
    }

    private void startCopyMoveOperation(CopyPlan plan) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        showOperationOverlay(plan.move ? "mv" : "cp", cancelled);
        new Thread(() -> {
            OperationResult result = new OperationResult();
            long copiedBytes = 0;
            String writeBlock = firstBlockedWriteTarget(plan);
            if (writeBlock != null) {
                result.failed = plan.items.size();
                result.failures.add(writeBlock + ": destination is not writable (EPERM). Android blocked file creation there.");
                finishOperationOverlay();
                OperationResult finalResult = result;
                mainHandler.post(() -> showTerminalPopup(plan.move ? "mv" : "cp", finalResult.summary(plan.move ? "moved files" : "copied files", "deleted files", "copied files")));
                return;
            }
            for (File dir : plan.directories) {
                if (cancelled.get()) break;
                if (dir.exists()) result.skipped++;
                else if (dir.mkdirs()) result.created++;
                else {
                    result.failed++;
                    result.failures.add(dir.getAbsolutePath() + ": could not create directory");
                }
            }
            for (int i = 0; i < plan.items.size(); i++) {
                if (cancelled.get()) break;
                CopyItem item = plan.items.get(i);
                updateOperationOverlay(item.source.getAbsolutePath(), i, plan.items.size(), copiedBytes, plan.totalBytes);
                try {
                    copiedBytes += copyFileChunked(item.source, item.destination, cancelled);
                    if (cancelled.get()) {
                        result.cancelled = true;
                        break;
                    }
                    result.copied++;
                } catch (Exception e) {
                    result.failed++;
                    result.failures.add(item.source.getAbsolutePath() + " -> " + item.destination.getAbsolutePath() + ": " + e.getMessage());
                }
            }
            if (plan.move && !result.cancelled && result.failed == 0 && result.copied == plan.items.size()) {
                DeletePlan deletePlan = buildDeletePlan(plan.moveSources);
                for (int i = 0; i < deletePlan.items.size(); i++) {
                    if (cancelled.get()) break;
                    File file = deletePlan.items.get(i);
                    updateOperationOverlay(file.getAbsolutePath(), i, deletePlan.items.size(), copiedBytes, plan.totalBytes);
                    if (file.delete()) result.deleted++;
                    else {
                        result.failed++;
                        result.failures.add(file.getAbsolutePath() + ": could not delete source");
                    }
                }
            }
            if (cancelled.get()) result.cancelled = true;
            finishOperationOverlay();
            OperationResult finalResult = result;
            mainHandler.post(() -> {
                renderListing();
                showTerminalPopup(plan.move ? "mv" : "cp", finalResult.summary(plan.move ? "moved files" : "copied files", "deleted files", "copied files"));
            });
        }, "retui-file-op").start();
    }

    private String firstBlockedWriteTarget(CopyPlan plan) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (CopyItem item : plan.items) {
            File parent = item.destination.getParentFile();
            if (parent == null) continue;
            String key = pathKey(parent);
            if (!seen.add(key)) continue;
            try {
                if (!parent.exists() && !parent.mkdirs()) return parent.getAbsolutePath();
                File probe = new File(parent, ".retui-write-test");
                try (OutputStream out = new FileOutputStream(probe, false)) {
                    out.write(1);
                }
                probe.delete();
            } catch (Exception e) {
                return parent.getAbsolutePath();
            }
        }
        return null;
    }

    private ZipPlan buildZipPlan(File archive, List<SourceSpec> sources, boolean recursive) {
        ZipPlan plan = new ZipPlan(archive);
        for (SourceSpec source : sources) {
            if (source.file.isDirectory()) {
                if (!recursive && !source.copyContents) {
                    plan.error = "zip: directory requires -r: " + source.file.getAbsolutePath();
                    return plan;
                }
                String base = source.copyContents ? "" : source.file.getName() + "/";
                addZipDirectory(plan, source.file, base);
            } else {
                addZipFile(plan, source.file, source.file.getName());
            }
        }
        return plan;
    }

    private void addZipDirectory(ZipPlan plan, File directory, String baseName) {
        if (baseName.length() > 0) plan.entries.add(new ZipItem(directory, baseName, true));
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (pathKey(child).equals(pathKey(plan.archive))) continue;
            String entry = baseName + child.getName();
            if (child.isDirectory()) addZipDirectory(plan, child, entry + "/");
            else addZipFile(plan, child, entry);
        }
    }

    private void addZipFile(ZipPlan plan, File file, String entryName) {
        if (pathKey(file).equals(pathKey(plan.archive))) return;
        plan.entries.add(new ZipItem(file, entryName, false));
        plan.totalBytes += Math.max(0, file.length());
    }

    private void startZipOperation(ZipPlan plan) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        showOperationOverlay("zip", cancelled);
        new Thread(() -> {
            int zipped = 0;
            int failed = 0;
            ArrayList<String> failures = new ArrayList<>();
            long zippedBytes = 0;
            File parent = plan.archive.getParentFile();
            File temp = parent == null ? new File(plan.archive.getAbsolutePath() + ".retui-zipping") : new File(parent, plan.archive.getName() + ".retui-zipping");
            try {
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new Exception("could not create archive directory");
                }
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temp))) {
                    byte[] buffer = new byte[COPY_BUFFER_SIZE];
                    for (int i = 0; i < plan.entries.size(); i++) {
                        if (cancelled.get()) break;
                        ZipItem item = plan.entries.get(i);
                        updateOperationOverlay(item.source.getAbsolutePath(), i, plan.entries.size(), zippedBytes, plan.totalBytes);
                        try {
                            ZipEntry entry = new ZipEntry(item.entryName);
                            entry.setTime(item.source.lastModified());
                            zip.putNextEntry(entry);
                            if (!item.directory) {
                                try (InputStream in = new FileInputStream(item.source)) {
                                    int read;
                                    while ((read = in.read(buffer)) >= 0) {
                                        if (cancelled.get()) break;
                                        zip.write(buffer, 0, read);
                                        zippedBytes += read;
                                    }
                                }
                            }
                            zip.closeEntry();
                            if (!cancelled.get() && !item.directory) zipped++;
                        } catch (Exception e) {
                            failed++;
                            failures.add(item.source.getAbsolutePath() + ": " + e.getMessage());
                        }
                    }
                }
                if (cancelled.get()) {
                    temp.delete();
                } else {
                    if (plan.archive.exists() && !plan.archive.delete()) throw new Exception("could not overwrite archive");
                    if (!temp.renameTo(plan.archive)) throw new Exception("could not finish archive");
                }
            } catch (Exception e) {
                failed++;
                failures.add(plan.archive.getAbsolutePath() + ": " + e.getMessage());
                temp.delete();
            }
            finishOperationOverlay();
            int finalZipped = zipped;
            int finalFailed = failed;
            boolean finalCancelled = cancelled.get();
            ArrayList<String> finalFailures = failures;
            mainHandler.post(() -> {
                renderListing();
                StringBuilder out = new StringBuilder();
                out.append("zipped files: ").append(finalZipped).append('\n');
                out.append("failed files: ").append(finalFailed);
                if (finalCancelled) out.append("\ncancelled");
                if (!finalFailures.isEmpty()) {
                    out.append("\nfailed paths:");
                    for (String failure : finalFailures) out.append('\n').append(failure);
                }
                showTerminalPopup("zip", out.toString());
            });
        }, "retui-zip-op").start();
    }

    private long copyFileChunked(File source, File destination, AtomicBoolean cancelled) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("could not create destination directory");
        }
        try {
            return copyFileViaTemp(source, destination, cancelled);
        } catch (Exception e) {
            if (isTempFileBlocked(e)) {
                return copyFileDirect(source, destination, cancelled);
            }
            throw e;
        }
    }

    private long copyFileViaTemp(File source, File destination, AtomicBoolean cancelled) throws Exception {
        File temp = new File(destination.getParentFile(), destination.getName() + ".retui-copying");
        long written = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(temp)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (cancelled.get()) {
                    out.close();
                    temp.delete();
                    return written;
                }
                out.write(buffer, 0, read);
                written += read;
            }
        }
        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new Exception("could not overwrite destination");
        }
        if (!temp.renameTo(destination)) {
            temp.delete();
            throw new Exception("could not finish copy");
        }
        destination.setLastModified(source.lastModified());
        return written;
    }

    private long copyFileDirect(File source, File destination, AtomicBoolean cancelled) throws Exception {
        long written = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(destination, false)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (cancelled.get()) {
                    out.close();
                    destination.delete();
                    return written;
                }
                out.write(buffer, 0, read);
                written += read;
            }
        }
        destination.setLastModified(source.lastModified());
        return written;
    }

    private boolean isTempFileBlocked(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("EPERM") || message.contains("EACCES"));
    }

    private void startDeleteOperation(DeletePlan plan) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        showOperationOverlay("rm", cancelled);
        new Thread(() -> {
            OperationResult result = new OperationResult();
            for (int i = 0; i < plan.items.size(); i++) {
                if (cancelled.get()) break;
                File item = plan.items.get(i);
                updateOperationOverlay(item.getAbsolutePath(), i, plan.items.size(), i, plan.items.size());
                if (item.delete()) result.deleted++;
                else {
                    result.failed++;
                    result.failures.add(item.getAbsolutePath() + ": could not delete");
                }
            }
            if (cancelled.get()) result.cancelled = true;
            finishOperationOverlay();
            OperationResult finalResult = result;
            mainHandler.post(() -> {
                renderListing();
                showTerminalPopup("rm", finalResult.summary("copied files", "deleted files", "copied files"));
            });
        }, "retui-delete-op").start();
    }

    private void confirmTerminal(String message, String title, Runnable onConfirm) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("YES", (d, which) -> onConfirm.run())
                .setNegativeButton("NO", (d, which) -> showTerminalPopup(title, title + ": skipped"))
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(panelDrawable(true));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(textColor);
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(textColor);
            TextView titleView = dialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
            if (titleView != null) titleView.setTextColor(textColor);
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(textColor);
                messageView.setTypeface(appTypeface);
            }
        });
        dialog.show();
    }

    private void showTerminalPopup(String title, CharSequence message) {
        TextView body = label(message == null ? "" : message.toString(), outputTextSizeSp, false);
        body.setPadding(dp(14), dp(10), dp(14), dp(10));
        body.setTextIsSelectable(true);
        body.setSingleLine(false);
        body.setTypeface(appTypeface);
        body.setTextColor(textColor);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        int maxHeight = Math.max(dp(180), getResources().getDisplayMetrics().heightPixels / 3);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, maxHeight));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("OK", (d, which) -> refocusInput())
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(panelDrawable(PanelRole.OUTPUT));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(textColor);
            TextView dialogTitle = dialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
            if (dialogTitle != null) {
                dialogTitle.setTextColor(textColor);
                dialogTitle.setTypeface(appTypeface, Typeface.BOLD);
                dialogTitle.setTextSize(headerTextSizeSp);
            }
        });
        dialog.show();
    }

    private void showOperationOverlay(String title, AtomicBoolean cancelled) {
        mainHandler.post(() -> {
            if (operationOverlay != null) stage.removeView(operationOverlay.container);
            operationOverlay = new OperationOverlay(title, cancelled);
            stage.addView(operationOverlay.container, operationOverlay.params);
        });
    }

    private void updateOperationOverlay(String current, int done, int total, long bytesDone, long bytesTotal) {
        mainHandler.post(() -> {
            if (operationOverlay != null) operationOverlay.update(current, done, total, bytesDone, bytesTotal);
        });
    }

    private void finishOperationOverlay() {
        mainHandler.post(() -> {
            if (operationOverlay != null) {
                stage.removeView(operationOverlay.container);
                operationOverlay = null;
            }
        });
    }

    private void updateSuggestions(String input) {
        if (suggestionsGroup == null) return;
        suggestionsGroup.removeAllViews();
        String trimmed = input == null ? "" : input.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (trimmed.length() == 0) {
            if (!hasStorageAccess()) {
                setSuggestionsVisible(true);
                addSuggestion("permission", "permission", true);
                return;
            }
            setSuggestionsVisible(false);
            return;
        }
        setSuggestionsVisible(true);

        String prefix = "";
        boolean dirsOnly = false;
        boolean filesOnly = false;
        String commandPrefix = trimmed.contains(" ") ? trimmed.substring(0, trimmed.indexOf(' ') + 1) : "";
        if (lower.startsWith("cd ")) {
            prefix = trimmed.substring(3).trim();
            dirsOnly = true;
        } else if (lower.startsWith("open ")) {
            prefix = trimmed.substring(5).trim();
            filesOnly = true;
        } else if (lower.startsWith("share ") || lower.startsWith("rm ")) {
            SuggestionTarget target = shellSuggestionTarget(trimmed);
            prefix = target.prefix;
            commandPrefix = target.commandPrefix;
        } else if (lower.startsWith("cp ") || lower.startsWith("mv ") || lower.startsWith("mkdir ") || lower.startsWith("zip ")) {
            SuggestionTarget target = shellSuggestionTarget(trimmed);
            prefix = target.prefix;
            commandPrefix = target.commandPrefix;
        }
        suggestChildren(prefix, dirsOnly, filesOnly, commandPrefix);
    }

    private void setSuggestionsVisible(boolean visible) {
        if (suggestionsScroll != null) {
            suggestionsScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void suggestChildren(String prefix, boolean dirsOnly, boolean filesOnly, String commandPrefix) {
        File[] children = currentDirectory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        int added = 0;
        for (File child : children) {
            if (added >= 24) return;
            if (dirsOnly && !child.isDirectory()) continue;
            if (filesOnly && !child.isFile()) continue;
            String name = child.getName();
            if (prefix.length() > 0 && !name.toLowerCase(Locale.US).contains(prefix.toLowerCase(Locale.US))) continue;
            boolean execute = !(commandPrefix.endsWith("open ") || commandPrefix.endsWith("share ")
                    || commandPrefix.startsWith("cp ") || commandPrefix.startsWith("mv ")
                    || commandPrefix.startsWith("rm ") || commandPrefix.startsWith("mkdir ")
                    || commandPrefix.startsWith("zip "));
            addSuggestion(name, commandPrefix + quoteIfNeeded(name), execute, false);
            added++;
        }
    }

    private int lastUnquotedSpace(String value) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = value.length() - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                return i;
            }
        }
        return -1;
    }

    private SuggestionTarget shellSuggestionTarget(String input) {
        int split = lastUnquotedSpace(input);
        String prefix = split >= 0 ? input.substring(split + 1).trim() : "";
        String commandPrefix = split >= 0 ? input.substring(0, split + 1) : input + " ";
        if (prefix.startsWith("-")) {
            return new SuggestionTarget("", input.endsWith(" ") ? input : input + " ");
        }
        return new SuggestionTarget(prefix, commandPrefix);
    }

    private void addSuggestion(String label, String value, boolean execute) {
        addSuggestion(label, value, execute, true);
    }

    private void addSuggestion(String label, String value, boolean execute, boolean commandChip) {
        TextView chip = label(label.toUpperCase(Locale.US), 12, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        styleChip(chip, commandChip);
        chip.setOnClickListener(v -> {
            inputView.setText(value);
            inputView.setSelection(inputView.getText().length());
            if (execute) runInput(value);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(28));
        lp.setMargins(0, dp(3), dp(8), dp(3));
        suggestionsGroup.addView(chip, lp);
    }

    private File resolve(String raw) {
        String value = unquote(raw == null ? "" : raw.trim());
        if (value.length() == 0) return currentDirectory;
        File file = value.startsWith(File.separator) ? new File(value) : new File(currentDirectory, value);
        try {
            return file.getCanonicalFile();
        } catch (Exception e) {
            return file.getAbsoluteFile();
        }
    }

    private File resolveStartDirectory(Intent intent) {
        String raw = intent == null ? null : intent.getStringExtra(EXTRA_PATH);
        File fallback = sharedStorageRoot();
        File start = raw == null || raw.length() == 0 ? fallback : new File(raw);
        if ("/storage/emulated".equals(start.getAbsolutePath())) {
            start = fallback;
        }
        return start.exists() && start.isDirectory() ? start : fallback;
    }

    private void handleIncomingCommand(Intent intent) {
        String command = intent == null ? null : intent.getStringExtra(EXTRA_COMMAND);
        if (!TextUtils.isEmpty(command)) execute(command);
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureStorageAccess() {
        if (hasStorageAccess()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openStorageAccessSettings();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 7);
        }
        Toast.makeText(this, "Grant storage access for Re:T-UI Files.", Toast.LENGTH_LONG).show();
    }

    private void openStorageAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 7);
        }
    }

    private File sharedStorageRoot() {
        File root = Environment.getExternalStorageDirectory();
        if (root != null && "/storage/emulated".equals(root.getAbsolutePath())) {
            File userRoot = new File(root, "0");
            if (userRoot.exists()) return userRoot;
        }
        File direct = new File("/storage/emulated/0");
        return direct.exists() ? direct : root;
    }

    private Uri uriFor(File file) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    private String mimeFor(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String type = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.US));
        return type == null ? "*/*" : type;
    }

    private void print(String text) {
        print((CharSequence) text);
    }

    private void print(CharSequence text) {
        setRailsVisible(false);
        if (outputView != null) outputView.setVisibility(View.VISIBLE);
        if (fileRowsView != null) {
            fileRowsView.removeAllViews();
            fileRowsView.setVisibility(View.GONE);
        }
        outputView.setText(text == null ? "" : text, TextView.BufferType.SPANNABLE);
        outputView.setMovementMethod(LinkMovementMethod.getInstance());
        if (outputScroll != null) outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_UP));
    }

    private void renderFileRows(List<TreeRow> rows, String report) {
        setRailsVisible(true);
        if (outputView != null) {
            outputView.setVisibility(TextUtils.isEmpty(report) ? View.GONE : View.VISIBLE);
            outputView.setText(report == null ? "" : report);
        }
        if (fileRowsView == null) return;
        fileRowsView.removeAllViews();
        fileRowsView.setVisibility(View.VISIBLE);
        for (TreeRow row : rows) {
            fileRowsView.addView(fileRowView(row), new LinearLayout.LayoutParams(-1, -2));
        }
        buildAlphaRail(rows);
        buildPinnedRail();
        if (outputScroll != null) outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_UP));
    }

    private void setRailsVisible(boolean visible) {
        if (alphaRailHost != null) alphaRailHost.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (pinnedRailHost != null) pinnedRailHost.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void buildAlphaRail(List<TreeRow> rows) {
        if (alphaRailView == null) return;
        visibleSections.clear();
        visibleSectionRows.clear();
        ArrayList<RailItem> items = new ArrayList<>();
        if (rows == null) {
            alphaRailView.setItems(items);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            TreeRow row = rows.get(i);
            if (row == null || row.file == null || row.file.equals(currentDirectory.getParentFile())) continue;
            String section = sectionForFile(row.file);
            if (!visibleSections.contains(section)) {
                visibleSections.add(section);
                visibleSectionRows.add(i);
            }
        }
        selectedSection = visibleSections.isEmpty() ? null : visibleSections.get(0);
        for (int i = 0; i < visibleSections.size(); i++) {
            String section = visibleSections.get(i);
            int rowIndex = visibleSectionRows.get(i);
            items.add(new RailItem(section, section.equals(selectedSection), 9.5f, () -> jumpToRow(rowIndex, section), null));
        }
        alphaRailView.setItems(items, true);
    }

    private void buildPinnedRail() {
        if (pinnedRailView == null) return;
        ArrayList<RailItem> items = new ArrayList<>();
        addPinnedTab(items, "HOME", sharedStorageRoot());
        addPinnedTab(items, "DL", new File(sharedStorageRoot(), "Download"));
        addPinnedTab(items, "RET", new File(sharedStorageRoot(), "Re-T-UI"));
        addPinnedTab(items, "R:T", new File(sharedStorageRoot(), "Re:T-UI"));
        addPinnedTab(items, "AND", new File(sharedStorageRoot(), "Android"));
        for (FavoritePath favorite : loadFavorites()) {
            File dir = new File(favorite.path);
            if (dir.exists() && dir.isDirectory()) addPinnedTab(items, favorite.label, dir, true);
        }
        addPinnedTab(items, "CUR", currentDirectory);
        pinnedRailView.setItems(items, false);
    }

    private void addPinnedTab(ArrayList<RailItem> items, String label, File directory) {
        addPinnedTab(items, label, directory, false);
    }

    private void addPinnedTab(ArrayList<RailItem> items, String label, File directory, boolean customFavorite) {
        if (items == null || directory == null || !directory.exists() || !directory.isDirectory()) return;
        float size = label.length() > 3 ? 7.5f : 8.5f;
        Runnable click = () -> changeDirectory(directory.getAbsolutePath());
        Runnable longClick = customFavorite ? () -> showFavoriteActions(label, directory) : null;
        items.add(new RailItem(label, pathKey(directory).equals(pathKey(currentDirectory)), size, click, longClick));
    }

    private void buildPinnedRailLegacy() {
        if (pinnedRail == null) return;
        pinnedRail.removeAllViews();
        addPinnedTab("HOME", sharedStorageRoot());
        addPinnedTab("DL", new File(sharedStorageRoot(), "Download"));
        addPinnedTab("RET", new File(sharedStorageRoot(), "Re-T-UI"));
        addPinnedTab("R:T", new File(sharedStorageRoot(), "Re:T-UI"));
        addPinnedTab("AND", new File(sharedStorageRoot(), "Android"));
        for (FavoritePath favorite : loadFavorites()) {
            File dir = new File(favorite.path);
            if (dir.exists() && dir.isDirectory()) addPinnedTab(favorite.label, dir, true);
        }
        addPinnedTab("CUR", currentDirectory);
    }

    private void addPinnedTab(String label, File directory) {
        addPinnedTab(label, directory, false);
    }

    private void addPinnedTab(String label, File directory, boolean customFavorite) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return;
        TextView tab = sideTab(label, label.length() > 3 ? 7.5f : 8.5f);
        styleRailTab(tab, pathKey(directory).equals(pathKey(currentDirectory)));
        tab.setOnClickListener(v -> changeDirectory(directory.getAbsolutePath()));
        if (customFavorite) {
            tab.setOnLongClickListener(v -> {
                showFavoriteActions(label, directory);
                return true;
            });
        }
        pinnedRail.addView(tab);
    }

    private void showFavoriteActions(String label, File directory) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("fav " + label)
                .setMessage(directory.getAbsolutePath())
                .setPositiveButton("OPEN", (d, which) -> changeDirectory(directory.getAbsolutePath()))
                .setNegativeButton("REMOVE", (d, which) -> removeFavorite(label))
                .setNeutralButton("RENAME", (d, which) -> seed("fav rename " + label + " "))
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(panelDrawable(PanelRole.OUTPUT));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(outputTextColor);
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(outputTextColor);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(outputTextColor);
            TextView title = dialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
            if (title != null) title.setTextColor(outputTextColor);
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(outputTextColor);
                message.setTypeface(appTypeface);
            }
        });
        dialog.show();
    }

    private TextView sideTab(String label, float sp) {
        TextView tab = label(label, Math.round(sp), true);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setPadding(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(34));
        lp.bottomMargin = dp(4);
        tab.setLayoutParams(lp);
        return tab;
    }

    private void styleRailTab(TextView tab, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(2));
        bg.setStroke(dp(1), moduleButtonBorderColor);
        bg.setColor(selected ? moduleButtonTextColor : modulePanelColor);
        tab.setBackground(bg);
        tab.setTextColor(selected ? modulePanelColor : moduleButtonTextColor);
    }

    private void jumpToRow(int rowIndex, String section) {
        if (fileRowsView == null || outputScroll == null || rowIndex < 0 || rowIndex >= fileRowsView.getChildCount()) return;
        selectedSection = section;
        buildAlphaRailFromCurrentRows();
        View child = fileRowsView.getChildAt(rowIndex);
        int target = outputView != null && outputView.getVisibility() == View.VISIBLE ? outputView.getHeight() : 0;
        target += fileRowsView.getTop() + child.getTop();
        outputScroll.smoothScrollTo(0, Math.max(0, target - dp(12)));
    }

    private void buildAlphaRailFromCurrentRows() {
        if (alphaRailView == null) return;
        ArrayList<RailItem> items = new ArrayList<>();
        for (int i = 0; i < visibleSections.size(); i++) {
            String section = visibleSections.get(i);
            int rowIndex = visibleSectionRows.get(i);
            items.add(new RailItem(section, section.equals(selectedSection), 9.5f, () -> jumpToRow(rowIndex, section), null));
        }
        alphaRailView.setItems(items, false);
    }

    private String sectionForFile(File file) {
        String name = file == null ? "" : file.getName();
        if (name.length() == 0) return "#";
        char first = Character.toUpperCase(name.charAt(0));
        if (first < 'A' || first > 'Z') return "#";
        return String.valueOf(first);
    }

    private ArrayList<FavoritePath> loadFavorites() {
        ArrayList<FavoritePath> favorites = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(PREF_FAVORITES, "");
        if (TextUtils.isEmpty(raw)) return favorites;
        String[] lines = raw.split("\n");
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab >= line.length() - 1) continue;
            String label = normalizeFavoriteLabel(line.substring(0, tab));
            String path = line.substring(tab + 1);
            if (label.length() > 0 && path.length() > 0) favorites.add(new FavoritePath(label, path));
        }
        return favorites;
    }

    private void saveFavorites(List<FavoritePath> favorites) {
        StringBuilder out = new StringBuilder();
        if (favorites != null) {
            for (FavoritePath favorite : favorites) {
                if (favorite == null || TextUtils.isEmpty(favorite.label) || TextUtils.isEmpty(favorite.path)) continue;
                out.append(normalizeFavoriteLabel(favorite.label)).append('\t').append(favorite.path).append('\n');
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_FAVORITES, out.toString()).apply();
    }

    private void upsertFavorite(ArrayList<FavoritePath> favorites, FavoritePath incoming) {
        for (FavoritePath favorite : favorites) {
            if (favorite.label.equalsIgnoreCase(incoming.label)) {
                favorite.path = incoming.path;
                return;
            }
        }
        favorites.add(incoming);
    }

    private String favoriteListText() {
        ArrayList<FavoritePath> favorites = loadFavorites();
        if (favorites.isEmpty()) return "No custom favorites.\nUse: fav add [label] [path]";
        StringBuilder out = new StringBuilder("favorites:");
        for (FavoritePath favorite : favorites) {
            out.append('\n').append(favorite.label).append(" -> ").append(favorite.path);
        }
        return out.toString();
    }

    private String normalizeFavoriteLabel(String raw) {
        if (raw == null) return "";
        String upper = raw.trim().toUpperCase(Locale.US);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < upper.length() && out.length() < 4; i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ':' || c == '_') out.append(c);
        }
        return out.toString();
    }

    private String autoFavoriteLabel(File directory, List<FavoritePath> existing) {
        String base = normalizeFavoriteLabel(directory == null ? "FAV" : directory.getName());
        if (base.length() == 0) base = "FAV";
        if (isFavoriteLabelFree(base, existing)) return base;
        String stem = base.length() > 3 ? base.substring(0, 3) : base;
        for (int i = 2; i < 10; i++) {
            String candidate = normalizeFavoriteLabel(stem + i);
            if (isFavoriteLabelFree(candidate, existing)) return candidate;
        }
        return normalizeFavoriteLabel(stem + "X");
    }

    private boolean isFavoriteLabelFree(String label, List<FavoritePath> existing) {
        if (existing == null) return true;
        for (FavoritePath favorite : existing) {
            if (favorite.label.equalsIgnoreCase(label)) return false;
        }
        return true;
    }

    private View fileRowView(TreeRow row) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(1), 0, dp(1));
        line.setOnClickListener(v -> handleRowClick(row));

        int size = row.directory ? directoryRowSizeSp() : fileRowSizeSp();
        TextView prefix = label(row.prefix, size, false);
        prefix.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        prefix.setIncludeFontPadding(false);
        line.addView(prefix, new LinearLayout.LayoutParams(-2, -2));

        if (!TextUtils.isEmpty(row.icon)) {
            TextView icon = label(row.icon, size + 1, false);
            icon.setTypeface(iconTypeface, Typeface.NORMAL);
            icon.setGravity(Gravity.CENTER);
            icon.setIncludeFontPadding(false);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), -2);
            iconParams.rightMargin = dp(4);
            line.addView(icon, iconParams);
        }

        TextView name = label(row.name, size, row.directory);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setSingleLine(false);
        name.setIncludeFontPadding(false);
        if (row.directory && row.hasChildren) {
            name.setPaintFlags(name.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        return line;
    }

    private int fileRowSizeSp() {
        return clamp(inputFontSizeSp + 1, 13, 20);
    }

    private int directoryRowSizeSp() {
        return clamp(inputFontSizeSp + 2, 14, 22);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void handleRowClick(TreeRow row) {
        if (row == null || row.file == null) return;
        if (row.directory) {
            if (row.file.equals(currentDirectory.getParentFile())) {
                changeDirectory("..");
            } else {
                toggleDirectory(row.file);
            }
        } else {
            openFile(row.file);
        }
    }

    private void seed(String value) {
        inputView.setText(value);
        inputView.setSelection(inputView.getText().length());
        inputView.requestFocus();
    }

    private void refocusInput() {
        if (inputView == null) return;
        inputView.postDelayed(() -> {
            inputView.requestFocus();
            inputView.setSelection(inputView.getText().length());
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
        }, 40);
    }

    private void addTool(LinearLayout parent, String label, View.OnClickListener listener) {
        TextView view = label(label, 12, true);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(listener);
        parent.addView(view, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(sp);
        view.setTypeface(appTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private void stylePanel(View view) {
        stylePanel(view, false);
    }

    private void stylePanel(View view, boolean translucent) {
        view.setBackground(panelDrawable(translucent ? PanelRole.OUTPUT : PanelRole.MODULE));
    }

    private void stylePanel(View view, PanelRole role) {
        view.setBackground(panelDrawable(role));
    }

    private GradientDrawable panelDrawable(boolean translucent) {
        return panelDrawable(translucent ? PanelRole.OUTPUT : PanelRole.MODULE);
    }

    private GradientDrawable panelDrawable(PanelRole role) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        int baseFill = modulePanelColor;
        int stroke = moduleBorderColor;
        if (role == PanelRole.OUTPUT) {
            baseFill = outputPanelColor;
            stroke = outputBorderColor;
        } else if (role == PanelRole.HEADER) {
            baseFill = headerPanelColor;
            stroke = moduleBorderColor;
        } else if (role == PanelRole.INPUT) {
            baseFill = inputBgColor;
            stroke = moduleBorderColor;
        }
        int fill = role == PanelRole.OUTPUT
                ? Color.argb(250, Color.red(baseFill), Color.green(baseFill), Color.blue(baseFill))
                : baseFill;
        bg.setColor(fill);
        bg.setStroke(dp(1), stroke);
        int radius = moduleCornerRadiusDp;
        if (role == PanelRole.OUTPUT) radius = outputCornerRadiusDp;
        else if (role == PanelRole.HEADER) radius = headerCornerRadiusDp;
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private void styleChip(TextView view, boolean commandChip) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(commandChip ? moduleButtonBgColor : modulePanelColor);
        bg.setStroke(dp(1), moduleButtonBorderColor);
        view.setBackground(bg);
        view.setTextColor(moduleButtonTextColor);
    }

    private void applyIntentTheme(Intent intent) {
        if (intent == null) return;
        bgColor = intent.getIntExtra(EXTRA_THEME_BG, bgColor);
        panelColor = intent.getIntExtra(EXTRA_TERMINAL_BG, panelColor);
        textColor = intent.getIntExtra(EXTRA_THEME_TEXT, textColor);
        borderColor = intent.getIntExtra(EXTRA_THEME_BORDER, borderColor);
        modulePanelColor = intent.getIntExtra(EXTRA_MODULE_BG_COLOR, panelColor);
        moduleTextColor = intent.getIntExtra(EXTRA_MODULE_TEXT_COLOR, textColor);
        moduleBorderColor = intent.getIntExtra(EXTRA_MODULE_BORDER_COLOR, borderColor);
        headerPanelColor = intent.getIntExtra(EXTRA_MODULE_HEADER_BG_COLOR, modulePanelColor);
        headerTextColor = intent.getIntExtra(EXTRA_MODULE_HEADER_TEXT_COLOR, moduleTextColor);
        moduleButtonBgColor = intent.getIntExtra(EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor);
        moduleButtonTextColor = intent.getIntExtra(EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleTextColor);
        moduleButtonBorderColor = intent.getIntExtra(EXTRA_MODULE_BUTTON_BORDER_COLOR, moduleBorderColor);
        inputBgColor = intent.getIntExtra(EXTRA_INPUT_BG_COLOR, inputBgColor);
        inputTextColor = intent.getIntExtra(EXTRA_INPUT_TEXT_COLOR, textColor);
        outputPanelColor = intent.getIntExtra(EXTRA_OUTPUT_BG_COLOR, panelColor);
        outputTextColor = intent.getIntExtra(EXTRA_OUTPUT_TEXT_COLOR, textColor);
        outputBorderColor = intent.getIntExtra(EXTRA_OUTPUT_BORDER_COLOR, moduleBorderColor);
        textColor = moduleTextColor;
        borderColor = moduleBorderColor;
        topMarginDp = intent.getIntExtra(EXTRA_TOP_MARGIN, topMarginDp);
        inputFontSizeSp = intent.getIntExtra(EXTRA_INPUT_FONT_SIZE, inputFontSizeSp);
        headerTextSizeSp = readIntExtra(intent, headerTextSizeSp, EXTRA_HEADER_TEXT_SIZE, EXTRA_MODULE_HEADER_TEXT_SIZE, "header_font_size");
        outputHeaderTextSizeSp = readIntExtra(intent, outputHeaderTextSizeSp, EXTRA_OUTPUT_HEADER_TEXT_SIZE, "output_header_text_size_sp");
        outputTextSizeSp = readIntExtra(intent, outputTextSizeSp, EXTRA_OUTPUT_TEXT_SIZE, "module_output_text_size", "output_font_size");
        moduleCornerRadiusDp = readIntExtra(intent, moduleCornerRadiusDp, EXTRA_MODULE_CORNER_RADIUS, "module_radius", "corner_radius", "corner_radius_dp");
        outputCornerRadiusDp = readIntExtra(intent, outputCornerRadiusDp, EXTRA_OUTPUT_CORNER_RADIUS, "output_radius", "output_corner_radius_dp", EXTRA_MODULE_CORNER_RADIUS);
        headerCornerRadiusDp = readIntExtra(intent, headerCornerRadiusDp, EXTRA_HEADER_CORNER_RADIUS, "header_radius", "header_corner_radius_dp", EXTRA_MODULE_CORNER_RADIUS);
        terminalBackgroundImage = readStringExtra(intent, EXTRA_TERMINAL_BG_IMAGE, "terminal_bg_path", "terminal_background", "terminal_background_image", "wallpaper_path");
        displayMarginsMm = parseDisplayMargins(intent.getStringExtra(EXTRA_DISPLAY_MARGIN_MM));
        appTypeface = resolveTypeface(intent);
        iconTypeface = resolveIconTypeface();
    }

    private int readIntExtra(Intent intent, int fallback, String... keys) {
        if (intent == null || keys == null) return fallback;
        for (String key : keys) {
            if (key != null && intent.hasExtra(key)) return intent.getIntExtra(key, fallback);
        }
        return fallback;
    }

    private String readStringExtra(Intent intent, String... keys) {
        if (intent == null || keys == null) return null;
        for (String key : keys) {
            String value = key == null ? null : intent.getStringExtra(key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return null;
    }

    private Typeface resolveTypeface(Intent intent) {
        String path = intent.getStringExtra(EXTRA_FONT_PATH);
        if (!TextUtils.isEmpty(path)) {
            try {
                File font = new File(path);
                if (font.exists() && font.length() > 0) {
                    return Typeface.createFromFile(font);
                }
            } catch (Exception ignored) {
            }
        }
        String name = intent.getStringExtra(EXTRA_FONT_NAME);
        if ("system".equalsIgnoreCase(name)) {
            return Typeface.DEFAULT;
        }
        try {
            return Typeface.createFromAsset(getAssets(), "lucida_console.ttf");
        } catch (Exception ignored) {
            return Typeface.MONOSPACE;
        }
    }

    private Typeface resolveIconTypeface() {
        try {
            return Typeface.createFromAsset(getAssets(), "symbols_nerd_font_mono.ttf");
        } catch (Exception ignored) {
            return appTypeface;
        }
    }

    private void applyWindowMargins() {
        if (rootLayoutParams == null || root == null) return;
        int top = dp(Math.max(0, topMarginDp));
        if (rootLayoutParams.topMargin != top) {
            rootLayoutParams.topMargin = top;
            root.setLayoutParams(rootLayoutParams);
        }
    }

    private void applyStagePadding() {
        if (stage == null) return;
        stage.setPadding(
                dp(22) + mmToPx(displayMarginsMm[0]),
                dp(22) + mmToPx(displayMarginsMm[1]),
                dp(22) + mmToPx(displayMarginsMm[2]),
                dp(4) + mmToPx(displayMarginsMm[3])
        );
    }

    private int[] parseDisplayMargins(String raw) {
        int[] margins = new int[]{0, 0, 0, 0};
        if (TextUtils.isEmpty(raw)) return margins;
        String[] parts = raw.split(",");
        for (int i = 0; i < margins.length && i < parts.length; i++) {
            try {
                margins[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            } catch (Exception ignored) {
                margins[i] = 0;
            }
        }
        return margins;
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void applyWallpaperBackground() {
        if (stage == null) return;
        if (!TextUtils.isEmpty(terminalBackgroundImage)) {
            try {
                Drawable provided = Drawable.createFromPath(terminalBackgroundImage);
                if (provided != null) {
                    stage.setBackground(provided);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        stage.setBackgroundColor(Color.TRANSPARENT);
    }

    private String quoteIfNeeded(String value) {
        return value.contains(" ") ? "\"" + value.replace("\"", "\\\"") + "\"" : value;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"");
        }
        return value;
    }

    private String pathKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private int mmToPx(int mm) {
        return (int) (mm * getResources().getDisplayMetrics().xdpi / 25.4f + 0.5f);
    }

    private static final class TreeOptions {
        boolean allFiles;
        boolean dirsOnly;
        boolean fullPath;
        boolean humanSize;
        boolean byteSize;
        boolean date;
        boolean typeSuffix;
        boolean noIndentLines;
        boolean reverse;
        boolean dirsFirst = true;
        boolean ignoreCase;
        boolean matchDirs;
        boolean noReport;
        int maxDepth;
        String sort = "name";
        String error;
        Pattern includePattern;
        Pattern excludePattern;

        static TreeOptions defaultListing() {
            TreeOptions options = new TreeOptions();
            options.maxDepth = LIST_TREE_DEPTH;
            options.noReport = true;
            return options;
        }

        static TreeOptions expanded() {
            TreeOptions options = new TreeOptions();
            options.maxDepth = EXPANDED_TREE_DEPTH;
            return options;
        }

        static TreeOptions error(String message) {
            TreeOptions options = new TreeOptions();
            options.error = message;
            return options;
        }
    }

    private static final class TreeStats {
        int directories;
        int files;
    }

    private static final class TreeRow {
        final String prefix;
        final String icon;
        final String name;
        final File file;
        final boolean directory;
        final boolean expanded;
        final boolean hasChildren;

        TreeRow(String prefix, String icon, String name, File file, boolean directory, boolean expanded, boolean hasChildren) {
            this.prefix = prefix;
            this.icon = icon;
            this.name = name;
            this.file = file;
            this.directory = directory;
            this.expanded = expanded;
            this.hasChildren = hasChildren;
        }
    }

    private enum PanelRole {
        MODULE,
        OUTPUT,
        HEADER,
        INPUT
    }

    private static final class SuggestionTarget {
        final String prefix;
        final String commandPrefix;

        SuggestionTarget(String prefix, String commandPrefix) {
            this.prefix = prefix;
            this.commandPrefix = commandPrefix;
        }
    }

    private static final class FavoritePath {
        String label;
        String path;

        FavoritePath(String label, String path) {
            this.label = label;
            this.path = path;
        }
    }

    private static final class SourceSpec {
        final File file;
        final boolean copyContents;

        SourceSpec(File file, boolean copyContents) {
            this.file = file;
            this.copyContents = copyContents;
        }
    }

    private static final class CopyItem {
        final File source;
        final File destination;

        CopyItem(File source, File destination) {
            this.source = source;
            this.destination = destination;
        }
    }

    private static final class CopyPlan {
        final boolean move;
        final ArrayList<File> directories = new ArrayList<>();
        final ArrayList<CopyItem> items = new ArrayList<>();
        final ArrayList<File> moveSources = new ArrayList<>();
        long totalBytes;
        int overwriteCount;
        String error;

        CopyPlan(boolean move) {
            this.move = move;
        }
    }

    private static final class DeletePlan {
        final ArrayList<File> items = new ArrayList<>();
        int totalItems;
    }

    private static final class ZipPlan {
        final File archive;
        final ArrayList<ZipItem> entries = new ArrayList<>();
        long totalBytes;
        String error;

        ZipPlan(File archive) {
            this.archive = archive;
        }
    }

    private static final class ZipItem {
        final File source;
        final String entryName;
        final boolean directory;

        ZipItem(File source, String entryName, boolean directory) {
            this.source = source;
            this.entryName = entryName;
            this.directory = directory;
        }
    }

    private static final class OperationResult {
        int copied;
        int deleted;
        int created;
        int skipped;
        int failed;
        boolean cancelled;
        final ArrayList<String> failures = new ArrayList<>();

        String summary(String primaryLabel, String deleteLabel, String copyLabel) {
            StringBuilder out = new StringBuilder();
            if (created > 0) out.append("created ").append(created).append(" directories\n");
            if (copied > 0 || primaryLabel.startsWith("copied") || primaryLabel.startsWith("moved")) {
                out.append(primaryLabel).append(": ").append(copied).append('\n');
            } else {
                out.append(copyLabel).append(": ").append(copied).append('\n');
            }
            out.append(deleteLabel).append(": ").append(deleted).append('\n');
            out.append("skipped files: ").append(skipped).append('\n');
            out.append("failed files: ").append(failed);
            if (cancelled) out.append("\ncancelled");
            if (!failures.isEmpty()) {
                out.append("\nfailed paths:");
                for (String failure : failures) out.append('\n').append(failure);
            }
            return out.toString();
        }
    }

    private static final class RailItem {
        final String label;
        final boolean selected;
        final float textSizeSp;
        final Runnable click;
        final Runnable longClick;

        RailItem(String label, boolean selected, float textSizeSp, Runnable click, Runnable longClick) {
            this.label = label;
            this.selected = selected;
            this.textSizeSp = textSizeSp;
            this.click = click;
            this.longClick = longClick;
        }
    }

    private final class SideRailView extends View {
        private final ArrayList<RailItem> items = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float scrollOffset;
        private float downY;
        private float startScroll;
        private boolean dragging;
        private boolean longPressed;
        private Runnable pendingLongPress;

        SideRailView(MainActivity context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
        }

        void setItems(List<RailItem> next) {
            setItems(next, false);
        }

        void setItems(List<RailItem> next, boolean resetScroll) {
            items.clear();
            if (next != null) items.addAll(next);
            if (resetScroll) scrollOffset = 0;
            scrollOffset = clampScroll(scrollOffset);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            float y = -scrollOffset;
            for (RailItem item : items) {
                rect.set(0, y, getWidth(), y + railItemHeight());
                if (rect.bottom >= 0 && rect.top <= getHeight()) drawItem(canvas, item, rect);
                y += railItemStride();
            }
            canvas.restore();
        }

        private void drawItem(Canvas canvas, RailItem item, RectF bounds) {
            float radius = dp(2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(item.selected ? moduleButtonTextColor : modulePanelColor);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(moduleButtonBorderColor);
            canvas.drawRoundRect(bounds, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(0);
            paint.setTypeface(appTypeface);
            paint.setTextSize(fitTextSize(item.label, item.textSizeSp, bounds.width() - dp(6)));
            paint.setColor(item.selected ? modulePanelColor : moduleButtonTextColor);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = bounds.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(item.label, bounds.centerX(), textY, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (items.isEmpty()) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getY();
                    startScroll = scrollOffset;
                    dragging = false;
                    longPressed = false;
                    pendingLongPress = () -> {
                        if (!dragging) {
                            RailItem item = itemAt(downY);
                            if (item != null && item.longClick != null) {
                                longPressed = true;
                                item.longClick.run();
                            }
                        }
                    };
                    mainHandler.postDelayed(pendingLongPress, 450);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = event.getY() - downY;
                    if (Math.abs(dy) > dp(4)) {
                        dragging = true;
                        if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress);
                    }
                    scrollOffset = clampScroll(startScroll - dy);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress);
                    pendingLongPress = null;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress);
                    pendingLongPress = null;
                    if (!dragging && !longPressed) {
                        RailItem item = itemAt(event.getY());
                        if (item != null && item.click != null) {
                            performClick();
                            item.click.run();
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private RailItem itemAt(float y) {
            int index = (int) ((y + scrollOffset) / railItemStride());
            if (index < 0 || index >= items.size()) return null;
            float itemTop = index * railItemStride() - scrollOffset;
            if (y > itemTop + railItemHeight()) return null;
            return items.get(index);
        }

        private float clampScroll(float value) {
            float max = Math.max(0, items.size() * railItemStride() - dp(4) - getHeight());
            return Math.max(0, Math.min(value, max));
        }

        private float railItemHeight() {
            return dp(34);
        }

        private float railItemStride() {
            return dp(38);
        }

        private float fitTextSize(String label, float desiredSp, float maxWidth) {
            float size = sp(desiredSp);
            paint.setTextSize(size);
            while (size > sp(6f) && paint.measureText(label) > maxWidth) {
                size -= sp(0.5f);
                paint.setTextSize(size);
            }
            return size;
        }
    }

    private final class OperationOverlay {
        final LinearLayout container;
        final FrameLayout.LayoutParams params;
        final TextView title;
        final TextView bar;
        final TextView current;
        final TextView counts;
        final TextView cancel;

        OperationOverlay(String name, AtomicBoolean cancelled) {
            container = new LinearLayout(MainActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(12), dp(10), dp(12), dp(10));
            stylePanel(container, true);

            title = label(name + " running", headerTextSizeSp, true);
            title.setGravity(Gravity.CENTER);
            container.addView(title, new LinearLayout.LayoutParams(-1, -2));

            bar = label("[--------------------]", outputTextSizeSp, false);
            bar.setGravity(Gravity.CENTER);
            container.addView(bar, new LinearLayout.LayoutParams(-1, -2));

            current = label("", Math.max(10, outputTextSizeSp - 1), false);
            current.setSingleLine(true);
            current.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            container.addView(current, new LinearLayout.LayoutParams(-1, -2));

            counts = label("", Math.max(10, outputTextSizeSp - 1), false);
            counts.setGravity(Gravity.CENTER);
            container.addView(counts, new LinearLayout.LayoutParams(-1, -2));

            cancel = label("CANCEL", 12, true);
            cancel.setGravity(Gravity.CENTER);
            cancel.setPadding(0, dp(4), 0, dp(4));
            styleChip(cancel, true);
            cancel.setOnClickListener(v -> {
                cancelled.set(true);
                title.setText(name + " cancelling");
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(30));
            cancelParams.topMargin = dp(8);
            container.addView(cancel, cancelParams);

            params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
            params.leftMargin = dp(24);
            params.rightMargin = dp(24);
            update("", 0, 0, 0, 0);
        }

        void update(String path, int done, int total, long bytesDone, long bytesTotal) {
            current.setText(path == null ? "" : path);
            int width = 20;
            int filled = total > 0 ? Math.min(width, Math.max(0, (int) ((done * 1L * width) / total))) : 0;
            StringBuilder line = new StringBuilder("[");
            for (int i = 0; i < width; i++) line.append(i < filled ? '#' : '-');
            line.append(']');
            bar.setText(line.toString());
            String itemText = total > 0 ? done + "/" + total : "?";
            String byteText = bytesTotal > 0 ? humanSize(bytesDone) + "/" + humanSize(bytesTotal) : "";
            counts.setText(byteText.length() > 0 ? itemText + "  " + byteText : itemText);
        }
    }

    private final class FileClickSpan extends ClickableSpan {
        private final File file;
        private final boolean directory;

        FileClickSpan(File file, boolean directory) {
            this.file = file;
            this.directory = directory;
        }

        @Override
        public void onClick(View widget) {
            if (file == null) return;
            if (directory) {
                if (file.equals(currentDirectory.getParentFile())) {
                    changeDirectory("..");
                } else {
                    toggleDirectory(file);
                }
            } else {
                openFile(file);
            }
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(textColor);
            ds.setUnderlineText(false);
            ds.setFakeBoldText(directory);
        }
    }
}
