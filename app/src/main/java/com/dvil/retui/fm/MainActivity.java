package com.dvil.retui.fm;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    public static final String ACTION_OPEN_CONSOLE = "com.dvil.retui.fm.OPEN_CONSOLE";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_THEME_BG = "theme_bg";
    public static final String EXTRA_THEME_TEXT = "theme_text";
    public static final String EXTRA_THEME_BORDER = "theme_border";
    public static final String EXTRA_TERMINAL_BG = "terminal_bg";
    public static final String EXTRA_TOP_MARGIN = "top_margin";
    public static final String EXTRA_INPUT_FONT_SIZE = "input_font_size";
    public static final String EXTRA_DISPLAY_MARGIN_MM = "display_margin_mm";
    public static final String EXTRA_FONT_PATH = "font_path";
    public static final String EXTRA_FONT_NAME = "font_name";

    private static final int LIST_TREE_DEPTH = 0;
    private static final int EXPANDED_TREE_DEPTH = 2;
    private static final int TREE_MAX_ITEMS = 320;
    private static final String ICON_FOLDER = "\uea83";
    private static final String ICON_FOLDER_OPEN = "\ueaf7";
    private static final String ICON_IMAGE = "\uf1c5";
    private static final String ICON_DOCUMENT = "\udb82\uddee";
    private static final String ICON_CODE = "\udb81\uddc0";
    private static final String ICON_FILE = "\uf15b";

    private int bgColor = Color.rgb(38, 40, 40);
    private int panelColor = Color.rgb(48, 50, 50);
    private int textColor = Color.rgb(195, 139, 150);
    private int borderColor = Color.rgb(103, 64, 71);
    private int topMarginDp = 18;
    private int inputFontSizeSp = 14;
    private int[] displayMarginsMm = new int[]{0, 0, 0, 0};
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
    private TextView inputPrefixView;
    private EditText inputView;
    private HorizontalScrollView suggestionsScroll;
    private LinearLayout suggestionsGroup;
    private ScrollView outputScroll;
    private final ArrayList<String> history = new ArrayList<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private TreeOptions activeTreeOptions = TreeOptions.defaultListing();
    private int historyIndex = -1;

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
        contentFrame.setOrientation(LinearLayout.VERTICAL);
        contentFrame.setPadding(0, 0, 0, 0);
        RelativeLayout.LayoutParams contentParams = new RelativeLayout.LayoutParams(-1, -1);
        contentParams.addRule(RelativeLayout.ABOVE, bottomDock.getId());
        root.addView(contentFrame, contentParams);

        titleView = label("FILES", 14, true);
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

        pathView = label("", 12, true);
        pathView.setSingleLine(true);
        contentFrame.addView(pathView, new LinearLayout.LayoutParams(-1, -2));

        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputContainer = new LinearLayout(this);
        outputContainer.setOrientation(LinearLayout.VERTICAL);
        outputView = label("", 13, false);
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

    private void styleUi() {
        if (root == null) return;
        applyWallpaperBackground();
        applyWindowMargins();
        stylePanel(root, false);
        if (contentFrame != null) {
            contentFrame.setBackgroundColor(Color.TRANSPARENT);
        }
        if (titleView != null) {
            titleView.setTextColor(textColor);
            stylePanel(titleView, false);
        }
        if (closeView != null) {
            closeView.setTextColor(textColor);
            stylePanel(closeView, false);
        }
        if (pathView != null) {
            pathView.setTextColor(textColor);
            pathView.setTextSize(fileRowSizeSp());
        }
        if (outputView != null) outputView.setTextColor(textColor);
        if (inputView != null) {
            inputView.setTextColor(textColor);
            inputView.setHintTextColor(Color.argb(150, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
            inputView.setTypeface(appTypeface);
            inputView.setTextSize(inputFontSizeSp);
            inputView.setGravity(Gravity.CENTER_VERTICAL);
            inputView.setIncludeFontPadding(false);
            stylePanel(inputGroup, false);
        }
        if (inputPrefixView != null) {
            inputPrefixView.setTextColor(textColor);
            inputPrefixView.setTypeface(appTypeface, Typeface.BOLD);
            inputPrefixView.setTextSize(inputFontSizeSp);
            inputPrefixView.setGravity(Gravity.CENTER);
            inputPrefixView.setIncludeFontPadding(false);
        }
        if (toolsView != null) toolsView.setBackgroundColor(Color.TRANSPARENT);
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
        inputView.setText("");
        if (command.length() == 0) return;
        history.add(command);
        historyIndex = history.size();
        execute(command);
        refocusInput();
    }

    private void execute(String command) {
        String lower = command.toLowerCase(Locale.US);
        if ("help".equals(lower)) {
            print("Commands:\ncd [folder]\ncd ..\nls\npwd\ntree [-a -d -f -h -s -D -F -i -r --dirsfirst --ignore-case --noreport -L n -P pattern -I pattern --sort name|size|mtime]\nopen [file]\nshare [file]\nmkdir [folder]\nrm [file]\npermission\nrefresh\nexit");
        } else if ("exit".equals(lower) || "close".equals(lower)) {
            finish();
        } else if ("permission".equals(lower) || "permissions".equals(lower) || "permit".equals(lower)) {
            openStorageAccessSettings();
        } else if ("tree".equals(lower) || lower.startsWith("tree ")) {
            renderTree(command);
        } else if ("ls".equals(lower) || "refresh".equals(lower)) {
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
            makeDirectory(command.substring(6).trim());
        } else if (lower.startsWith("rm ")) {
            remove(command.substring(3).trim());
        } else {
            print("Command not found: " + command + "\nType help.");
        }
    }

    private void changeDirectory(String target) {
        File dir = resolve(target);
        if (dir == null || !dir.exists()) {
            print("Not found: " + target);
            return;
        }
        if (!dir.isDirectory()) {
            print("Not a directory: " + target);
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
            print("Storage access required.\nType: permission\nOr grant All files access in Android settings.");
            return;
        }
        File[] children = currentDirectory.listFiles();
        if (children == null) {
            print("Cannot read: " + currentDirectory.getAbsolutePath());
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
            print(options.error);
            return;
        }
        renderListing(options);
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
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
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
            print("Not found: " + target);
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
            print("No app can open: " + file.getName());
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
            print("Not a file: " + target);
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
        if (dir != null && dir.mkdirs()) renderListing();
        else print("Could not create: " + name);
    }

    private void remove(String target) {
        File file = resolve(target);
        if (file == null || !file.exists()) {
            print("Not found: " + target);
            return;
        }
        if (file.isDirectory()) {
            print("Refusing directory delete for now: " + file.getName());
            return;
        }
        if (file.delete()) renderListing();
        else print("Could not delete: " + file.getName());
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
        if (lower.startsWith("cd ")) {
            prefix = trimmed.substring(3).trim();
            dirsOnly = true;
        } else if (lower.startsWith("open ")) {
            prefix = trimmed.substring(5).trim();
            filesOnly = true;
        } else if (lower.startsWith("share ") || lower.startsWith("rm ")) {
            prefix = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
            filesOnly = true;
        }
        suggestChildren(prefix, dirsOnly, filesOnly, trimmed.contains(" ") ? trimmed.substring(0, trimmed.indexOf(' ') + 1) : "");
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
            addSuggestion(name, commandPrefix + quoteIfNeeded(name), !commandPrefix.endsWith("open ") && !commandPrefix.endsWith("share "), false);
            added++;
        }
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
        if (outputScroll != null) outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_UP));
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
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        int fill = translucent
                ? Color.argb(250, Color.red(panelColor), Color.green(panelColor), Color.blue(panelColor))
                : panelColor;
        bg.setColor(fill);
        bg.setStroke(dp(1), borderColor);
        view.setBackground(bg);
    }

    private void styleChip(TextView view, boolean commandChip) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(commandChip ? Color.rgb(103, 64, 83) : Color.rgb(150, 130, 134));
        bg.setStroke(dp(1), borderColor);
        view.setBackground(bg);
        view.setTextColor(commandChip ? textColor : Color.WHITE);
    }

    private void applyIntentTheme(Intent intent) {
        if (intent == null) return;
        bgColor = intent.getIntExtra(EXTRA_THEME_BG, bgColor);
        panelColor = intent.getIntExtra(EXTRA_TERMINAL_BG, panelColor);
        textColor = intent.getIntExtra(EXTRA_THEME_TEXT, textColor);
        borderColor = intent.getIntExtra(EXTRA_THEME_BORDER, borderColor);
        topMarginDp = intent.getIntExtra(EXTRA_TOP_MARGIN, topMarginDp);
        inputFontSizeSp = intent.getIntExtra(EXTRA_INPUT_FONT_SIZE, inputFontSizeSp);
        displayMarginsMm = parseDisplayMargins(intent.getStringExtra(EXTRA_DISPLAY_MARGIN_MM));
        appTypeface = resolveTypeface(intent);
        iconTypeface = resolveIconTypeface();
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
        try {
            Drawable wallpaper = WallpaperManager.getInstance(this).getDrawable();
            if (wallpaper != null) {
                stage.setBackground(wallpaper);
                return;
            }
        } catch (Exception ignored) {
        }
        stage.setBackgroundColor(bgColor);
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
