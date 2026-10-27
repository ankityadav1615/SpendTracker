package com.spendtracker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionsTest {

    @Test
    fun `prefers the background download when Play allows it`() {
        assertEquals(UpdateFlow.FLEXIBLE, UpdateDecisions.flowFor(flexibleAllowed = true, immediateAllowed = true))
        assertEquals(UpdateFlow.IMMEDIATE, UpdateDecisions.flowFor(flexibleAllowed = false, immediateAllowed = true))
        assertNull(UpdateDecisions.flowFor(flexibleAllowed = false, immediateAllowed = false))
    }

    @Test
    fun `offers an available update`() {
        assertEquals(UpdateState.Available(7), banner(updateAvailable = true, version = 7))
    }

    @Test
    fun `shows nothing when there is no update or Play won't allow one`() {
        assertEquals(UpdateState.None, banner(updateAvailable = false, version = 7))
        assertEquals(UpdateState.None, banner(updateAvailable = true, version = 7, flow = null))
    }

    @Test
    fun `not now hides only the dismissed version`() {
        assertEquals(UpdateState.None, banner(updateAvailable = true, version = 7, dismissed = 7))
        assertEquals(UpdateState.Available(8), banner(updateAvailable = true, version = 8, dismissed = 7))
    }

    @Test
    fun `a download in progress shows even after not now`() {
        assertEquals(
            UpdateState.Downloading(0.25f),
            banner(updateAvailable = true, version = 7, phase = InstallPhase.DOWNLOADING, bytes = 25, total = 100, dismissed = 7),
        )
    }

    @Test
    fun `a finished download asks to restart`() {
        assertEquals(
            UpdateState.ReadyToInstall,
            banner(updateAvailable = false, version = 7, phase = InstallPhase.DOWNLOADED, dismissed = 7),
        )
    }

    @Test
    fun `progress is unknown until Play reports a size, and stays within 0 to 1`() {
        assertNull(UpdateDecisions.progress(10, 0))
        assertNull(UpdateDecisions.progress(10, -1))
        assertEquals(0f, UpdateDecisions.progress(0, 100)!!, 0f)
        assertEquals(0.5f, UpdateDecisions.progress(50, 100)!!, 0f)
        assertEquals(1f, UpdateDecisions.progress(150, 100)!!, 0f)
    }

    @Test
    fun `notifies once per new version`() {
        assertTrue(UpdateDecisions.shouldNotify(updateAvailable = true, availableVersionCode = 7, lastNotifiedVersionCode = 0))
        assertFalse(UpdateDecisions.shouldNotify(updateAvailable = true, availableVersionCode = 7, lastNotifiedVersionCode = 7))
        assertTrue(UpdateDecisions.shouldNotify(updateAvailable = true, availableVersionCode = 8, lastNotifiedVersionCode = 7))
        assertFalse(UpdateDecisions.shouldNotify(updateAvailable = false, availableVersionCode = 8, lastNotifiedVersionCode = 7))
    }

    private fun banner(
        updateAvailable: Boolean,
        version: Int,
        flow: UpdateFlow? = UpdateFlow.FLEXIBLE,
        phase: InstallPhase = InstallPhase.IDLE,
        bytes: Long = 0,
        total: Long = 0,
        dismissed: Int? = null,
    ) = UpdateDecisions.bannerState(updateAvailable, version, flow, phase, bytes, total, dismissed)
}
