package com.example.parser

import com.example.model.RideOffer

/**
 * Aplicativos de transporte suportados pelo leitor automático do Rota Pro.
 */
enum class SupportedApp(
    val displayName: String,
    val packageNames: Set<String>
) {
    UBER(
        displayName = "Uber",
        packageNames = setOf("com.ubercab.driver")
    ),
    APP_99(
        displayName = "99",
        packageNames = setOf("com.taxis99.driver", "com.didi.brazil.driver")
    ),
    INDRIVE(
        displayName = "inDrive",
        packageNames = setOf("sinet.startup.inDriver", "com.indrive.driver")
    );

    companion object {
        fun fromPackageName(packageName: String?): SupportedApp? {
            if (packageName.isNullOrBlank()) return null
            return entries.firstOrNull { packageName in it.packageNames }
        }
    }
}

/**
 * Resultado estruturado da leitura de uma tela de oferta de corrida.
 */
data class ParsedRideOffer(
    val app: SupportedApp,
    val offer: RideOffer
)

/**
 * Contrato comum para parsers específicos por aplicativo de transporte.
 */
interface RideOfferParser {
    val app: SupportedApp

    /**
     * Tenta extrair uma [RideOffer] válida a partir dos textos visíveis na janela ativa.
     * Retorna null caso a tela não seja uma oferta ou não contenha dados suficientes, evitando falsos positivos.
     */
    fun parse(nodesText: List<String>): RideOffer?
}
