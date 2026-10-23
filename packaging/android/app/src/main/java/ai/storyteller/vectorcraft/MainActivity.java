package ai.storyteller.vectorcraft;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline Android shell for VectorCraft's existing touch-enabled WebAssembly app. */
public final class MainActivity extends Activity {
    private static final String TAG = "VectorCraft";
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final int OPEN_FILE_REQUEST = 4101;
    private static final int SAVE_FILE_REQUEST = 4102;
    private static final int MAX_QUEUED_SAVES = 2;

    private final ArrayDeque<SaveRequest> saveQueue = new ArrayDeque<>();
    private final ExecutorService fileWriter = Executors.newSingleThreadExecutor();

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private SaveRequest activeSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(38, 38, 38));
        window.setNavigationBarColor(Color.rgb(38, 38, 38));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(38, 38, 38));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new OfflineAssetClient(assetLoader));
        webView.setWebChromeClient(new FilePickerChromeClient());
        webView.addJavascriptInterface(new AndroidDownloads(), "VectorCraftAndroid");
        webView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        setContentView(webView);
        applySystemBarInsets(window);

        // Android WebView reliably supports WebGL2, while WebGPU support varies by device/WebView.
        // The app assets are served from a secure virtual HTTPS origin, not from file://.
        webView.loadUrl("https://" + ASSET_HOST + "/assets/index.html?webgl");
    }

    private void applySystemBarInsets(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            View decor = window.getDecorView();
            decor.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                if (webView != null) {
                    webView.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                }
                return insets;
            });
            decor.requestApplyInsets();
        }
    }

    private final class OfflineAssetClient extends WebViewClient {
        private final WebViewAssetLoader assetLoader;

        OfflineAssetClient(WebViewAssetLoader assetLoader) {
            this.assetLoader = assetLoader;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!"https".equals(uri.getScheme()) || !ASSET_HOST.equals(uri.getHost())) {
                return blockedResponse();
            }
            WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
            if (response == null) {
                return blockedResponse();
            }
            return withKnownMimeType(uri, response);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("https".equals(scheme) && ASSET_HOST.equals(uri.getHost())) {
                return false;
            }
            // The WebView stays offline; blob/data URLs are used by the app for exports and print.
            return !"blob".equals(scheme) && !"data".equals(scheme) && !"about".equals(scheme);
        }
    }

    private static WebResourceResponse blockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Forbidden",
                Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }

    private static WebResourceResponse withKnownMimeType(Uri uri, WebResourceResponse response) {
        String mime = mimeTypeForPath(uri.getPath());
        if (mime == null || response.getStatusCode() != 200 || response.getData() == null) {
            return response;
        }

        Map<String, String> headers = new HashMap<>();
        Map<String, String> originalHeaders = response.getResponseHeaders();
        if (originalHeaders != null) {
            for (Map.Entry<String, String> header : originalHeaders.entrySet()) {
                if (!"content-type".equalsIgnoreCase(header.getKey())) {
                    headers.put(header.getKey(), header.getValue());
                }
            }
        }
        headers.put("Content-Type", mime);
        String encoding = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") ? "UTF-8" : null;
        return new WebResourceResponse(mime, encoding, 200, "OK", headers, response.getData());
    }

    private static String mimeTypeForPath(String path) {
        if (path == null) {
            return null;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".js")) return "text/javascript";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return null;
    }

    private final class FilePickerChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params) {
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
            }
            fileChooserCallback = callback;
            try {
                startActivityForResult(params.createIntent(), OPEN_FILE_REQUEST);
                return true;
            } catch (RuntimeException error) {
                Log.e(TAG, "Couldn't open Android's file picker", error);
                fileChooserCallback = null;
                callback.onReceiveValue(null);
                showToast("No file picker is available on this device");
                return true;
            }
        }
    }

    private final class AndroidDownloads {
        @JavascriptInterface
        public void saveFile(String name, String mimeType, String base64) {
            if (base64 == null) {
                showToast("Nothing to save");
                return;
            }
            final byte[] bytes;
            try {
                bytes = Base64.decode(base64, Base64.DEFAULT);
            } catch (IllegalArgumentException error) {
                Log.e(TAG, "VectorCraft sent an invalid file", error);
                showToast("Couldn't prepare the file for saving");
                return;
            }
            SaveRequest request = new SaveRequest(safeFileName(name), safeMimeType(mimeType), bytes);
            runOnUiThread(() -> enqueueSave(request));
        }
    }

    private void enqueueSave(SaveRequest request) {
        int pending = saveQueue.size() + (activeSave == null ? 0 : 1);
        if (pending >= MAX_QUEUED_SAVES) {
            showToast("Finish the current save before exporting another file");
            return;
        }
        saveQueue.addLast(request);
        launchNextSavePicker();
    }

    private void launchNextSavePicker() {
        if (activeSave != null || saveQueue.isEmpty() || isFinishing()) {
            return;
        }
        activeSave = saveQueue.removeFirst();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(activeSave.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, activeSave.name);
        try {
            startActivityForResult(intent, SAVE_FILE_REQUEST);
            } catch (RuntimeException error) {
                Log.e(TAG, "Couldn't open Android's Save As picker", error);
            activeSave = null;
            showToast("No Save As provider is available on this device");
            launchNextSavePicker();
        }
    }

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_FILE_REQUEST) {
            if (fileChooserCallback != null) {
                Uri[] selected = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileChooserCallback.onReceiveValue(selected);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode == SAVE_FILE_REQUEST) {
            SaveRequest request = activeSave;
            activeSave = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && request != null) {
                writeFile(data.getData(), request);
            }
            launchNextSavePicker();
        }
    }

    private void writeFile(Uri uri, SaveRequest request) {
        fileWriter.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Storage provider did not open the destination");
                }
                output.write(request.bytes);
                output.flush();
                showToast("Saved " + request.name);
            } catch (IOException | RuntimeException error) {
                Log.e(TAG, "Couldn't save " + request.name, error);
                showToast("Couldn't save " + request.name);
            }
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            if (!isFinishing()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String safeFileName(String name) {
        String candidate = name == null ? "" : name.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (candidate.isEmpty() || ".".equals(candidate) || "..".equals(candidate)) {
            return "VectorCraft";
        }
        return candidate;
    }

    private static String safeMimeType(String mimeType) {
        return mimeType != null && mimeType.matches("[\\w.+-]+/[\\w.+-]+") ? mimeType : "*/*";
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("VectorCraftAndroid");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        fileWriter.shutdown();
        super.onDestroy();
    }

    private static final class SaveRequest {
        final String name;
        final String mimeType;
        final byte[] bytes;

        SaveRequest(String name, String mimeType, byte[] bytes) {
            this.name = name;
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }
}
