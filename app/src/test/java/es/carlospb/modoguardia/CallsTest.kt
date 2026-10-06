package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.CallEvaluation
import es.carlospb.modoguardia.domain.CallerId
import es.carlospb.modoguardia.domain.PhoneNumbers
import es.carlospb.modoguardia.domain.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {

    @Test
    fun `same number written in different ways`() {
        assertTrue(PhoneNumbers.same("+34 600 12 34 56", "600123456"))
        assertTrue(PhoneNumbers.same("0034600123456", "600 12 34 56"))
        assertTrue(PhoneNumbers.same("(91) 555-12-34", "915551234"))
        assertTrue(PhoneNumbers.same("‪+34 600 12 34 56‬", "+34600123456"))
    }

    @Test
    fun `different numbers are different`() {
        assertFalse(PhoneNumbers.same("600123456", "600123457"))
        assertFalse(PhoneNumbers.same("", "600123456"))
        // Los números cortos tienen que ser idénticos: 112 no es el final de otro número.
        assertTrue(PhoneNumbers.same("112", "112"))
        assertFalse(PhoneNumbers.same("1123", "112"))
        assertFalse(PhoneNumbers.same("600112", "34600112"))
    }

    @Test
    fun `number only detection and extraction`() {
        assertTrue(PhoneNumbers.isNumberOnly("+34 600 12 34 56"))
        assertFalse(PhoneNumbers.isNumberOnly("Carlos"))
        assertFalse(PhoneNumbers.isNumberOnly("Oficina 2"))
        assertEquals(listOf("34600123456"), PhoneNumbers.extract("Móvil · +34 600 12 34 56"))
        assertTrue(PhoneNumbers.extract("Llamada de 2 min").isEmpty())
        assertEquals(listOf("012"), PhoneNumbers.extract("012"))
    }

    @Test
    fun `caller from a contact name`() {
        val c = CallerId.from(dialerCall("Carlos Pérez", ts = 1))
        assertEquals(listOf("Carlos Pérez"), c.names)
        assertTrue(c.numbers.isEmpty())
    }

    @Test
    fun `caller from a number when it is not a contact`() {
        val c = CallerId.from(dialerCall("+34 600 12 34 56", ts = 1))
        assertTrue(c.names.isEmpty())
        assertEquals(listOf("34600123456"), c.numbers)
    }

    @Test
    fun `caller from the call person of a CallStyle notification`() {
        val e = dialerCall("Llamada entrante", ts = 1, text = null)
            .copy(personNames = listOf("Laura"), personNumbers = listOf("+34 611 22 33 44"))
        val c = CallerId.from(e)
        assertEquals(listOf("Laura"), c.names)
        assertEquals(listOf("34611223344"), c.numbers)
    }

    @Test
    fun `generic dialer texts are not names`() {
        assertTrue(CallerId.from(dialerCall("Número desconocido", ts = 1, text = null)).isEmpty)
        assertTrue(CallerId.from(dialerCall("Incoming call", ts = 1, text = null)).isEmpty)
        assertTrue(CallerId.from(dialerCall(null, ts = 1, text = null)).isEmpty)
    }

    @Test
    fun `call evaluation by name, alias and number`() {
        val carlos = callRule("Carlos Pérez", "+34 600 11 22 33", alt = "Pérez, Carlos")
        val empresa = numberRule("912345678", label = "Empresa")
        val rules = listOf(carlos, empresa)
        assertEquals(CallEvaluation.Match(carlos), RuleEngine.evaluateCall(CallerId(listOf("carlos perez"), emptyList()), rules))
        assertEquals(CallEvaluation.Match(carlos), RuleEngine.evaluateCall(CallerId(listOf("Pérez, Carlos"), emptyList()), rules))
        assertEquals(CallEvaluation.Match(empresa), RuleEngine.evaluateCall(CallerId(emptyList(), listOf("34912345678")), rules))
        assertEquals(CallEvaluation.NoMatch, RuleEngine.evaluateCall(CallerId(listOf("Pedro"), listOf("699999999")), rules))
        assertEquals(CallEvaluation.Unidentified, RuleEngine.evaluateCall(CallerId(emptyList(), emptyList()), rules))
        // La etiqueta de un número a mano no es un nombre de contacto: «Empresa» en pantalla no basta.
        assertEquals(CallEvaluation.Unidentified, RuleEngine.evaluateCall(CallerId(listOf("Empresa"), emptyList()), listOf(empresa)))
    }

    @Test
    fun `disabled or empty call rules never match`() {
        val disabled = callRule("Carlos", "600112233").copy(enabled = false)
        val empty = callRule(null)
        assertFalse(empty.isValid())
        assertEquals(CallEvaluation.NoMatch, RuleEngine.evaluateCall(CallerId(listOf("Carlos"), listOf("600112233")), listOf(disabled, empty)))
    }

    @Test
    fun `a contact is ruled out by another name, a typed number only by another number`() {
        val carlos = callRule("Carlos", "600112233")
        val empresa = numberRule("912345678", label = "Empresa")
        // Otro contacto: descarta a Carlos, pero no al número (no sabemos qué número llama).
        assertEquals(CallEvaluation.Unidentified, RuleEngine.evaluateCall(CallerId(listOf("Mamá"), emptyList()), listOf(carlos, empresa)))
        assertEquals(CallEvaluation.NoMatch, RuleEngine.evaluateCall(CallerId(listOf("Mamá"), emptyList()), listOf(carlos)))
        // Con el número, se descartan los dos.
        assertEquals(CallEvaluation.NoMatch, RuleEngine.evaluateCall(CallerId(emptyList(), listOf("611000000")), listOf(carlos, empresa)))
        // Nadie elegido: nunca despierta.
        assertEquals(CallEvaluation.NoMatch, RuleEngine.evaluateCall(CallerId(emptyList(), emptyList()), emptyList()))
    }
}
