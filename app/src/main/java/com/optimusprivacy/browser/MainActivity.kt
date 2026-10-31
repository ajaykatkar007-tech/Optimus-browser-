package com.optimusprivacy.browser

import android.app.Activity
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebStorage
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val browserPrefs by lazy { getSharedPreferences("optimus_browser", MODE_PRIVATE) }

    private val blockedDomains = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "connect.facebook.net",
        "scorecardresearch.com"
    )
    private var blockedCount = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 8)
        }
        addressBar = EditText(this).apply {
            hint = "Search or enter website"
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setText("https://www.google.com")
        }
        addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                navigate(addressBar.text.toString())
                true
            } else false
        }
        root.addView(addressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val primaryControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        primaryControls.addView(makeButton("Go") { navigate(addressBar.text.toString()) })
        primaryControls.addView(makeButton("Back") {
            if (webView.canGoBack()) webView.goBack()
        })
        primaryControls.addView(makeButton("Reload") { webView.reload() })
        root.addView(primaryControls)

        val savedControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        savedControls.addView(makeButton("Bookmark") { saveCurrentBookmark() })
        savedControls.addView(makeButton("Bookmarks") { showSavedPages("bookmarks") })
        savedControls.addView(makeButton("History") { showSavedPages("history") })
        savedControls.addView(makeButton("Clear data") { confirmClearBrowsingData() })
        root.addView(savedControls)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 4
        ))
        statusText = TextView(this).apply {
            text = "Optimus Browser • Starter tracker protection"
            textSize = 12f
        }
        root.addView(statusText)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme != "https" && uri.scheme != "http"
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host?.lowercase(Locale.ROOT) ?: return null
                if (blockedDomains.any { host == it || host.endsWith(".$it") }) {
                    synchronized(this@MainActivity) { blockedCount++ }
                    runOnUiThread { statusText.text = "Optimus Browser • Blocked requests: $blockedCount" }
                    return WebResourceResponse(
                        "text/plain", "utf-8", 204, "No Content",
                        emptyMap(), ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                addressBar.setText(url.orEmpty())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                addressBar.setText(url.orEmpty())
                if (!url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))) {
                    addToHistory(url)
                }
                statusText.text = "Optimus Browser • Blocked requests: $blockedCount"
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WebView.startSafeBrowsing(this) { }
        }
        webView.loadUrl("https://www.google.com")
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            minWidth = 0
            setPadding(4, 0, 4, 0)
            setOnClickListener { action() }
        }

    private fun navigate(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        val target = when {
            input.startsWith("https://", true) || input.startsWith("http://", true) -> input
            input.contains(" ") || !input.contains(".") ->
                "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
            else -> "https://$input"
        }
        val scheme = android.net.Uri.parse(target).scheme
        if (scheme != "https" && scheme != "http") {
            Toast.makeText(this, "Only HTTP and HTTPS links are supported", Toast.LENGTH_SHORT).show()
            return
        }
        webView.loadUrl(target)
    }

    private fun saveCurrentBookmark() {
        val url = webView.url
        if (url.isNullOrBlank() || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            Toast.makeText(this, "Open a website before bookmarking", Toast.LENGTH_SHORT).show()
            return
        }
        val bookmarks = readList("bookmarks").toMutableList()
        bookmarks.remove(url)
        bookmarks.add(0, url)
        writeList("bookmarks", bookmarks.take(200))
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun addToHistory(url: String) {
        val history = readList("history").toMutableList()
        if (history.firstOrNull() != url) {
            history.remove(url)
            history.add(0, url)
            writeList("history", history.take(100))
        }
    }

    private fun readList(key: String): List<String> =
        browserPrefs.getString(key, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    private fun writeList(key: String, items: List<String>) {
        browserPrefs.edit().putString(key, items.joinToString("\n")).apply()
    }

    private fun showSavedPages(key: String) {
        val urls = readList(key)
        if (urls.isEmpty()) {
            Toast.makeText(this, if (key == "history") "No browsing history yet" else "No bookmarks yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = urls.map { url ->
            val host = android.net.Uri.parse(url).host.orEmpty()
            if (host.isBlank()) url else "$host\n$url"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (key == "history") "Browsing history" else "Bookmarks")
            .setItems(labels) { _, which -> webView.loadUrl(urls[which]) }
            .setNeutralButton("Clear list") { _, _ ->
                writeList(key, emptyList())
                Toast.makeText(this, if (key == "history") "History cleared" else "Bookmarks cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmClearBrowsingData() {
        AlertDialog.Builder(this)
            .setTitle("Clear browsing data?")
            .setMessage("This clears cookies, cache, form data, and history on this device. Saved bookmarks will remain.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> clearBrowsingData() }
            .show()
    }

    private fun clearBrowsingData() {
        webView.stopLoading()
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
        WebStorage.getInstance().deleteAllData()
        browserPrefs.edit().remove("history").apply()
        blockedCount = 0
        addressBar.setText("")
        statusText.text = "Browsing data cleared on this device"
        Toast.makeText(this, "Local browsing data cleared", Toast.LENGTH_SHORT).show()
        webView.loadUrl("about:blank")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
