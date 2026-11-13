package com.example.parser

/**
 * Seleciona automaticamente o parser correspondente ao aplicativo em primeiro plano.
 */
class RideParserRegistry(
    private val parsers: Map<SupportedApp, RideOfferParser> = mapOf(
        SupportedApp.UBER to UberParser(),
        SupportedApp.APP_99 to App99Parser(),
        SupportedApp.INDRIVE to InDriveParser()
    )
) {

    fun isSupportedPackage(packageName: String?): Boolean {
        return SupportedApp.fromPackageName(packageName) != null
    }

    /**
     * Tenta interpretar os textos capturados para o pacote informado.
     * Retorna null para pacotes desconhecidos ou telas sem oferta válida.
     */
    fun parse(packageName: String?, nodesText: List<String>): ParsedRideOffer? {
        val app = SupportedApp.fromPackageName(packageName) ?: return null
        val parser = parsers[app] ?: return null
        val offer = parser.parse(nodesText) ?: return null
        return ParsedRideOffer(app = app, offer = offer)
    }
}
