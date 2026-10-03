# HomeCore 1.14.0 / API 1.9.0 — HomeLink UI Kit

## Français

Le langage visuel de Dashboard devient l'API cliente commune des interfaces
HomeLink. HomeCore fournit le cadre industriel graphite, les panneaux encastrés,
les boutons gris en relief, la sélection enfoncée, le focus cuivre et les tokens
de dimensions. Dashboard consomme désormais cette API comme les autres mods ;
HomeCore ne dépend pas de Dashboard et aucun consommateur n'a besoin de
Dashboard pour afficher son interface.

L'ajout est compatible et fait passer HomeCore de **1.13.0 à 1.14.0**, avec une
API publique **1.8.0 → 1.9.0**. Les versions des consommateurs restent celles
réellement présentes : Dashboard **1.6.0**, Storage **1.4.0**, Farm **1.5.0**,
Quarry **1.4.0**, Energy **0.5.0**, Tasks **0.2.0**. Tous déclarent HomeCore
**1.14.0** et la plage minimale **[1.14.0,2.0.0)**. Minecraft reste en **1.21.1**,
Java en **21**, NeoForge dans la branche **21.1.x**.

Les six anciennes paires `DashboardTheme`/`DashboardButton`,
`StorageTheme`/`StorageButton`, `FarmTheme`/`FarmButton`,
`QuarryTheme`/`QuarryButton`, `EnergyTheme`/`EnergyButton` et
`TaskTheme`/`TaskButton` sont supprimées. Il ne reste pas de wrapper de rendu
dupliqué. Les petits composants locaux `StorageStatusColors`,
`FarmStatusColors`, `QuarryStatusColors` et `TaskAvailabilityStyle` conservent
les états métier. `TaskItemButton` spécialise le contenu d'un `HomeLinkButton`
pour les icônes item, sans recopier son chrome.

Dashboard reste la référence visuelle : palette, cadre, boutons, onglets et
focus proviennent du code extrait. Storage couvre aussi les interfaces Pipes
existantes. Les écrans avec slots conservent leurs coordonnées fonctionnelles.
Les interfaces Terminal/Controller Storage et les cinq écrans Energy sans
slots adaptent leur surface à la fenêtre ; Energy fait défiler sa télémétrie
avec un scissor absolu et garde ses actions visibles. Les overlays monde Farm
et Quarry, l'eau Energy et les couleurs sémantiques Tasks restent propres à
leur fonction.

Les contrôles d'actions Dashboard emploient aussi les tokens partagés. Le menu
déroulant conserve sa hauteur fonctionnelle de 20 px, avec focus ambre,
scrollbar cuivre, ellipsis et tooltip complet. Cette normalisation de ses
anciens contrôles est une évolution visuelle volontaire ; le cadre et le bouton
de référence conservent leurs primitives historiques.

Le futur HomeLink Furnace peut utiliser directement `frame`, `panel`,
`HomeLinkButton.builder`, les tokens et `HomeLinkScreenLayout.fit` sans nouveau
thème. Il n'est pas créé dans ce chantier. Les futurs GUI Storage Pipes doivent
également utiliser le kit, comme les interfaces Pipes déjà migrées.

Tout le package `fr.lkdm.homecore.api.client.ui` est réservé au client. Une
classe commune ou serveur ne doit pas importer ses types, les exposer dans une
signature ou les charger indirectement. Les helpers n'enregistrent aucun event,
tick, réseau ou état métier. Le build composite utilise les sources locales ;
aucun package n'a été publié sur un registre distant dans ce chantier.

## English

Dashboard's visual language becomes the shared client API for HomeLink screens.
HomeCore owns the graphite industrial frame, recessed panels, raised grey
buttons, pressed selection, copper focus and dimension tokens. Dashboard now
uses the same API as every other consumer. HomeCore has no Dashboard dependency,
and consumers can display their UI without Dashboard installed.

This additive release moves HomeCore **1.13.0 → 1.14.0** and its public API
**1.8.0 → 1.9.0**. Consumer artifact versions remain Dashboard **1.6.0**,
Storage **1.4.0**, Farm **1.5.0**, Quarry **1.4.0**, Energy **0.5.0**, and Tasks
**0.2.0**. Each declares HomeCore **1.14.0**, with the minimum range
**[1.14.0,2.0.0)**. The platform remains Minecraft **1.21.1**, Java **21**, and
NeoForge **21.1.x**.

All six local Theme/Button pairs listed above are removed. Local status helpers
retain domain meaning; `TaskItemButton` extends the shared button label content
for item icons without duplicating its shell. Dashboard keeps the reference
appearance. Existing Storage Pipes screens also migrate. Inventory slots keep
their functional geometry. Slot-free Storage Terminal/Controller and Energy
screens fit the viewport; Energy scrolls telemetry while retaining fixed
actions. World overlays and domain-specific water/task colors stay local.

Dashboard action controls also use the shared tokens. The dropdown retains its
functional 20 px height, with amber focus, copper scrollbar, ellipsis and full
label tooltips. These action controls have intentional visual changes; the
reference frame and button retain the historical rendering primitives.

Future Furnace and Storage Pipes screens can use the public kit directly.
Furnace is not created here. The entire `fr.lkdm.homecore.api.client.ui` package
is client-only; common/server code must not import, expose or indirectly load
these types. Rendering helpers introduce no server ticking, networking or
business state. Composite builds consume local sources. No package was
published to a remote registry as part of this work.

## API publique / Public API

The package contains five public top-level types. Examples and detailed visual
rules are in [UI_STYLE.md](UI_STYLE.md); migration details are in
[UI_MIGRATION.md](UI_MIGRATION.md).

| Type | Contrat / Contract |
| --- | --- |
| `HomeLinkTheme` | Official colors; control 18 px; header/footer 29 px; padding 12 px; outer margin 8 px; default preferred maximum 520 × 340; typed and legacy string status mapping. |
| `HomeLinkStatusTone` | `ONLINE`, `WARNING`, `OFFLINE`, `NEUTRAL`. Domain state stays in the consumer. |
| `HomeLinkUi` | Stateless `frame`, configurable-header `window`, `panel`, `slot`, `separator`, `screw`, `mark`, `input`, `statusDot` (color or tone), bounded `progressBar`, 3 px `gauge`, and `clip`. `input` styles an existing vanilla EditBox without changing its bounds or responder. |
| `HomeLinkButton` | Vanilla Button narration and interaction; typed fluent builder; instance `selected`, `selectedWhen`, `navigation`; normal/hover/disabled/selected/focus rendering; centered ellipsis label; automatic full-label tooltip, updated when the message changes. Protected label rendering supports domain content extensions. |
| `HomeLinkScreenLayout` | Immutable centered rectangle from `fit(screenWidth, screenHeight, preferredWidth, preferredHeight)`; bounded header/footer and content coordinates; tiny viewports stay nonnegative. No mandatory base screen or fixed-size requirement. |

Selection/navigation are configured on the built button, for example
`HomeLinkButton.builder(label, action).bounds(x, y, width, 18).build().navigation(true)`.
`selectedWhen` must read pure client state and must not trigger network or
business actions. Status indicators need text, a symbol or a tooltip; color
alone must not convey state. Vanilla narration, logical tab order, visible
keyboard focus and translated FR/EN labels remain required.

## Validation — 2026-10-03

Les résultats ci-dessous proviennent des dernières passes locales exécutées sur
les sources de cette livraison. Les builds des sept dépôts sont réussis. Les
smokes ciblent les interfaces modifiées ; ils ne couvrent pas tous les profils
métier historiques disponibles. La CI distante HomeCore du commit initial du
kit et celles d'Energy, Storage, Farm, Quarry et Dashboard ont également réussi.
Les résultats distants restent distincts des validations locales ; Tasks est
encore en cours au moment de cette revue.

These results come from the latest local runs against this release's sources.
All seven repository builds passed. Targeted smokes cover the changed interfaces.
The initial HomeCore kit commit, Energy, Storage, Farm, Quarry and Dashboard also
passed remote CI. Remote results remain separate from local validation; Tasks
is still running at the time of this review.

| Dépôt / Repository | JUnit | Serveur dédié / Dedicated server | Dernier état client / Latest client state |
| --- | --- | --- | --- |
| HomeCore | 158 passed | Fresh write/read persistence pair: 16 GameTests per pass passed | Build, tests, Javadocs, local Maven staging and independent artifact resolution passed. EN/FR UI smoke passed at 640 × 360 and 427 × 240; workbench FR smoke passed. |
| Dashboard | 15 passed | 83 GameTests passed | Keyboard smoke passed in EN at 427 × 240 and FR at 854 × 480; native controls and complete focus cycles checked. Additional FR Home/Settings/editor and Alerts smokes passed, including drag, search, real buttons, scrolling and a small window. |
| Storage | 19 passed | 36 GameTests per write/read persistence pass passed | Final EN/FR runs passed: Terminal/Controller and manuals at 640 × 360 and 320 × 240, native focus cycles and clicks; Deposit and Pipes, including GUI scales 2/3/4. |
| Farm | 43 passed | 90 GameTests passed | Final EN/FR 57-step runs passed: four device screens, guides, drafts, resizing and complete native tab cycles. |
| Quarry | 10 passed | 47 GameTests passed | Final EN/FR 155-step runs passed: machine/help views, small viewport, guides, actions and complete native tab cycles. |
| Energy | 87 passed | 74 GameTests passed | Latest EN/FR runs passed: five real screens, three viewports/GUI scales, focus, bounds and scrolling; 24 captures per language. |
| Tasks | 78 passed | 40 GameTests without Storage; 46 with Storage/Energy passed | Final EN/FR GUI review passed: 23 views at 640 × 360, 320 × 240 and 427 × 240, complete native focus cycles, control bounds/overlap and physical display geometry. 82 captures archived per language. |
| Integration — 7 mods | Compiled checks passed | All seven production mods loaded; five integration GameTests passed | Combined client smoke passed: all seven mods loaded, native Enter/Tab/text input, actual framebuffer color and saved PNG. |

Total confirmé : **410 tests JUnit**, sans échec, erreur ou test ignoré.
Avant les pushes, les builds/tests Dashboard, Storage, Energy, Tasks et Quarry
avec `-PwithStorage` ont été revérifiés avec succès, ainsi que le contrôle
intégré des consommateurs et des JAR. Le smoke des actions Dashboard a réussi
en EN et FR après la dernière normalisation du menu déroulant. L'audit des CI
a parsé 7 workflows YAML, vérifié 29 blocs Bash et 12 références de dépendance,
sans erreur. Ces vérifications locales n'exécutent pas les workflows distants.
Les succès serveur restent des preuves distinctes des builds de documentation
plus récents. Les tests de layout couvrent les dimensions minuscules et les
bornes ; les tests de thème couvrent la palette et les mappings d'état ; le
contrôle de bytecode vérifie la frontière commune/client du kit. La validation
intégrée a vérifié les six consommateurs compilés : appel réel au kit public,
absence de palette complète de shell copiée et dépendances réelles compatibles.
Les sept JAR ont été inspectés : métadonnées conformes, API présente dans HomeCore
et aucune classe HomeCore embarquée dans un consommateur. Les quatre fichiers
Energy déjà modifiés ont conservé leurs SHA-256 initiaux.

Confirmed total: **410 JUnit tests**, with no failures, errors or skipped tests.
Server results are separate evidence from later documentation-only builds.
Layout tests cover tiny dimensions and bounds; theme tests cover the palette
and status mapping; bytecode checks enforce the kit's common/client boundary.
The integration harness checked all six compiled consumers for actual use of the
public kit, complete shell palette duplication and compatible real dependencies.
All seven JARs were inspected: correct metadata, public API present in HomeCore
and no embedded HomeCore classes in a consumer. SHA-256 checks confirm that the
four pre-existing Energy modifications stayed unchanged.

### Référence visuelle / Visual reference

Le rendu extrait de Dashboard conserve la palette et les primitives du cadre,
des panneaux et des huit états de bouton. Les captures avant/après ont été
comparées et relues ; Discovery et Network Rename étaient identiques sur la
région comparée. Les autres différences observées provenaient du survol/focus
ou des données réseau du fixture. Les dernières passes clavier confirment les
petites/grandes fenêtres et FR/EN. Les 23 petites vues Tasks ont été relues dans
chaque langue, avec contrôle des panneaux, libellés, ellipses et contraste.

Dashboard's extracted renderer preserves its palette, frame/panel primitives
and eight button states. Before/after captures were compared and inspected;
Discovery and Network Rename matched in the compared region. Other observed
differences came from hover/focus or fixture network data. Final keyboard runs
cover small/large windows and both languages. All 23 small Tasks views were
visually reviewed in each language for panels, text, clipping and contrast.

### Preuves locales / Local evidence

Les chemins ci-dessous sont relatifs à HomeCore, sauf indication du dépôt voisin.
Ces sorties de build restent locales et ne sont pas des livrables versionnés.

The paths below are relative to HomeCore unless a neighboring repository is
named. Build evidence stays local and is not distributed in release artifacts.

| Contrôle / Check | Journaux ou rapports / Logs or reports |
| --- | --- |
| HomeCore build/test/Javadocs, fresh persistence write/read | `build/ui-release-core-write.log`, `build/ui-release-core-read.log` |
| Local Maven binary/sources/Javadocs/POM/module resolution | `build/ui-release-maven-check.log` |
| Core UI EN/FR, eight states, two viewports | `build/ui-smoke-verified-en.log`, `build/ui-smoke-verified-fr.log` |
| Workbench client FR | `build/ui-workbench-smoke-fr.log` |
| Dashboard keyboard EN/FR and additional FR views | `build/ui-dashboard-keyboard-en.log`, `build/ui-dashboard-keyboard-fr-large.log`, `build/ui-dashboard-home-alerts-fr.log` |
| Storage final clean EN/FR | `build/ui-storage-clean-en.log`, `build/ui-storage-clean-fr.log` |
| Farm final keyboard EN/FR | `build/ui-farm-keyboard-en.log`, `build/ui-farm-keyboard-fr.log` |
| Quarry final keyboard EN/FR | `build/ui-quarry-keyboard-en.log`, `build/ui-quarry-keyboard-fr.log` |
| Energy final EN/FR | `build/ui-energy-smoke-en.log`, `build/ui-energy-smoke-fr.log` |
| Tasks final EN/FR | `build/ui-tasks-verified-en.log`, `build/ui-tasks-verified-fr.log`; `HomeLinkTask/build/validation/ui-kit-en` and `ui-kit-fr` contain captures and client logs |
| Combined server/static checks and client | `build/ui-integration-server.log`, `build/ui-integration-client.log` |
| JUnit XML totals and release JAR inspection | `build/ui-release-unit-evidence.log`, `build/ui-release-artifacts-evidence.log`; each repository's `build/test-results/test/TEST-*.xml` |

Deux assertions de fixture ont été corrigées avant les passes réussies : Storage
devait laisser avancer les ticks serveur réels pour son transfert persistant ;
Tasks devait examiner le focus après Tab plutôt que le booléen retourné par
`Screen.keyPressed`. La capture Core attend désormais un vrai frame sans overlay.
Un ancien second read Core a échoué après consommation du fixture ; la nouvelle
paire write/read complète a réussi. Les derniers résultats indiqués ci-dessus
font foi, sans masquer ces premières passes échouées.

Two fixture assertions were corrected before successful reruns: Storage needed
real server ticks for its persistent transfer, and Tasks needed to inspect focus
after Tab rather than require a true return from `Screen.keyPressed`. Core captures
now wait for an actual rendered frame without an overlay. An old second Core read
failed after consuming its fixture; a fresh complete write/read pair passed.
The final results above supersede those initial failed runs.

### Limites et livraison / Limits and delivery

Les écrans avec inventaire gardent leurs dimensions fonctionnelles. À GUI scale
élevé, Farm/Quarry peuvent avoir moins de 8 px autour du cadre ; leurs contrôles
restent visibles dans les petites fenêtres testées. Les tailles minimales qui
laissent le biseau entier sont documentées dans les guides de migration locaux.
Le kit ne garantit pas qu'un inventaire vanilla complet tienne dans une fenêtre
arbitrairement petite ; chaque écran garde la responsabilité de son contenu.

Les six migrations ont été commitées et poussées sur leurs branches distantes.
La correction Hydro préexistante autorisée est livrée séparément dans le commit
Energy `ca540c2`. Les CI consomment les sources compatibles de HomeCore et Energy
à des SHA complets ; Tasks épingle aussi Storage. Elles ne supposent aucune
publication Maven distante. HomeCore 1.14.0 n'a pas été publié sur un registry
distant ; le staging local vérifie seulement la résolution des artefacts.
Aucun mod Furnace n'était présent à migrer.

Inventory screens retain functional dimensions. At high GUI scale, Farm/Quarry
can have less than 8 pixels around the frame; controls remain visible in tested
small windows. Local migration guides record minimum sizes for the complete bevel.
The kit cannot make a full vanilla inventory fit an arbitrarily tiny window;
consumers remain responsible for their content.

All six migrations were committed and pushed to their remote branches. The
authorized existing Hydro correction is a separate Energy commit, `ca540c2`.
CI checks out compatible HomeCore/Energy sources at full immutable SHAs; Tasks
also pins Storage. No remote Maven publication is assumed or performed.
Local staging verifies artifact resolution. No Furnace repository was present.

### Commits des migrations / Migration commits

These revisions identify the code migration; later documentation commits may
advance the branches without changing the kit API.

| Repository | Migration commit |
| --- | --- |
| HomeCore | `dbcd1dc9fe694796780498ef1628903c89f6d04f` |
| Dashboard | `d1727903a16d38518c19378b1c1466c2326129bd` |
| Storage | `d7b8c7adbbff2a497ae1a5be6ffc77208d6f2fa5` |
| Farm | `b247827582f348e65413884a44cce7e31f642c6d` |
| Quarry | `552c8b7e8df07760048fa654d15c865630a4734e` |
| Energy | `57620158b6e29f47bccf951a6c253295dda73602` |
| Tasks | `1b929ceffa2e6bf4782361637f4bffa13e74d961` |

## Inventaire des fichiers / File inventory

`M`: modified; `A`: added; `D`: removed. Paths are relative to each repository.
This list includes the complete delivery, including the separate Hydro fix and
CI source pins. Build output, ignored local tools and unchanged files are excluded.

### HomeCore

```text
A docs/UI_MIGRATION.md
A docs/UI_RELEASE_NOTES.md
A docs/UI_STYLE.md
A integration-tests/src/main/java/fr/lkdm/homelink/integration/client/IntegrationClientSmoke.java
A integration-tests/src/main/resources/assets/homelink_integration/lang/en_us.json
A integration-tests/src/main/resources/assets/homelink_integration/lang/fr_fr.json
A src/main/java/fr/lkdm/homecore/api/client/ui/HomeLinkButton.java
A src/main/java/fr/lkdm/homecore/api/client/ui/HomeLinkScreenLayout.java
A src/main/java/fr/lkdm/homecore/api/client/ui/HomeLinkStatusTone.java
A src/main/java/fr/lkdm/homecore/api/client/ui/HomeLinkTheme.java
A src/main/java/fr/lkdm/homecore/api/client/ui/HomeLinkUi.java
A src/main/java/fr/lkdm/homecore/api/client/ui/package-info.java
A src/test/java/fr/lkdm/homecore/api/client/ui/HomeLinkScreenLayoutTest.java
A src/test/java/fr/lkdm/homecore/api/client/ui/HomeLinkThemeTest.java
A src/test/java/fr/lkdm/homecore/api/client/ui/HomeLinkUiBoundaryTest.java
A src/verification/java/fr/lkdm/homecore/verification/UiKitSmoke.java
A src/verification/resources/assets/homecore_validation/lang/en_us.json
A src/verification/resources/assets/homecore_validation/lang/fr_fr.json
M .github/workflows/integration.yml
M build.gradle
M docs/DEPENDENCIES.md
M docs/INTEGRATION_CI.md
M docs/ITEM_PORTS.md
M docs/PROTOCOL.md
M gradle.properties
M gradle/publication-smoke/build.gradle
M integration-tests/build.gradle
M integration-tests/gradle.properties
M integration-tests/README.md
M integration-tests/settings.gradle
M integration-tests/src/main/resources/META-INF/neoforge.mods.toml
M README.en.md
M README.md
M src/main/java/fr/lkdm/homecore/api/DashboardAPI.java
M src/main/java/fr/lkdm/homecore/workbench/client/ElectronicsScreen.java
```

### Dashboard

```text
A docs/UI_MIGRATION.md
D src/main/java/fr/lkdm/homelink/dashboard/client/rendering/DashboardTheme.java
D src/main/java/fr/lkdm/homelink/dashboard/client/widget/DashboardButton.java
M .github/workflows/ci.yml
M build.gradle
M docs/DESIGN.md
M docs/DEVELOPER_GUIDE.md
M gradle.properties
M README.md
M src/main/java/fr/lkdm/homelink/dashboard/client/rendering/DashboardDisplayRenderer.java
M src/main/java/fr/lkdm/homelink/dashboard/client/rendering/MetricRendererRegistry.java
M src/main/java/fr/lkdm/homelink/dashboard/client/screen/DashboardScreen.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ActionControlRegistry.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ActionDropdown.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ActionPanel.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/AlertCenterView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/DeviceExplorerView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/DiscoveryView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/EditorPalette.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/HomeDashboardView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/HomeEditorView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/MachinesView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ManualView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/NetworkView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/SettingsView.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/WidgetCards.java
M src/verification/java/fr/lkdm/homelink/dashboard/verification/DashboardClientSmoke.java
```

### Storage

```text
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/storage/client/rendering/StorageStatusColors.java
A src/verification/java/fr/lkdm/homelink/storage/verification/StorageUiChecks.java
D src/main/java/fr/lkdm/homelink/storage/client/rendering/StorageTheme.java
D src/main/java/fr/lkdm/homelink/storage/client/widget/StorageButton.java
M .github/workflows/ci.yml
M build.gradle
M docs/DEVELOPMENT.md
M docs/FILES.md
M docs/HOMECORE.md
M docs/RECIPES.md
M docs/STORAGE_DEPOSIT.md
M docs/STORAGE_PIPES_ARCHITECTURE.md
M docs/USER_GUIDE.md
M gradle.properties
M README.md
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeChoiceScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeRecoveryScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/rendering/CoverageRenderer.java
M src/main/java/fr/lkdm/homelink/storage/client/screen/DepositScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/screen/StorageScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/widget/StorageManualView.java
M src/main/resources/assets/homelink_storage/MATERIALS.md
M src/verification/java/fr/lkdm/homelink/storage/verification/PipePersistenceGameTests.java
M src/verification/java/fr/lkdm/homelink/storage/verification/StorageSmoke.java
```

### Farm

```text
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/farm/client/screen/FarmStatusColors.java
D src/main/java/fr/lkdm/homelink/farm/client/screen/FarmButton.java
D src/main/java/fr/lkdm/homelink/farm/client/screen/FarmTheme.java
M .github/workflows/ci.yml
M art/README.md
M CHANGELOG.md
M gradle.properties
M README.md
M src/gametest/java/fr/lkdm/homelink/farm/gametest/client/HelpSmoke.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmBotStationScreen.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmDeviceScreen.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmHelpView.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/IrrigationPumpScreen.java
```

### Quarry

```text
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryStatusColors.java
D src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryButton.java
D src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryTheme.java
M .github/workflows/ci.yml
M build.gradle
M CHANGELOG.md
M docs/HOMECORE.md
M gradle.properties
M README.md
M settings.gradle
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryHelpView.java
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryScreen.java
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryScreenLayout.java
M src/verification/java/fr/lkdm/homelink/quarry/verification/QuarryScenes.java
M src/verification/java/fr/lkdm/homelink/quarry/verification/QuarrySmoke.java
```

### Energy

```text
A docs/UI_MIGRATION.md
A src/verification/java/fr/lkdm/homelink/energy/verification/EnergyGuiSmoke.java
D src/main/java/fr/lkdm/homelink/energy/client/EnergyButton.java
D src/main/java/fr/lkdm/homelink/energy/client/EnergyTheme.java
M .github/workflows/ci.yml
M docs/HOMECORE_AUDIT.md
M gradle.properties
M README.md
M scripts/hydro-geometry.cjs
M src/main/java/fr/lkdm/homelink/energy/client/EnergyScreen.java
M src/main/java/fr/lkdm/homelink/energy/client/HydroPumpScreen.java
M src/main/java/fr/lkdm/homelink/energy/client/HydroTurbineScreen.java
M src/main/java/fr/lkdm/homelink/energy/client/WindTurbineScreen.java
M src/main/java/fr/lkdm/homelink/energy/hydro/WaterWindow.java
M src/main/resources/assets/homelink_energy/models/block/hydro_louver.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pipe_arm.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pipe_core.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_1_0_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_1_0_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_1_1_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_1_1_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_2_0_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_2_0_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_2_1_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_2_1_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_3_0_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_3_0_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_3_1_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_pump_3_1_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_rotor_blade.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_rotor_hub.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_0_0_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_0_0_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_0_1_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_0_1_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_1_0_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_1_0_1.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_1_1_0.obj
M src/main/resources/assets/homelink_energy/models/block/hydro_turbine_1_1_1.obj
M src/main/resources/assets/homelink_energy/models/item/hydro_pipe.obj
M src/main/resources/assets/homelink_energy/models/item/hydro_pump_1.obj
M src/main/resources/assets/homelink_energy/models/item/hydro_pump_2.obj
M src/main/resources/assets/homelink_energy/models/item/hydro_pump_3.obj
M src/main/resources/assets/homelink_energy/models/item/hydro_turbine.obj
M src/test/java/fr/lkdm/homelink/energy/hydro/WaterWindowTest.java
M src/verification/java/fr/lkdm/homelink/energy/verification/ClientSmoke.java
M src/verification/java/fr/lkdm/homelink/energy/verification/HydroClientSmoke.java
M src/verification/java/fr/lkdm/homelink/energy/verification/HydroGameTests.java
```

### Tasks

```text
A src/main/java/fr/lkdm/homelink/tasks/client/TaskAvailabilityStyle.java
A src/main/java/fr/lkdm/homelink/tasks/client/TaskItemButton.java
D src/main/java/fr/lkdm/homelink/tasks/client/TaskButton.java
D src/main/java/fr/lkdm/homelink/tasks/client/TaskTheme.java
M .github/workflows/ci.yml
M build.gradle
M docs/adapters.md
M docs/architecture.md
M docs/validation.md
M gradle.properties
M README.md
M src/main/java/fr/lkdm/homelink/tasks/client/BoardListScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/BoardScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/BoardSettingsScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/CardActionsScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/CardDetailScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/CardEditorScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/ItemPickerScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/MindMapScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/ObjectiveSettingsScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/PinnedTaskOverlay.java
M src/main/java/fr/lkdm/homelink/tasks/client/ProjectMaterialsScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/ProjectViewerScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/RecipeExpansionScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/RecipePickerScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/RecipeScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/ScreenNetworkScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskDisplayRenderer.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskMenuScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskScreen.java
M src/verification/java/fr/lkdm/homelink/tasks/verification/InGameValidation.java
```
