# Changelog

Todas as mudanças relevantes deste projeto são documentadas neste arquivo seguindo o formato [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o padrão [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.1.0] - 2026-11-13

### Adicionado
- **Timer de 15s e alerta háptico no card flutuante (`OverlayService` + `HapticFeedbackHelper`)**: o card flutuante exibe barra de contagem regressiva de 15 segundos e fecha automaticamente ao terminar (pausando quando o motorista expande os detalhes). Corridas classificadas como **Aceitar (Verde)** disparam vibração discreta (`VIBRATE`).
- **Meta diária de lucro (`DailyGoalCalculator`)**: novo campo configurável de meta diária (padrão `R$ 250,00`) na aba Metas, com barra de progresso na tela inicial e no painel de Estatísticas.
- **Filtro por aplicativo no Histórico (`HistoryAppFilter`)**: chips para filtrar corridas e estatísticas por `Todos`, `Uber`, `99`, `inDrive` ou `Simulador`.
- **Tela de Diagnóstico (`DiagnosticActivity`)**: ambiente offline para acompanhar o status da última leitura do serviço de acessibilidade em memória e testar textos de ofertas para Uber, 99 e inDrive.
- **Testes automatizados no CI**: etapa `./gradlew testDebugUnitTest` integrada ao workflow `.github/workflows/build-apk.yml`.

### Corrigido
- Eliminados os avisos de depreciação de `Locale` (migrado para `Locale.forLanguageTag("pt-BR")`) e anotações `@param:StringRes` (`KT-73255`) nos estados imutáveis de UI.

## [1.0.0] - 2026-11-12

### Adicionado
- **Motor de análise financeira (`RideAnalyzer`)**: cálculo de R$/km bruto, R$/h bruto, R$/km líquido, custo operacional real por km e lucro estimado considerando distância e tempo de deslocamento até o passageiro + corrida.
- **Semáforo de decisão**: classificação automática em **Aceitar** (`ACCEPT`), **Avaliar** (`CAUTION`, até 20% abaixo das metas com lucro positivo) e **Recusar** (`REJECT`).
- **Captura em segundo plano (`RideAccessibilityService`)**: leitura de ofertas na tela para os pacotes oficiais de Uber Driver, 99 Motorista e inDrive com parsers dedicados (`UberParser`, `App99Parser`, `InDriveParser`).
- **Card flutuante (`OverlayService`)**: exibição em tempo real sobre outros apps via `WindowManager`, com arraste, toque para expandir/recolher, fechamento automático e registro de decisão.
- **Notificação persistente de monitoramento (`MonitoringNotificationHelper`)**: atalhos rápidos para abrir o app e pausar/retomar a exibição automática.
- **Configurações locais (`SettingsRepository` + `SettingsActivity`)**: persistência em DataStore Preferences para preço do combustível, consumo médio (km/L), manutenção por km, depreciação por km e metas mínimas de R$/km e R$/h.
- **Histórico e estatísticas (`RotaProDatabase` + `HistoryActivity`)**: armazenamento offline em Room (SQLite) com resumo de corridas aceitas, recusadas, avaliadas, ganho bruto e lucro estimado.
- **Onboarding e tela Sobre (`OnboardingActivity`, `AccessibilityGuideActivity`, `AboutActivity`)**: fluxo inicial em 3 passos, guia de permissões Android e tela com versão dinâmica (`BuildConfig.VERSION_NAME` / `VERSION_CODE`), privacidade local e atalho para rever o tutorial.
- **Testes automatizados**: suíte unitária e de UI (JUnit, MockK, Coroutines Test, Robolectric e Espresso) cobrindo motor de cálculo, parsers, banco Room, notificação, onboarding, formulário principal e tela Sobre.
- **CI/CD no GitHub Actions (`.github/workflows/build-apk.yml`)**: workflow com JDK 17, restauração da keystore de debug e geração automática do artefato `app-debug.apk`.

### Alterado
- **Build sem dependência obrigatória de Firebase**: plugin `com.google.gms.google-services` mantido comentado em `app/build.gradle.kts` para permitir compilação limpa offline e em pipelines de CI sem `google-services.json`.
- **Versionamento inicial**: definido `versionCode = 1` e `versionName = "1.0.0"` em `app/build.gradle.kts`.
