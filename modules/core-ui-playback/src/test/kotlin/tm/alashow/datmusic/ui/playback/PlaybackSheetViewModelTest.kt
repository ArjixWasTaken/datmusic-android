/*
 * Copyright (C) 2021, Alashov Berkeli
 * All rights reserved.
 */
package tm.alashow.datmusic.ui.playback

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import tm.alashow.base.testing.BaseTest
import tm.alashow.base.ui.SnackbarManager
import tm.alashow.base.util.toUiMessage
import tm.alashow.datmusic.data.DatmusicSearchParams
import tm.alashow.datmusic.data.SampleData
import tm.alashow.datmusic.data.db.DatabaseModule
import tm.alashow.datmusic.data.interactors.playlist.CreatePlaylist
import tm.alashow.datmusic.playback.PlaybackConnection
import tm.alashow.datmusic.playback.models.MEDIA_TYPE_ALBUM
import tm.alashow.datmusic.playback.models.MediaId
import tm.alashow.datmusic.playback.models.PlaybackQueue
import tm.alashow.datmusic.playback.models.QueueTitle
import tm.alashow.navigation.Navigator
import tm.alashow.navigation.assertNextRouteContains
import tm.alashow.navigation.screens.LeafScreen

@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
class PlaybackSheetViewModelTest : BaseTest() {

    @Inject lateinit var playbackConnection: PlaybackConnection
    @Inject lateinit var snackbarManager: SnackbarManager
    @Inject lateinit var navigator: Navigator

    private lateinit var viewModel: PlaybackSheetViewModel

    private val fakePlaylist = SampleData.playlist()
    private val fakeSavedAsPlaylistMessage = SavedAsPlaylistMessage(fakePlaylist)
    private val fakeCreatePlaylist = mock<CreatePlaylist> {
        on { invoke(any()) } doReturn flowOf(fakePlaylist)
    }

    private val fakeError = Throwable("Fake error")
    private val erroneousCreatePlaylist = mock<CreatePlaylist> {
        on { invoke(any()) } doReturn flow { throw fakeError }
    }

    private val fakeQueueTitle = QueueTitle(sourceMediaId = MediaId(MEDIA_TYPE_ALBUM, "fake-id"))
    private val fakePlaybackConnection = mock<PlaybackConnection> {
        on { playbackQueue } doReturn MutableStateFlow(PlaybackQueue(title = fakeQueueTitle.toString()))
    }

    private fun buildVm(
        playbackConnection: PlaybackConnection? = null,
        createPlaylist: CreatePlaylist? = null
    ) = PlaybackSheetViewModel(
        SavedStateHandle(),
        playbackConnection ?: this.playbackConnection,
        createPlaylist ?: this.fakeCreatePlaylist,
        snackbarManager,
        navigator
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        viewModel = buildVm()
    }

    @After
    fun tearDown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun `saveQueueAsPlaylist adds saved message then navigates to playlist detail`() = testScope.runBlockingTest {
        viewModel.saveQueueAsPlaylist()
        verify(fakeCreatePlaylist).invoke(any())
        snackbarManager.messages.test {
            assertThat(awaitItem()).isEqualTo(fakeSavedAsPlaylistMessage)
        }
        snackbarManager.onMessageActionPerformed(fakeSavedAsPlaylistMessage)
        navigator.assertNextRouteContains(fakePlaylist.getIdentifier())
    }

    @Test
    fun `saveQueueAsPlaylist fails CreatePlaylist gracefully`() = testScope.runBlockingTest {
        viewModel = buildVm(createPlaylist = erroneousCreatePlaylist)
        viewModel.saveQueueAsPlaylist()

        verify(erroneousCreatePlaylist).invoke(any())
        snackbarManager.messages.test {
            assertThat(awaitItem().message).isEqualTo(fakeError.toUiMessage())
        }
    }

    @Test
    fun `navigateToQueueSource navigates to queue's source's media id`() = testScope.runBlockingTest {
        viewModel = buildVm(playbackConnection = fakePlaybackConnection)
        viewModel.navigateToQueueSource()

        navigator.assertNextRouteContains(fakeQueueTitle.sourceMediaId.value)
    }

    @Test
    fun `onTitleClick navigates to search albums route`() = testScope.runBlockingTest {
        viewModel.onTitleClick()

        navigator.assertNextRouteContains(LeafScreen.Search().root?.route, DatmusicSearchParams.BackendType.ALBUMS.type)
    }

    @Test
    fun `onArtistClick navigates to search albums & artists route`() = testScope.runBlockingTest {
        viewModel.onArtistClick()

        navigator.assertNextRouteContains(
            LeafScreen.Search().root?.route,
            DatmusicSearchParams.BackendType.ALBUMS.type,
            DatmusicSearchParams.BackendType.ARTISTS.type
        )
    }
}
