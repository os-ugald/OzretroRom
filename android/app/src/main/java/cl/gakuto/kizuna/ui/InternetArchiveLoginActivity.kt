package cl.gakuto.kizuna.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.auth.InternetArchiveS3PageParser
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * App-local Internet Archive sign-in. Oz reads existing IA-S3 fields only from the exact official
 * keys page and never installs a JavaScript bridge. Password fields, key generation and key
 * rotation are outside this flow. Manual IA-S3 import remains available if IA changes the page.
 */
class InternetArchiveLoginActivity : ComponentActivity() {
    private lateinit var auth: InternetArchiveAuthManager
    private var authExchangeInFlight = false

    private enum class Mode { WEB, S3_KEYS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = InternetArchiveAuthManager(this)
        setContent {
            OzTheme {
                var status by remember { mutableStateOf("Abriendo la sesión segura de Internet Archive…") }
                var exchanging by remember { mutableStateOf(false) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                var mode by remember { mutableStateOf(Mode.WEB) }
                var accessKey by remember { mutableStateOf("") }
                var secretKey by remember { mutableStateOf("") }

                fun completeConnection() {
                    setResult(RESULT_OK)
                    Toast.makeText(
                        this@InternetArchiveLoginActivity,
                        "Internet Archive conectado",
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }

                fun beginExchange(
                    failureMessage: String,
                    block: suspend () -> InternetArchiveAuthManager.Session?,
                ) {
                    if (authExchangeInFlight) return
                    authExchangeInFlight = true
                    exchanging = true
                    lifecycleScope.launch {
                        val session = try {
                            block()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        } finally {
                            authExchangeInFlight = false
                            exchanging = false
                        }
                        if (session != null) completeConnection() else status = failureMessage
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().background(OzColors.DeepSpace),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Internet Archive", style = OzTypography.section, color = OzColors.TextPrimary)
                            Text(
                                "Sesión dentro de Oz · contraseña nunca almacenada",
                                style = OzTypography.meta,
                                color = OzColors.TextSecondary,
                            )
                        }
                        Button(onClick = { finish() }) { Text("Cerrar") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled = !exchanging,
                            onClick = {
                                mode = Mode.WEB
                                status = "Inicia sesión; Oz capturará las claves sólo en la página IA-S3 oficial."
                            },
                        ) { Text("Inicio web") }
                        Button(
                            enabled = !exchanging,
                            onClick = {
                                webView?.stopLoading()
                                webView?.destroy()
                                webView = null
                                mode = Mode.S3_KEYS
                                status = "Pega Access Key y Secret Key; Oz no necesita tu contraseña."
                            },
                        ) { Text("Usar claves") }
                    }

                    if (mode == Mode.WEB) {
                        Text(
                            status,
                            style = OzTypography.meta,
                            color = if (exchanging) OzColors.Cyan else OzColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        if (exchanging) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.padding(5.dp))
                                Text("Validando credenciales IA-S3…", color = OzColors.TextPrimary)
                            }
                        } else {
                            Button(
                                onClick = {
                                    status = "Recargando la página oficial para reintentar la captura…"
                                    webView?.reload()
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            ) { Text("Reintentar captura") }
                        }
                        InternetArchiveWebView(
                            auth = auth,
                            modifier = Modifier.fillMaxSize(),
                            onWebView = { webView = it },
                            onStatus = { status = it },
                            onPageKeys = { keys, cookies ->
                                beginExchange(
                                    "Oz leyó la página IA-S3, pero Internet Archive no validó las claves. Reintenta o usa ‘Usar claves’.",
                                ) {
                                    auth.completeLoginFromPageKeys(keys.access, keys.secret, cookies)
                                }
                            },
                            onCookieSession = { cookies ->
                                beginExchange(
                                    "La sesión web no entregó credenciales válidas. Reintenta o usa ‘Usar claves’.",
                                ) {
                                    auth.completeLoginFromCookies(cookies)
                                }
                            },
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Conexión con claves IA-S3", style = OzTypography.section, color = OzColors.TextPrimary)
                            Text(
                                "Copia Access Key y Secret Key desde la página IA-S3. Oz las valida por HTTPS y las guarda cifradas con Android Keystore.",
                                style = OzTypography.meta,
                                color = OzColors.TextSecondary,
                            )
                            Text(
                                "No compartas capturas de esa página: muestran tu clave secreta.",
                                style = OzTypography.meta,
                                color = OzColors.Warning,
                            )
                            Button(
                                enabled = !exchanging,
                                onClick = {
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(InternetArchiveAuthManager.S3_KEYS_URL)))
                                    }.onFailure {
                                        status = "Android no encontró un navegador para abrir Internet Archive."
                                    }
                                },
                            ) { Text("Abrir página IA-S3") }
                            OutlinedTextField(
                                value = accessKey,
                                onValueChange = { accessKey = it.take(64) },
                                label = { Text("Access Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = secretKey,
                                onValueChange = { secretKey = it.take(64) },
                                label = { Text("Secret Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (exchanging) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.padding(5.dp))
                                    Text("Validando con Internet Archive…", color = OzColors.TextPrimary)
                                }
                            }
                            Button(
                                enabled = !exchanging && accessKey.isNotBlank() && secretKey.isNotBlank(),
                                onClick = {
                                    status = "Validando claves IA-S3…"
                                    beginExchange(
                                        "No se pudieron validar las claves. Revisa que ambas tengan 16 caracteres y que Internet Archive responda.",
                                    ) {
                                        auth.importS3Credentials(accessKey, secretKey)
                                    }
                                },
                            ) { Text("Validar y conectar") }
                            Text(status, style = OzTypography.meta, color = OzColors.TextSecondary)
                        }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        webView?.stopLoading()
                        webView?.destroy()
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun InternetArchiveWebView(
    auth: InternetArchiveAuthManager,
    modifier: Modifier,
    onWebView: (WebView) -> Unit,
    onStatus: (String) -> Unit,
    onPageKeys: (InternetArchiveS3PageParser.Keys, String) -> Unit,
    onCookieSession: (String) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                onWebView(this)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }
                val loginWebView = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(loginWebView, false)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val target = request.url.toString()
                        if (!auth.isExpectedWebLoginUrl(target)) {
                            onStatus("Oz bloqueó una navegación fuera de Internet Archive.")
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        if (url != null && !auth.isExpectedWebLoginUrl(url)) {
                            view.stopLoading()
                            onStatus("Oz bloqueó una navegación fuera de Internet Archive.")
                            return
                        }
                        onStatus("Internet Archive · conexión segura")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        val current = url.orEmpty()
                        if (!auth.isExpectedWebLoginUrl(current)) return
                        if (!auth.isS3KeysPageUrl(current)) {
                            onStatus("Sesión web activa. Continúa hasta la página oficial de claves IA-S3.")
                            return
                        }

                        onStatus("Página IA-S3 verificada. Leyendo las claves existentes de forma segura…")
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.flush()
                        val cookies = cookieManager.getCookie(current).orEmpty()
                        view.evaluateJavascript(InternetArchiveS3PageParser.DOM_EXTRACTION_SCRIPT) { rawResult ->
                            if (!auth.isS3KeysPageUrl(view.url.orEmpty())) {
                                onStatus("La página cambió antes de completar la verificación; reintenta.")
                                return@evaluateJavascript
                            }
                            val keys = InternetArchiveS3PageParser.parseJavascriptResult(rawResult)
                            when {
                                keys != null -> onPageKeys(keys, cookies)
                                cookies.isNotBlank() -> onCookieSession(cookies)
                                else -> onStatus("No se detectaron claves ni una sesión web utilizable. Usa ‘Usar claves’.")
                            }
                        }
                    }
                }
                loadUrl(InternetArchiveAuthManager.S3_KEYS_URL)
            }
        },
    )
}
