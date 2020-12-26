package com.example.fastani.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import com.example.fastani.*
import com.example.fastani.MainActivity.Companion.getViewKey
import com.example.fastani.MainActivity.Companion.getViewPosDur
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import java.lang.Exception

const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"

/**
 * A simple [Fragment] subclass.
 * Use the [PlayerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

// TITLE AND URL OR CARD MUST BE PROVIDED
// EPISODE AND SEASON SHOULD START AT 0
data class PlayerData(
    val title: String?,
    val url: String?,

    val episodeIndex: Int?,
    val seasonIndex: Int?,
    val card: FastAniApi.Card?,
)

class PlayerFragment(data: PlayerData) : Fragment() {
    companion object {
        var isInPlayer: Boolean = false
        var onLeftPlayer = Event<Boolean>()
    }

    private var data: PlayerData = data
    private lateinit var exoPlayer: SimpleExoPlayer
    private lateinit var dataSourceFactory: MediaSourceFactory

    // private val url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var isFullscreen = false
    private var isPlayerPlaying = true
    private val mediaItem = MediaItem.Builder()
        .setUri(getCurrentUrl())
        .setMimeType(MimeTypes.APPLICATION_MP4)
        .build()

    fun canPlayNextEpisode(): Boolean {
        if (data.card == null || data.seasonIndex == null || data.episodeIndex == null) {
            return false
        }
        try {
            return MainActivity.canPlayNextEpisode(data.card!!, data.seasonIndex!!, data.episodeIndex!!).isFound
        } catch (e: Exception) {
            return false
        }
    }

    private fun getCurrentEpisode(): FastAniApi.FullEpisode {
        return data.card!!.cdnData.seasons[data.seasonIndex!!].episodes[data.episodeIndex!!]
    }

    fun getCurrentTitle(): String {
        if (data.title != null) return data.title!!

        val isMovie: Boolean = data.card!!.episodes == 1 && data.card!!.status == "FINISHED"
        // data.card!!.cdnData.seasons.size == 1 && data.card!!.cdnData.seasons[0].episodes.size == 1
        var preTitle = ""
        if (isMovie) {
            preTitle = "S${data.seasonIndex!! + 1}E:${data.episodeIndex} · "
        }
        return preTitle + getCurrentEpisode().title!!
    }

    private fun getCurrentUrl(): String {
        println("MAN::: " + data.url)
        if (data.url != null) return data.url!!
        return getCurrentEpisode().file
    }

    override fun onDestroy() {
        if (data.card != null && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0) {
            MainActivity.setViewPosDur(data, exoPlayer.currentPosition, exoPlayer.duration)
        }
        // DON'T SAVE DATA OF TRAILERS

        isInPlayer = false
        onLeftPlayer.invoke(true)
        MainActivity.showSystemUI()

        super.onDestroy()

        //MainActivity.showSystemUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isInPlayer = true
        retainInstance = true // OTHERWISE IT WILL CAUSE A CRASH
        MainActivity.hideSystemUI()

        exo_rew.setOnClickListener {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)
            exoPlayer.seekTo(maxOf(exoPlayer.currentPosition - 10000L, 0L))
        }
        exo_ffwd.setOnClickListener {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)
            exoPlayer.seekTo(minOf(exoPlayer.currentPosition + 10000L, exoPlayer.duration))
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }
    }

    private fun releasePlayer() {
        isPlayerPlaying = exoPlayer.playWhenReady
        playbackPosition = exoPlayer.currentPosition
        currentWindow = exoPlayer.currentWindowIndex
        exoPlayer.release()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
        outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        super.onSaveInstanceState(outState)
    }

    private fun initPlayer() {
        // NEEDED FOR HEADERS
        class CustomFactory : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val dataSource = DefaultHttpDataSourceFactory(FastAniApi.USER_AGENT).createDataSource()
                FastAniApi.currentHeaders?.forEach {
                    dataSource.setRequestProperty(it.key, it.value)
                }
                return dataSource
            }
        }

        exoPlayer =
            SimpleExoPlayer.Builder(this.requireContext())
                .setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))
                .build().apply {
                    playWhenReady = isPlayerPlaying
                    seekTo(currentWindow, playbackPosition)
                    setMediaItem(mediaItem, false)
                    prepare()
                }
        player_view.player = exoPlayer
    }

    override fun onStart() {
        super.onStart()
        if (data.card != null) {
            val pro = getViewPosDur(data.card!!.anilistId, data.seasonIndex!!, data.episodeIndex!!)
            if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                playbackPosition = pro.pos
            }
        }
        if (Util.SDK_INT > 23) {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23) {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.player, container, false)
    }
}