package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes unitários dos parsers de tela (Uber, 99, inDrive) e do roteador [RideParserRegistry],
 * incluindo casos negativos para evitar falsos positivos.
 */
class RideParsersTest {

    private val registry = RideParserRegistry()

    @Test
    fun `UberParser extrai oferta completa com busca e viagem em minutos e km`() {
        val nodes = listOf(
            "UberX",
            "R$ 24,50",
            "Inclui + R$ 3,00",
            "R$ 2,52/km",
            "4 min (1,2 km) de distância",
            "16 min (8,5 km) de viagem",
            "Aceitar"
        )

        val parsed = registry.parse("com.ubercab.driver", nodes)

        assertNotNull(parsed)
        assertEquals(SupportedApp.UBER, parsed!!.app)
        assertEquals(24.50, parsed.offer.fare, 0.001)
        assertEquals(1.2, parsed.offer.pickupKm, 0.001)
        assertEquals(4.0, parsed.offer.pickupMin, 0.001)
        assertEquals(8.5, parsed.offer.tripKm, 0.001)
        assertEquals(16.0, parsed.offer.tripMin, 0.001)
    }

    @Test
    fun `UberParser converte metros corretamente quando busca vem apenas em minutos`() {
        val nodes = listOf(
            "Comfort",
            "R$ 31,00",
            "3 min",
            "18 min (900 m)",
            "Aceitar"
        )

        val offer = UberParser().parse(nodes)

        assertNotNull(offer)
        assertEquals(31.00, offer!!.fare, 0.001)
        assertEquals(0.0, offer.pickupKm, 0.001)
        assertEquals(3.0, offer.pickupMin, 0.001)
        assertEquals(0.9, offer.tripKm, 0.001)
        assertEquals(18.0, offer.tripMin, 0.001)
    }

    @Test
    fun `App99Parser extrai card rotulado com metros na busca e km na viagem`() {
        val nodes = listOf(
            "99Pop",
            "R$ 19,80",
            "Embarque: 800 m · 3 min",
            "Viagem: 7,5 km · 16 min",
            "Aceitar corrida"
        )

        val parsed = registry.parse("com.taxis99.driver", nodes)

        assertNotNull(parsed)
        assertEquals(SupportedApp.APP_99, parsed!!.app)
        assertEquals(19.80, parsed.offer.fare, 0.001)
        assertEquals(0.8, parsed.offer.pickupKm, 0.001)
        assertEquals(3.0, parsed.offer.pickupMin, 0.001)
        assertEquals(7.5, parsed.offer.tripKm, 0.001)
        assertEquals(16.0, parsed.offer.tripMin, 0.001)
    }

    @Test
    fun `InDriveParser extrai oferta com pares e com busca em metros isolada`() {
        val nodesWithPairs = listOf(
            "inDrive",
            "R$ 28,00",
            "~1,4 km (5 min)",
            "11,2 km (22 min)",
            "Aceitar"
        )
        val parsed = registry.parse("sinet.startup.inDriver", nodesWithPairs)

        assertNotNull(parsed)
        assertEquals(SupportedApp.INDRIVE, parsed!!.app)
        assertEquals(28.00, parsed.offer.fare, 0.001)
        assertEquals(1.4, parsed.offer.pickupKm, 0.001)
        assertEquals(5.0, parsed.offer.pickupMin, 0.001)
        assertEquals(11.2, parsed.offer.tripKm, 0.001)
        assertEquals(22.0, parsed.offer.tripMin, 0.001)

        val nodesWithoutPickupTime = listOf(
            "R$ 26,00",
            "900 m",
            "9,1 km",
            "22 min"
        )
        val offer = InDriveParser().parse(nodesWithoutPickupTime)
        assertNotNull(offer)
        assertEquals(0.9, offer!!.pickupKm, 0.001)
        assertEquals(9.1, offer.tripKm, 0.001)
        assertEquals(22.0, offer.tripMin, 0.001)
    }

    @Test
    fun `fallback seguro retorna null para telas de ganhos, dados incompletos ou app desconhecido`() {
        val earningsScreen = listOf(
            "Ganhos de hoje",
            "R$ 245,00",
            "12 viagens",
            "4 h 20 min"
        )
        assertNull(UberParser().parse(earningsScreen))

        val missingTime = listOf("R$ 22,00", "8,5 km")
        assertNull(App99Parser().parse(missingTime))
        assertNull(InDriveParser().parse(missingTime))

        val validUberNodes = listOf("R$ 25,00", "4 min (1,0 km)", "15 min (8,0 km)")
        assertNull(registry.parse("com.whatsapp", validUberNodes))
    }
}
