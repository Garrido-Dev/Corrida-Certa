# Corrida Certa

Aplicativo Android para motoristas de aplicativo calcularem, em segundos, se uma oferta de corrida compensa antes de aceitar. O app cruza o valor oferecido com o trajeto completo (deslocamento até o passageiro + corrida) e aplica o custo real do veículo para entregar um veredito direto no formato de semáforo.

Todo o processamento roda localmente no aparelho. Não há envio de corridas, valores ou telas lidas para servidores externos.

---

## O problema que resolve

Na tela de chamada da Uber, 99 ou inDrive, o motorista tem poucos segundos para decidir olhando apenas o valor bruto e números separados de embarque e viagem. Olhar só o valor da corrida engana: uma corrida de R$ 18,00 com 4 km de deslocamento vazio e 25 minutos no trânsito pode dar menos que a meta horária ou até gerar prejuízo depois de combustível, manutenção e depreciação.

O **Corrida Certa** resolve isso de duas formas:

1. **Simulação manual** na tela principal para testar cenários e conferir valores.
2. **Leitura automática em segundo plano** via Serviço de Acessibilidade do Android + card flutuante sobre a tela do app de corridas.

---

## Regras de cálculo e semáforo

As fórmulas ficam isoladas em `RideAnalyzer.kt` (sem dependência do framework Android):

### 1. Custo operacional por km (`DriverSettings.costPerKm`)

$$\text{custoKm} = \left(\frac{\text{preçoCombustível}}{\text{consumoKmPorLitro}}\right) + \text{manutençãoPorKm} + \text{depreciaçãoPorKm}$$

Valores padrão na primeira instalação (editáveis em **Configurações**):
- Combustível: `R$ 5,89 / L`
- Consumo médio: `10,0 km/L`
- Manutenção: `R$ 0,15 / km`
- Depreciação: `R$ 0,20 / km`
- Meta mínima por km: `R$ 2,00 / km`
- Meta mínima por hora: `R$ 50,00 / h`

### 2. Métricas da corrida

- **Distância total**: `pickupKm + tripKm`
- **Tempo total**: `pickupMin + tripMin`
- **Custo estimado**: `totalKm * costPerKm`
- **Lucro líquido estimado**: `fare - totalCost`
- **Bruto por km**: `fare / totalKm`
- **Bruto por hora**: `fare / (totalMin / 60)`
- **Líquido por km**: `profit / totalKm`

### 3. Classificação do semáforo

| Veredito | Condição |
| :--- | :--- |
| **Aceitar** (`ACCEPT`) | Lucro > 0 **e** `R$/km >= metaKm` **e** `R$/h >= metaHora` |
| **Avaliar** (`CAUTION`) | Lucro > 0 **e** ambos os indicadores estão dentro de até 20% abaixo da meta (`>= 80%` de cada meta) |
| **Recusar** (`REJECT`) | Lucro `<= 0`, entrada inválida ou qualquer indicador abaixo de 80% da meta |

---

## Apps suportados na captura automática

O `RideAccessibilityService` é filtrado no manifesto (`accessibility_service_config.xml`) apenas para os pacotes de motorista suportados, evitando leitura desnecessária de outros apps:

| Aplicativo | Pacotes monitorados | Parser |
| :--- | :--- | :--- |
| **Uber Driver** | `com.ubercab.driver` | `UberParser` |
| **99 Motorista** | `com.taxis99.driver`, `com.didi.brazil.driver` | `App99Parser` |
| **inDrive** | `sinet.startup.inDriver`, `com.indrive.driver` | `InDriveParser` |

Os textos capturados passam por `RideParserRegistry`, que escolhe o parser pelo `packageName` em primeiro plano. Como os layouts dos apps parceiros mudam com frequência, cada parser extrai valores monetários (`R$`), distâncias (`km` / `m`) e tempos (`min` / `h`) com tolerância a variações de formatação e ordem dos nós na árvore de acessibilidade.

---

## Telas e fluxo do app

- **Onboarding (`OnboardingActivity`)**: apresentação em 3 etapas na primeira abertura (ou reaberta pela tela Sobre).
- **Tela principal (`MainActivity`)**:
  - Card do semáforo com resultado detalhado (R$/km, R$/h, custo e lucro).
  - Card de progresso da **Meta diária de lucro** calculada sobre as corridas viáveis de hoje.
  - Barra de status das permissões de **Sobreposição** e **Acessibilidade**, além do teste rápido do card flutuante (que fecha automaticamente após 15 segundos e vibra discretamente em ofertas verdes).
  - Formulário manual (valor, distância/tempo até o embarque e distância/tempo da viagem).
  - Acesso direto ao **Histórico**, **Configurações** e **Sobre** (tocando no selo de versão no topo).
- **Configurações (`SettingsActivity`)**: ajuste de combustível, consumo, manutenção, depreciação, metas (`R$/km`, `R$/h` e meta diária em `R$`) e atalho para **Sobre o aplicativo**.
- **Guia de Acessibilidade (`AccessibilityGuideActivity`)**: passo a passo objetivo para habilitar o serviço nas configurações do Android e acesso ao **Diagnóstico de leitura**.
- **Histórico (`HistoryActivity`)**: lista de corridas analisadas com filtro por período (`Hoje`, `7 dias`, `30 dias`) e por aplicativo (`Todos`, `Uber`, `99`, `inDrive`, `Simulador`), progresso da meta diária e resumo de totais.
- **Diagnóstico (`DiagnosticActivity`)**: exibe o status volátil da última leitura em memória e permite colar ou simular textos de ofertas para validar os parsers offline.
- **Sobre (`AboutActivity`)**: exibe versão e build (`BuildConfig.VERSION_NAME` / `VERSION_CODE`), `applicationId`, resumo de privacidade offline e atalhos para tutorial e diagnóstico.

---

## Arquitetura e organização do código

Arquitetura MVVM com camadas separadas por responsabilidade dentro de `app/src/main/java/com/example/`:

```text
com/example/
├── data/
│   ├── HistoryRepository.kt          # Acesso ao histórico e cálculo de resumo
│   ├── SettingsRepository.kt         # DataStore Preferences para metas e custos
│   └── local/                        # Room Database, DAO, Entity e estatísticas
├── engine/
│   ├── RideAnalyzer.kt               # Regras puras do semáforo e métricas financeiras
│   ├── RideStatisticsCalculator.kt   # Agregados do histórico por período e app
│   └── DailyGoalCalculator.kt        # Cálculo de progresso da meta diária
├── model/
│   ├── DriverSettings.kt             # Custos do veículo e metas do motorista
│   ├── RideOffer.kt                  # Dados de entrada da oferta
│   ├── RideAnalysis.kt               # Resultado calculado
│   └── RideVerdict.kt                # Enum ACCEPT / CAUTION / REJECT
├── parser/
│   ├── RideOfferParser.kt            # Contrato e modelo ParsedRideOffer
│   ├── RideParserRegistry.kt         # Roteamento por packageName
│   ├── ParserUtils.kt                # Extração de R$, km/m e min/h
│   ├── UberParser.kt
│   ├── App99Parser.kt
│   └── InDriveParser.kt
├── service/
│   ├── RideAccessibilityService.kt   # Escuta janelas dos apps de corrida
│   ├── OverlayService.kt             # Card flutuante via WindowManager (timer 15s + Foreground Service)
│   ├── OverlayGestureHandler.kt      # Arraste e toque do card flutuante
│   ├── HapticFeedbackHelper.kt       # Vibração curta em ofertas verdes (ACCEPT)
│   ├── MonitoringNotificationHelper.kt
│   ├── AccessibilityStatusHelper.kt
│   ├── OverlayPermissionHelper.kt
│   └── RideMonitorState.kt
└── ui/
    ├── MainActivity.kt / MainViewModel.kt / MainUiState.kt
    ├── SettingsActivity.kt / SettingsViewModel.kt / SettingsUiState.kt
    ├── HistoryActivity.kt / HistoryViewModel.kt / HistoryUiState.kt / HistoryRideAdapter.kt
    ├── DiagnosticActivity.kt
    ├── OnboardingActivity.kt
    ├── AccessibilityGuideActivity.kt
    └── AboutActivity.kt
```

### Stack técnica

- **Linguagem**: Kotlin (`2.2.10`)
- **UI**: XML + ViewBinding + Material Components 3 (`1.12.0`) + SplashScreen API (`1.0.1`)
- **Persistência**:
  - **DataStore Preferences** (`1.1.7`) para configurações e flags de onboarding/monitoramento
  - **Room + KSP** (`2.7.0` / KSP `2.3.5`) para histórico local em SQLite
- **Assincronismo**: Kotlin Coroutines & Flow (`1.10.2`)
- **Build**: Android Gradle Plugin `9.1.1`, Gradle `9.3.1`, Java 11 bytecode (JDK 17 no CI)
- **SDKs**: `minSdk = 26` (Android 8.0) · `targetSdk = 34` · `compileSdk = 36`

---

## Permissões utilizadas

Declaradas em `app/src/main/AndroidManifest.xml`:

- `SYSTEM_ALERT_WINDOW`: necessária para desenhar o card flutuante por cima dos aplicativos de corrida.
- `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE`: mantém o serviço de overlay ativo com notificação visível enquanto o monitoramento está ligado.
- `POST_NOTIFICATIONS`: exibe a notificação persistente no Android 13+ com botões rápidos de pausa/retomada.
- `VIBRATE`: permissão normal usada para emitir alerta tátil discreto quando uma corrida verde (`ACCEPT`) aparece no overlay.
- `BIND_ACCESSIBILITY_SERVICE`: vinculada exclusivamente ao `RideAccessibilityService`, restrita aos pacotes de Uber, 99 e inDrive.

O app **não solicita** internet, localização em segundo plano, câmera ou acesso a arquivos de mídia.

---

## Como compilar e rodar os testes

Pré-requisitos: **JDK 17** e **Android SDK** configurados (`ANDROID_HOME` ou `ANDROID_SDK_ROOT`).

### Gerar APK de debug

```bash
chmod +x gradlew
./gradlew assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`.

> **Sobre a keystore de debug**: o `build.gradle.kts` assina builds de debug com `debug.keystore` na raiz. Caso o arquivo ainda não exista localmente (ele fica fora do Git por segurança), restaure a partir de `debug.keystore.base64` antes de compilar:
>
> ```bash
> base64 -d debug.keystore.base64 > debug.keystore
> ```

### Rodar a suíte de testes locais

Os testes de lógica, banco Room e interface (Espresso sobre Robolectric) rodam direto na JVM, sem precisar de emulador:

```bash
./gradlew testDebugUnitTest
```

Cobertura atual em `app/src/test/java/com/example/`:
- `engine/RideAnalyzerTest.kt` e `DailyGoalCalculatorTest.kt`: fórmulas de lucro, custo/km, transições do semáforo e meta diária.
- `parser/*Test.kt`: extração de ofertas da Uber, 99, inDrive e roteamento no registro.
- `data/local/RotaProDatabaseTest.kt` e `RideStatisticsCalculatorTest.kt`: persistência Room e agregados.
- `service/MonitoringNotificationHelperTest.kt`: canal e ações da notificação persistente.
- `ui/MainActivityTest.kt`: validação Espresso do formulário manual e cálculo na tela.
- `ui/MainAndHistoryRobolectricTest.kt`, `OnboardingActivityTest.kt`, `AboutActivityTest.kt` e `DiagnosticActivityTest.kt`: fluxos de tela, histórico, versão dinâmica e diagnóstico offline.

---

## CI/CD (GitHub Actions)

O workflow `.github/workflows/build-apk.yml` executa automaticamente a cada `push` nas branches `main` ou `master` (ou disparo manual via `workflow_dispatch`):

1. Faz checkout do repositório e configura **JDK 17 (Temurin)** com cache do Gradle.
2. Restaura `debug.keystore` a partir de `debug.keystore.base64` (com fallback via `keytool` caso necessário).
3. Lê `versionName` e `versionCode` de `app/build.gradle.kts`.
4. Executa `./gradlew testDebugUnitTest` para garantir que toda a suíte de testes passe antes do empacotamento.
5. Executa `./gradlew assembleDebug` e renomeia o APK para `Corrida-Certa-v<versão>.apk`.
6. Publica o APK como artefato na aba *Actions* e **cria ou atualiza automaticamente a Release correspondente (`v<versão>`)** na aba **Releases** com as notas extraídas do `CHANGELOG.md`.

O plugin `com.google.gms.google-services` está comentado em `app/build.gradle.kts` porque o aplicativo não usa serviços do Firebase nem depende de `google-services.json` para compilar.

---

## Versionamento

O projeto adota **Versionamento Semântico (`MAJOR.MINOR.PATCH`)** configurado em `app/build.gradle.kts` (`versionCode` e `versionName`) e refletido automaticamente na tela principal e na tela **Sobre** via `BuildConfig`:

- **MAJOR**: mudanças incompatíveis de fluxo, banco ou estrutura principal.
- **MINOR**: novas funcionalidades compatíveis (ex.: suporte a novo app de corrida ou exportação de relatório).
- **PATCH**: correções de cálculo, parser ou interface sem quebra de comportamento.

Histórico detalhado de entregas em [CHANGELOG.md](CHANGELOG.md).

---

## Licença

Distribuído sob a **Licença MIT** em conjunto com o **Aviso Legal (Disclaimer)** de uso por conta e risco do usuário. Consulte [LICENSE](LICENSE) para mais detalhes.
