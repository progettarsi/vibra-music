/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        private val params = savedStateHandle.get<String>("params")

        /** The colour YouTube gives this category; none when opened without one. */
        val stripeColor: Long? = savedStateHandle.get<Long>("color")?.takeIf { it != 0L }

        val result = MutableStateFlow<BrowseResult?>(null)

        /** The page failed to load, or came back with nothing to show. */
        val failed = MutableStateFlow(false)
        val startingRadio = MutableStateFlow(false)

        init {
            viewModelScope.launch {
                YouTube
                    .browse(browseId, params)
                    .onSuccess {
                        val shown = withContentFilters(it)
                        if (shown.items.isEmpty()) failed.value = true else result.value = shown
                    }.onFailure {
                        reportException(it)
                        failed.value = true
                    }
            }
        }

        /**
         * A station for this category: songs from a few of its playlists and albums, shuffled.
         * Starting it again makes a new one; it never pauses or resumes what is playing.
         */
        fun startRadio(
            player: PlayerConnection,
            unavailable: () -> Unit,
        ) {
            val page = result.value ?: return
            if (startingRadio.value) return
            startingRadio.value = true
            viewModelScope.launch {
                try {
                    val items = page.items.flatMap { it.items }
                    val loaded =
                        items
                            .filter { it is PlaylistItem || it is AlbumItem }
                            .shuffled()
                            .take(RadioSources)
                            .map { source ->
                                async(Dispatchers.IO) {
                                    when (source) {
                                        is PlaylistItem -> YouTube.playlist(source.id).getOrNull()?.songs
                                        is AlbumItem -> YouTube.album(source.browseId).getOrNull()?.songs
                                        else -> null
                                    }.orEmpty()
                                }
                            }.awaitAll()
                            .flatten()
                    val candidates = items.filterIsInstance<SongItem>() + loaded
                    val songs =
                        withContentFilters(page.copy(items = listOf(BrowseResult.Item(null, candidates))))
                            .items
                            .flatMap { it.items }
                            .filterIsInstance<SongItem>()
                            .distinctBy { it.id }
                            .shuffled()
                            .take(RadioSize)
                    if (songs.isEmpty()) {
                        unavailable()
                    } else {
                        player.playQueue(ListQueue(title = page.title, items = songs.map { it.toMediaItem() }))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    unavailable()
                } finally {
                    startingRadio.value = false
                }
            }
        }

        private suspend fun withContentFilters(page: BrowseResult): BrowseResult {
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val aiContentFilterPolicy = loadAiContentFilterPolicy()
            val contentFilteredResult =
                page
                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                    .filterVideo(hideVideo)
                    .filterBlockedArtists(database.getBlockedArtistIds().toSet())
            return contentFilteredResult.copy(
                items =
                    contentFilteredResult.items.mapNotNull { section ->
                        section
                            .copy(items = filterAiContent(section.items, aiContentFilterPolicy))
                            .takeIf { filteredSection -> filteredSection.items.isNotEmpty() }
                    },
            )
        }

        private companion object {
            const val RadioSources = 4
            const val RadioSize = 100
        }
    }
