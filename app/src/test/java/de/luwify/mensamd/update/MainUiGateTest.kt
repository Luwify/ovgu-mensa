package de.luwify.mensamd.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiGateTest {
    @Test fun aRotationIsTheSameVisit() {
        val gate = MainUiGate()
        assertTrue(gate.started())
        assertFalse(gate.started())
    }

    @Test fun comingBackIsANewVisit() {
        val gate = MainUiGate()
        gate.started()
        gate.appStopped()
        assertTrue(gate.started())
    }

    /** The popup only ever causes [MainUiGate.appStopped]: opening the app from it still counts as a new visit. */
    @Test fun aPopupInBetweenDoesNotUseUpTheVisit() {
        val gate = MainUiGate()
        gate.appStopped()
        assertTrue(gate.started())
    }
}
