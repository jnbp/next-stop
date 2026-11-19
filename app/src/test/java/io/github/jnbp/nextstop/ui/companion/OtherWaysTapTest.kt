package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.data.PickerMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #193: the connection picker under a blue change card of the Live-Fahrt opens and folds by its
 * chip (#243; the chip "Andere Wege" is tested in ChangeTileRowTest).
 */
class OtherWaysTapTest {
    @Test
    fun theConnectionPickerOpensAndFoldsByItsLink() {
        // #193: tapped open (choose), then folded (fold): the link turns ▴ then ▾ again, in both foldable modes.
        for (mode in listOf(PickerMode.Folded, PickerMode.NextOpen)) {
            val opened = pickerShow(3, over = false, active = activeChangeKeepLegs(3, listOf(1, 3), opened = 3), open = 3, mode = mode)
            assertEquals(PickerShow.Open, opened)
            val folded = pickerShow(3, over = false, active = activeChangeKeepLegs(3, listOf(1, 3), opened = null), open = null, mode = mode, folded = 3)
            assertEquals(PickerShow.Link, folded)
        }
    }
}
