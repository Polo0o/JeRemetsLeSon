package com.durandestebe.jrls

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Moteur de lecture : une carte = un dossier, joué en boucle, ordre
 * alphabétique, reprise à la position mémorisée (RF03, RF04, RF07).
 */
class BoxPlayer(
    private val context: Context,
    private val registry: CardRegistry
) {

    private val audioExtensions =
        listOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus")

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var currentUid: String? = null

    fun playCard(card: Card) {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(card.treeUri))
        val files = (tree?.listFiles() ?: emptyArray())
            .filter { f ->
                f.isFile && (
                    f.type?.startsWith("audio/") == true ||
                        f.name?.let { name ->
                            audioExtensions.any {
                                name.endsWith(it, ignoreCase = true)
                            }
                        } == true
                    )
            }
            .sortedBy { f -> f.name?.lowercase() ?: "" }

        // Sauvegarde de la position de la carte en cours avant de changer
        savePosition()

        if (currentUid == card.uid && player.isPlaying) {
            // Re-posée pendant la lecture : simple reprise/poursuite
            player.play()
            return
        }

        if (files.isEmpty()) return

        val items = files.map { f -> MediaItem.Builder().setUri(f.uri).build() }
        currentUid = card.uid
        val resumeMs = registry.position(card.uid)
        player.setMediaItems(items, /* startIndex = */ 0, /* startPositionMs = */ resumeMs)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.play()
    }

    fun savePosition() {
        currentUid?.let { uid ->
            registry.setPosition(uid, player.currentPosition)
        }
    }

    fun resume() {
        player.play()
    }

    fun pause() {
        savePosition()
        player.pause()
    }

    fun release() {
        savePosition()
        player.release()
    }
}
