package com.durandestebe.jrls

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Activité unique : mode enfant (kiosque écran noir, lecture au tag NFC)
 * et interface parent (association des étiquettes, volume max, étiquette STOP).
 *
 * Les tags sont identifiés par leur UID : rien n'est jamais écrit dessus.
 */
class MainActivity : ComponentActivity() {

    private lateinit var registry: CardRegistry
    private lateinit var player: BoxPlayer
    private lateinit var guard: VolumeGuard
    private var nfc: NfcAdapter? = null

    /** File d'attente : le prochain tag lu sera livré à ce callback (assistant). */
    private var onNextTag: ((String) -> Unit)? = null
    private var lastTagAt = 0L

    var currentTitle by mutableStateOf<String?>(null)
    var paused by mutableStateOf(false)

    private val tagCallback = object : NfcAdapter.ReaderCallback {
        override fun onTagDiscovered(tag: Tag) {
            val uid = tag.id.joinToString("") { String.format("%02X", it) }
            runOnUiThread {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTagAt < 1000) return@runOnUiThread
                lastTagAt = now
                val handler = onNextTag
                if (handler != null) {
                    onNextTag = null
                    handler(uid)
                } else {
                    route(uid)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = CardRegistry(this)
        player = BoxPlayer(this, registry)
        guard = VolumeGuard(this) { registry.volumeMaxPct() }
        nfc = NfcAdapter.getDefaultAdapter(this)
        setContent { App() }
    }

    /** Comportement principal (RF01 à RF04) : un tag = une action. */
    private fun route(uid: String) {
        if (registry.stopUid().isNotEmpty() && uid == registry.stopUid()) {
            player.pause()
            paused = true
            return
        }
        val card = registry.card(uid) ?: return
        player.playCard(card)
        currentTitle = card.title
        paused = false
    }

    // --- Actions exposées à l'interface ---

    fun test(card: Card) {
        player.playCard(card)
        currentTitle = card.title
        paused = false
    }

    fun resumePlayback() {
        player.resume()
        paused = false
    }

    fun associateFolder(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun waitForNextTag(block: (String) -> Unit) { onNextTag = block }
    fun cancelWaitForTag() { onNextTag = null }

    // --- Mode kiosque (RF06) ---

    fun enterChild() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = 0.01f
        window.attributes = lp
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        runCatching { startLockTask() }
        guard.clamp()
    }

    fun exitChild() {
        runCatching { stopLockTask() }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    // --- Cycle de vie : NFC et verrouillage du volume ---

    override fun onResume() {
        super.onResume()
        nfc?.enableReaderMode(
            this, tagCallback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        guard.start()
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
        guard.stop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    // --- Interface ---

    private enum class Mode { SETUP, CHILD, ADMIN }

    private sealed class Step {
        object None : Step()
        object WaitTag : Step()
        data class PickFolder(val uid: String) : Step()
        data class Naming(val uid: String, val uri: Uri) : Step()
    }

    @Composable
    private fun App() {
        var mode by remember {
            mutableStateOf(if (registry.pin().isEmpty()) Mode.SETUP else Mode.CHILD)
        }
        LaunchedEffect(mode) {
            if (mode == Mode.CHILD) enterChild() else exitChild()
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            when (mode) {
                Mode.SETUP -> SetupScreen(onDone = { pin ->
                    registry.setPin(pin)
                    mode = Mode.ADMIN
                })
                Mode.CHILD -> {
                    ChildScreen()
                    var pinDialog by remember { mutableStateOf(false) }
                    BackHandler { pinDialog = true }
                    if (pinDialog) {
                        PinDialog(
                            onDismiss = { pinDialog = false },
                            onConfirm = { pin ->
                                pinDialog = false
                                if (pin == registry.pin()) mode = Mode.ADMIN
                            }
                        )
                    }
                }
                Mode.ADMIN -> AdminScreen(onChildMode = { mode = Mode.CHILD })
            }
        }
    }

    @Composable
    private fun SetupScreen(onDone: (String) -> Unit) {
        var pin by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Première configuration", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choisis un code parental (4 à 6 chiffres). " +
                    "Il sera demandé pour quitter le mode enfant.",
                color = Color.Gray
            )
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { s -> if (s.length <= 6) pin = s.filter { it.isDigit() } },
                label = { Text("Code parental") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { s -> if (s.length <= 6) confirm = s.filter { it.isDigit() } },
                label = { Text("Confirmer le code") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(8.dp))
            Button(
                onClick = { onDone(pin) },
                enabled = pin.length >= 4 && pin == confirm
            ) { Text("Enregistrer") }
        }
    }

    @Composable
    private fun ChildScreen() {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(currentTitle ?: "♪", color = Color(0xFF444444), textAlign = TextAlign.Center)
            if (paused) Text("En pause", color = Color(0xFF333333))
        }
    }

    @Composable
    private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Code parental") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { s -> if (s.length <= 6) pin = s.filter { it.isDigit() } },
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(pin) }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        )
    }

    @Composable
    private fun AdminScreen(onChildMode: () -> Unit) {
        var cards by remember { mutableStateOf(registry.cards()) }
        var stopWiz by remember { mutableStateOf(false) }
        var volume by remember { mutableStateOf(registry.volumeMaxPct()) }
        var step by remember { mutableStateOf<Step>(Step.None) }
        var pickedUri by remember { mutableStateOf<Uri?>(null) }
        var title by remember { mutableStateOf("") }

        val treePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                associateFolder(uri)
                pickedUri = uri
            }
        }

        when (val s = step) {
            Step.WaitTag -> {
                LaunchedEffect(Unit) {
                    waitForNextTag { uid -> step = Step.PickFolder(uid) }
                }
                DisposableEffect(Unit) { onDispose { cancelWaitForTag() } }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Pose une étiquette vierge contre le téléphone…", textAlign = TextAlign.Center)
                    Spacer(Modifier.padding(12.dp))
                    OutlinedButton(onClick = { step = Step.None }) { Text("Annuler") }
                }
            }
            is Step.PickFolder -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Étiquette détectée : ${s.uid}")
                    Spacer(Modifier.padding(12.dp))
                    Button(onClick = {
                        pickedUri = null
                        treePicker.launch(null)
                    }) { Text("Choisir le dossier de musique") }
                    if (pickedUri != null) Text("Dossier choisi ✓", color = Color.Green)
                    Spacer(Modifier.padding(12.dp))
                    Button(
                        onClick = { pickedUri?.let { step = Step.Naming(s.uid, it) } },
                        enabled = pickedUri != null
                    ) { Text("Continuer") }
                    Spacer(Modifier.padding(8.dp))
                    OutlinedButton(onClick = { step = Step.None }) { Text("Annuler") }
                }
            }
            is Step.Naming -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nom de la carte (ex. « Album de Julia »)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(12.dp))
                    Button(
                        onClick = {
                            registry.save(Card(s.uid, title.trim(), s.uri.toString()))
                            cards = registry.cards()
                            title = ""
                            step = Step.None
                        },
                        enabled = title.isNotBlank()
                    ) { Text("Enregistrer la carte") }
                    Spacer(Modifier.padding(8.dp))
                    OutlinedButton(onClick = { step = Step.None }) { Text("Annuler") }
                }
            }
            Step.None -> {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cartes", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${cards.size} étiquette(s) — STOP : " +
                                    if (registry.stopUid().isEmpty()) "non définie" else "définie",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = { step = Step.WaitTag }) { Text("Ajouter une carte") }
                    }
                    Spacer(Modifier.padding(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume maximum : $volume %")
                        OutlinedButton(onClick = { stopWiz = true }) {
                            Text("Définir l'étiquette STOP")
                        }
                    }
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { volume = it.toInt() },
                        onValueChangeFinished = {
                            registry.setVolumeMaxPct(volume)
                            guard.clamp()
                        },
                        valueRange = 20f..100f
                    )
                    Spacer(Modifier.padding(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(cards, key = { it.uid }) { card ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        card.uid,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(onClick = { test(card) }) { Text("Écouter") }
                                TextButton(onClick = {
                                    registry.delete(card.uid)
                                    cards = registry.cards()
                                }) { Text("Supprimer") }
                            }
                        }
                    }
                    Spacer(Modifier.padding(8.dp))
                    Button(onClick = onChildMode, modifier = Modifier.fillMaxWidth()) {
                        Text("Retour au mode enfant")
                    }
                    if (stopWiz) {
                        AlertDialog(
                            onDismissRequest = {
                                stopWiz = false
                                cancelWaitForTag()
                            },
                            title = { Text("Étiquette STOP") },
                            text = { Text("Appuie sur « Détecter » puis pose l'étiquette à utiliser pour la pause.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    waitForNextTag { uid ->
                                        registry.setStopUid(uid)
                                        stopWiz = false
                                    }
                                }) { Text("Détecter") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    stopWiz = false
                                    cancelWaitForTag()
                                }) { Text("Annuler") }
                            }
                        )
                    }
                }
            }
        }
    }
}
