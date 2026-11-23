package com.bara.chessclock

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import kotlin.math.abs

/**
 * Device/emulator level flow tests: real Activity, real ViewModel, real SharedPreferences.
 *
 * Two environment facts drive the shape of these tests:
 *  1. The running clock schedules a frame every 80 ms, so the compose test clock is driven manually
 *     (`autoAdvance = false`) and every interaction is followed by one frame.
 *  2. The clock faces use `semantics(mergeDescendants = true)` (one TalkBack node per face), so the
 *     digits inside them are only visible in the *unmerged* tree. [inner] is used for those.
 *
 * Elapsed-time assertions use tolerances; the exact arithmetic is proven by the JVM tests in
 * `app/src/test/java/com/bara/chessclock/core`.
 */
@RunWith(AndroidJUnit4::class)
class ClockFlowTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    /** Clears the snapshot before the Activity launches, so tests never inherit a game. */
    class ClearSnapshotRule : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                context.getSharedPreferences("chess_clock", Context.MODE_PRIVATE)
                    .edit().clear().commit()
                base.evaluate()
            }
        }
    }

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(ClearSnapshotRule()).around(composeRule)

    @Before
    fun disableAutoAdvance() {
        composeRule.mainClock.autoAdvance = false
    }

    // ------------------------------------------------------------------ helpers

    /** A node in the merged tree: faces, buttons, screens. */
    private fun merged(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** A node inside a merging parent (digits, state labels, hints). */
    private fun inner(tag: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun textOf(tag: String): String {
        val config = inner(tag).fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.Text)) {
            config[SemanticsProperties.Text].joinToString("") { it.text }
        } else {
            ""
        }
    }

    private fun descriptionOf(tag: String): String {
        val config = merged(tag).fetchSemanticsNode().config
        return if (config.contains(SemanticsProperties.ContentDescription)) {
            config[SemanticsProperties.ContentDescription].joinToString(" ")
        } else {
            ""
        }
    }

    private fun click(tag: String) {
        merged(tag).performClick()
        pump()
    }

    private fun pump() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun startGame(presetTag: String) {
        click(presetTag)
        click("startGame")
        merged("screenBoard").assertExists()
    }

    /**
     * Replaces the content of a number field and closes the soft keyboard.
     *
     * Closing it is required, not cosmetic: with `adjustResize` the IME covers the "进入对局" button,
     * so a tap injected at the button's coordinates lands on the keyboard and the game never starts.
     * (This is what made customFiveMinutes_showsFiveMinutes fail on the emulator.)
     */
    private fun typeInto(tag: String, value: String) {
        merged(tag).performTextClearance()
        if (value.isNotEmpty()) merged(tag).performTextInput(value)
        Espresso.closeSoftKeyboard()
        pump()
    }

    private fun parseClock(text: String): Long {
        val minutes = text.substringBefore(':').toLong()
        val rest = text.substringAfter(':')
        val seconds = rest.substringBefore('.').toLong()
        val tenths = rest.substringAfter('.', "0").take(1).toLong()
        return minutes * 60_000 + seconds * 1_000 + tenths * 100
    }

    /** True when a node with [tag] exists anywhere in the unmerged tree. */
    private fun exists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // ------------------------------------------------------------------ C. settings

    @Test
    fun oneMinutePreset_startsAReadyGameShowingOneMinute() {
        startGame("preset1")
        assertEquals("1:00", textOf("clockP1Time"))
        assertEquals("1:00", textOf("clockP2Time"))
        assertEquals("0", textOf("moves"))
        assertTrue(textOf("statusText").contains("点击任一方表盘开始"))
    }

    @Test
    fun threePlusTwoPreset_explainsTheIncrementBeforeAndAfterStarting() {
        click("preset3plus2")
        inner("incrementExplanation").assertExists()
        assertTrue(textOf("settingsSummary").contains("+2"))
        click("startGame")
        assertEquals("3:00", textOf("clockP1Time"))
        assertTrue(textOf("incrementHint").contains("+2"))
        assertTrue(textOf("statusText").contains("每步 +2 秒"))
    }

    @Test
    fun customFiveMinutes_showsFiveMinutes() {
        // AppForge AF-2/2-1: type 5 minutes, save, the clock reads 5:00.
        typeInto("minutesField", "5")
        typeInto("secondsField", "0")
        click("startGame")
        assertEquals("5:00", textOf("clockP1Time"))
        assertEquals("5:00", textOf("clockP2Time"))
    }

    @Test
    fun emptyDuration_isRejectedAndStaysOnSettings() {
        typeInto("minutesField", "")
        typeInto("secondsField", "")
        click("startGame")
        merged("screenSettings").assertExists()
        assertEquals(0, composeRule.onAllNodesWithTag("screenBoard").fetchSemanticsNodes().size)
        val message = textOf("settingsSummary")
        assertTrue("unexpected message: $message", message.contains("请填写") || message.contains("至少"))
    }

    @Test
    fun outOfRangeDuration_isRejectedWithTheLimitInTheMessage() {
        typeInto("minutesField", "61")
        click("startGame")
        merged("screenSettings").assertExists()
        assertTrue(textOf("settingsSummary").contains("60"))
    }

    // ------------------------------------------------------------------ A/B. clock semantics

    @Test
    fun firstTap_startsThatSideAndKeepsMovesAtZero() {
        startGame("preset3plus2")
        click("clockP2")
        advance(100)
        assertEquals("0", textOf("moves"))
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
        // the running face says so in words, not only by colour
        assertTrue(descriptionOf("clockP2").contains("计时中"))
        assertTrue(descriptionOf("clockP1").contains("未计时"))
    }

    @Test
    fun handover_countsOneMoveAndShowsTheIncrement() {
        startGame("preset3plus2")
        click("clockP2")
        advance(1_000)
        click("clockP1")
        advance(100)

        assertEquals("1", textOf("moves"))
        assertTrue(textOf("statusText").contains("玩家一 行棋中"))
        // The "+2 秒" flash is time-limited (1.6 s of wall clock), so presence is asserted instead of
        // relying on a frame-by-frame capture; the JVM tests pin the flash window exactly.
        assertTrue(exists("incrementFlashP2") || exists("incrementFlashP1"))
    }

    @Test
    fun repeatedTapsOnTheRunningSide_doNothing() {
        startGame("preset3plus2")
        click("clockP2")
        advance(200)
        repeat(3) { click("clockP2") }
        advance(100)
        assertEquals("0", textOf("moves"))
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
    }

    @Test
    fun pause_freezesBothReadingsAndResumeContinuesTheSameSide() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        click("pauseResume")

        val frozenOne = textOf("clockP1Time")
        val frozenTwo = textOf("clockP2Time")
        advance(2_000) // nothing may change while paused
        assertEquals(frozenOne, textOf("clockP1Time"))
        assertEquals(frozenTwo, textOf("clockP2Time"))
        assertTrue(textOf("statusText").contains("已暂停"))

        click("pauseResume")
        advance(100)
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
        assertEquals("0", textOf("moves"))
    }

    // ------------------------------------------------------------------ reset flow

    @Test
    fun reset_dialogStatesTheExactConsequenceAndFreezesTime() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        click("reset")

        merged("resetDialog").assertExists()
        composeRule.onNodeWithText("双方恢复至 5:00，步数归零。", substring = true).assertExists()

        val frozen = textOf("clockP2Time")
        advance(3_000) // reading the dialog costs no time
        assertEquals(frozen, textOf("clockP2Time"))
        click("resetCancel")
    }

    @Test
    fun resetCancelled_keepsTheGameAndResumesTheRunningSide() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        click("clockP1")
        advance(100)
        val before = textOf("clockP2Time")

        click("reset")
        click("resetCancel")
        advance(100)
        assertEquals(before, textOf("clockP2Time")) // no time lost while the dialog was open
        assertEquals("1", textOf("moves"))
        assertTrue(textOf("statusText").contains("玩家一 行棋中"))
    }

    @Test
    fun resetConfirmed_returnsToFiveMinutesWithZeroMoves() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        click("clockP1")
        advance(100)
        assertEquals("1", textOf("moves"))

        click("reset")
        click("resetConfirm")
        advance(100)

        assertEquals("5:00", textOf("clockP1Time"))
        assertEquals("5:00", textOf("clockP2Time"))
        assertEquals("0", textOf("moves"))
        assertTrue(textOf("statusText").contains("点击任一方表盘开始"))
    }

    @Test
    fun changingTheTimeControlMidGame_asksBeforeDiscardingTheGame() {
        startGame("preset5")
        click("clockP2")
        advance(500)
        click("clockP1")
        advance(100)

        click("settings")
        click("preset3plus2")
        click("startGame")

        merged("newGameDialog").assertExists()
        click("newGameCancel")
        // cancelling returns to settings (that is where the prompt was raised) with the old game intact
        merged("screenSettings").assertExists()
        click("backToBoard")
        assertEquals("1", textOf("moves")) // the old game survived the cancelled prompt

        click("settings")
        click("preset3plus2")
        click("startGame")
        click("newGameConfirm")
        advance(100)
        assertEquals("3:00", textOf("clockP1Time"))
        assertEquals("0", textOf("moves"))
    }

    // ------------------------------------------------------------------ D. recovery & platform

    @Test
    fun activityRecreation_keepsTheGameAndTheRunningSide() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        val before = parseClock(textOf("clockP2Time"))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        advance(100)

        assertEquals("0", textOf("moves"))
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
        val after = parseClock(textOf("clockP2Time"))
        // exactly one deduction: the reading may only move by the real seconds that passed
        assertTrue("reading jumped from $before to $after", abs(before - after) < 5_000)
    }

    @Test
    fun stopAndRestart_restoresFromTheSnapshot() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // -> ON_STOP saves
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        advance(100)

        assertEquals("0", textOf("moves"))
        val reading = parseClock(textOf("clockP2Time"))
        assertTrue("unexpected reading $reading", reading in 290_000..300_000)
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
    }

    @Test
    fun screenIsKeptOnWhileRunningAndReleasedWhenPaused() {
        startGame("preset5")
        val flags = { composeRule.activity.window.attributes.flags }
        assertEquals(0, flags() and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        click("clockP2")
        advance(100)
        assertTrue(flags() and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)

        click("pauseResume")
        advance(100)
        assertEquals(0, flags() and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test
    fun backFromTheBoard_goesToSettingsAndPausesTheClock() {
        startGame("preset5")
        click("clockP2")
        advance(1_000)
        val before = textOf("clockP2Time")

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        pump()

        merged("screenSettings").assertExists()
        click("backToBoard")
        advance(100)
        assertEquals(before, textOf("clockP2Time")) // time spent in settings is not charged
        // returning to the board resumes the side that was running before settings was opened
        assertTrue(textOf("statusText").contains("玩家二 行棋中"))
    }
}
