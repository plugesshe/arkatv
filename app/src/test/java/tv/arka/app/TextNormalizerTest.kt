package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.arka.app.data.TextNormalizer

class TextNormalizerTest {
    @Test
    fun removesAccentsCaseAndPunctuation() {
        assertEquals("les heritiers du vent", TextNormalizer.normalize("Les Héritiers du Vent"))
        assertEquals("l ete 2 le retour", TextNormalizer.normalize("  L'Été 2 : Le Retour!  "))
        assertEquals("canal hd", TextNormalizer.normalize("CANAL+ [HD]"))
        assertEquals("", TextNormalizer.normalize(" -- "))
    }

    @Test
    fun matchesEveryTermInAnyOrder() {
        val terms = TextNormalizer.terms(TextNormalizer.normalize("vent héritiers"))
        assertTrue(TextNormalizer.matches("Les Héritiers du Vent", terms))
        assertFalse(TextNormalizer.matches("Les Héritiers", terms))
    }
}
