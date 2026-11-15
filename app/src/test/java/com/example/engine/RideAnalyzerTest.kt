package com.example.engine

import com.example.model.DriverSettings
import com.example.model.PaymentMethod
import com.example.model.RideAlertType
import com.example.model.RideOffer
import com.example.model.RideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testes unitários puros para o motor [RideAnalyzer].
 * Cobre os 4 cenários principais (Aceitar, Avaliar, Recusar, Prejuízo), filtros pagos e casos de borda.
 */
class RideAnalyzerTest {

    private lateinit var analyzer: RideAnalyzer
    private lateinit var defaultSettings: DriverSettings

    @Before
    fun setUp() {
        analyzer = RideAnalyzer()
        // Custo/km = (6.00 / 10.0) + 0.15 + 0.20 = 0.60 + 0.35 = R$ 0,95/km
        defaultSettings = DriverSettings(
            fuelPrice = 6.00,
            consumption = 10.0,
            maintenance = 0.15,
            depreciation = 0.20,
            targetPerKm = 2.00,
            targetPerHour = 60.00
        )
    }

    @Test
    fun `cenario 1 - aceitar quando R$ por km, R$ por hora e lucro atingem as metas`() {
        val offer = RideOffer(
            fare = 30.00,
            pickupKm = 1.0,
            pickupMin = 3.0,
            tripKm = 9.0,
            tripMin = 22.0
        )

        val result = analyzer.analyze(offer, defaultSettings)

        assertEquals(RideVerdict.ACCEPT, result.verdict)
        assertEquals(3.00, result.grossPerKm, 0.001)
        assertEquals(72.00, result.grossPerHour, 0.001)
        assertEquals(20.50, result.profit, 0.001)
        assertEquals(2.05, result.netPerKm, 0.001)
    }

    @Test
    fun `cenario 2 - avaliar quando rentabilidade esta ate 20 por cento abaixo das metas`() {
        val offer = RideOffer(
            fare = 18.00,
            pickupKm = 1.5,
            pickupMin = 4.0,
            tripKm = 8.5,
            tripMin = 16.0
        )

        val result = analyzer.analyze(offer, defaultSettings)

        assertEquals(RideVerdict.CAUTION, result.verdict)
        assertEquals(1.80, result.grossPerKm, 0.001)
        assertEquals(54.00, result.grossPerHour, 0.001)
        assertEquals(8.50, result.profit, 0.001)
        assertEquals(0.85, result.netPerKm, 0.001)
    }

    @Test
    fun `cenario 3 - recusar quando alguma meta fica mais de 20 por cento abaixo mesmo com lucro`() {
        val offer = RideOffer(
            fare = 14.00,
            pickupKm = 2.0,
            pickupMin = 6.0,
            tripKm = 8.0,
            tripMin = 24.0
        )

        val result = analyzer.analyze(offer, defaultSettings)

        assertEquals(RideVerdict.REJECT, result.verdict)
        assertEquals(1.40, result.grossPerKm, 0.001)
        assertEquals(28.00, result.grossPerHour, 0.001)
        assertTrue(result.profit > 0.0)
    }

    @Test
    fun `cenario 4 - prejuizo quando custo operacional supera valor da corrida`() {
        val lowTargetSettings = defaultSettings.copy(
            targetPerKm = 0.50,
            targetPerHour = 15.00
        )
        val offer = RideOffer(
            fare = 9.00,
            pickupKm = 3.0,
            pickupMin = 5.0,
            tripKm = 9.0,
            tripMin = 15.0
        )

        val result = analyzer.analyze(offer, lowTargetSettings)

        assertEquals(RideVerdict.REJECT, result.verdict)
        assertEquals(-2.40, result.profit, 0.001)
        assertEquals(-0.20, result.netPerKm, 0.001)
    }

    @Test
    fun `caso de borda - distancia ou tempo zerados retornam REJECT sem NaN`() {
        val offer = RideOffer(
            fare = 20.00,
            pickupKm = 0.0,
            pickupMin = 0.0,
            tripKm = 0.0,
            tripMin = 0.0
        )

        val result = analyzer.analyze(offer, defaultSettings)

        assertEquals(RideVerdict.REJECT, result.verdict)
        assertEquals(0.0, result.grossPerKm, 0.001)
        assertEquals(0.0, result.grossPerHour, 0.001)
        assertEquals(0.0, result.netPerKm, 0.001)
    }

    @Test
    fun `area bloqueada forca REJECT mesmo quando tarifa supera metas sem acento ou maiusculas`() {
        val settings = defaultSettings.copy(blockedAreas = "Consolação, Área Rural")
        val offer = RideOffer(
            fare = 35.00,
            pickupKm = 1.0,
            pickupMin = 3.0,
            tripKm = 8.0,
            tripMin = 18.0,
            routeSummary = "Rua Augusta - CONSOLACAO"
        )

        val result = analyzer.analyze(offer, settings)

        assertEquals(RideVerdict.REJECT, result.verdict)
        assertTrue(RideAlertType.BLOCKED_AREA in result.alerts)
        assertEquals("Consolação", result.blockedAreaMatch)
    }

    @Test
    fun `filtros de valor minimo, busca maxima e nota minima forcam REJECT`() {
        val settings = defaultSettings.copy(
            minFare = 12.0,
            maxPickupKm = 2.0,
            minPassengerRating = 4.70
        )
        val offer = RideOffer(
            fare = 10.00,
            pickupKm = 2.5,
            pickupMin = 6.0,
            tripKm = 2.0,
            tripMin = 6.0,
            passengerRating = 4.50
        )

        val result = analyzer.analyze(offer, settings)

        assertEquals(RideVerdict.REJECT, result.verdict)
        assertTrue(RideAlertType.BELOW_MIN_FARE in result.alerts)
        assertTrue(RideAlertType.LONG_PICKUP in result.alerts)
        assertTrue(RideAlertType.LOW_RATING in result.alerts)
    }

    @Test
    fun `dinheiro ou parada extra rebaixam corrida verde para CAUTION com alertas`() {
        val offer = RideOffer(
            fare = 35.00,
            pickupKm = 1.0,
            pickupMin = 3.0,
            tripKm = 8.0,
            tripMin = 18.0,
            paymentMethod = PaymentMethod.CASH,
            hasExtraStop = true
        )

        val result = analyzer.analyze(offer, defaultSettings)

        assertEquals(RideVerdict.CAUTION, result.verdict)
        assertTrue(RideAlertType.CASH_PAYMENT in result.alerts)
        assertTrue(RideAlertType.EXTRA_STOP in result.alerts)
    }
}
