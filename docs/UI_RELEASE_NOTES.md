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
métier historiques disponibles. Aucun workflow distant n'est présenté comme
exécuté dans ce rapport.

These results come from the latest local runs against this release's sources.
All seven repository builds passed. Targeted smokes cover the changed interfaces.
This report makes no claim that every historical business profile or a remote
workflow ran.

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

Les modifications des six consommateurs restent dans leurs checkouts locaux.
La validation utilise ces sources par composite ; elle ne prouve pas que leurs
branches distantes contiennent déjà la migration. HomeCore 1.14.0 n'a pas été
publié sur un registry distant. Le staging Maven local sert uniquement à vérifier
la résolution des artefacts. Aucun mod Furnace n'était présent à migrer.

Inventory screens retain functional dimensions. At high GUI scale, Farm/Quarry
can have less than 8 pixels around the frame; controls remain visible in tested
small windows. Local migration guides record minimum sizes for the complete bevel.
The kit cannot make a full vanilla inventory fit an arbitrarily tiny window;
consumers remain responsible for their content.

The six consumer migrations remain in their local checkouts. Composite validation
does not claim their remote branches already contain these changes. HomeCore
1.14.0 was not published to a remote registry; local Maven staging only verified
artifact resolution. No Furnace repository was present to migrate.

## Inventaire des fichiers / File inventory

`M` : modifié / modified ; `A` : créé / added ; `D` : supprimé / removed.
Les chemins sont relatifs au dépôt indiqué. Cet inventaire suit le diff réel
et les nouveaux fichiers utiles, hors sorties de build, outils locaux ignorés
et fichiers sans diff. Les quatre modifications Energy préexistantes sont
exclues : `WaterWindow.java`, `WaterWindowTest.java`, `HydroClientSmoke.java`,
`HydroGameTests.java`. Leurs contenus ont été préservés.

Paths are relative to the named repository. The inventory follows the actual
diff and useful new files, excluding build output, ignored local tools and
unchanged files. The four pre-existing Energy modifications named above are
excluded and were preserved.

### HomeCore

```text
M .github/workflows/integration.yml
M README.en.md
M README.md
M build.gradle
M docs/DEPENDENCIES.md
M docs/INTEGRATION_CI.md
M docs/ITEM_PORTS.md
M docs/PROTOCOL.md
M gradle.properties
M gradle/publication-smoke/build.gradle
M integration-tests/README.md
M integration-tests/build.gradle
M integration-tests/gradle.properties
M integration-tests/settings.gradle
M integration-tests/src/main/resources/META-INF/neoforge.mods.toml
M src/main/java/fr/lkdm/homecore/api/DashboardAPI.java
M src/main/java/fr/lkdm/homecore/workbench/client/ElectronicsScreen.java
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
```

### Dashboard — HomeLink

```text
M README.md
M build.gradle
M docs/DESIGN.md
M docs/DEVELOPER_GUIDE.md
M gradle.properties
M src/main/java/fr/lkdm/homelink/dashboard/client/rendering/DashboardDisplayRenderer.java
D src/main/java/fr/lkdm/homelink/dashboard/client/rendering/DashboardTheme.java
M src/main/java/fr/lkdm/homelink/dashboard/client/rendering/MetricRendererRegistry.java
M src/main/java/fr/lkdm/homelink/dashboard/client/screen/DashboardScreen.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ActionControlRegistry.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/ActionPanel.java
M src/main/java/fr/lkdm/homelink/dashboard/client/widget/AlertCenterView.java
D src/main/java/fr/lkdm/homelink/dashboard/client/widget/DashboardButton.java
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
A docs/UI_MIGRATION.md
```

### Storage — HomeLink Storage

```text
M README.md
M build.gradle
M docs/DEVELOPMENT.md
M docs/FILES.md
M docs/HOMECORE.md
M docs/RECIPES.md
M docs/STORAGE_DEPOSIT.md
M docs/STORAGE_PIPES_ARCHITECTURE.md
M docs/USER_GUIDE.md
M gradle.properties
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeChoiceScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeRecoveryScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/logistics/PipeScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/rendering/CoverageRenderer.java
D src/main/java/fr/lkdm/homelink/storage/client/rendering/StorageTheme.java
M src/main/java/fr/lkdm/homelink/storage/client/screen/DepositScreen.java
M src/main/java/fr/lkdm/homelink/storage/client/screen/StorageScreen.java
D src/main/java/fr/lkdm/homelink/storage/client/widget/StorageButton.java
M src/main/java/fr/lkdm/homelink/storage/client/widget/StorageManualView.java
M src/verification/java/fr/lkdm/homelink/storage/verification/PipePersistenceGameTests.java
M src/verification/java/fr/lkdm/homelink/storage/verification/StorageSmoke.java
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/storage/client/rendering/StorageStatusColors.java
A src/verification/java/fr/lkdm/homelink/storage/verification/StorageUiChecks.java
```

### Farm — FarmLink

```text
M README.md
M gradle.properties
M src/gametest/java/fr/lkdm/homelink/farm/gametest/client/HelpSmoke.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmBotStationScreen.java
D src/main/java/fr/lkdm/homelink/farm/client/screen/FarmButton.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmDeviceScreen.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/FarmHelpView.java
D src/main/java/fr/lkdm/homelink/farm/client/screen/FarmTheme.java
M src/main/java/fr/lkdm/homelink/farm/client/screen/IrrigationPumpScreen.java
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/farm/client/screen/FarmStatusColors.java
```

### Quarry — HomeLinkQuarry

```text
M README.md
M docs/HOMECORE.md
M gradle.properties
D src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryButton.java
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryHelpView.java
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryScreen.java
M src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryScreenLayout.java
D src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryTheme.java
M src/verification/java/fr/lkdm/homelink/quarry/verification/QuarryScenes.java
M src/verification/java/fr/lkdm/homelink/quarry/verification/QuarrySmoke.java
A docs/UI_MIGRATION.md
A src/main/java/fr/lkdm/homelink/quarry/client/screen/QuarryStatusColors.java
```

### Energy — HomeLinkEnergy

```text
M README.md
M docs/HOMECORE_AUDIT.md
M gradle.properties
D src/main/java/fr/lkdm/homelink/energy/client/EnergyButton.java
M src/main/java/fr/lkdm/homelink/energy/client/EnergyScreen.java
D src/main/java/fr/lkdm/homelink/energy/client/EnergyTheme.java
M src/main/java/fr/lkdm/homelink/energy/client/HydroPumpScreen.java
M src/main/java/fr/lkdm/homelink/energy/client/HydroTurbineScreen.java
M src/main/java/fr/lkdm/homelink/energy/client/WindTurbineScreen.java
M src/verification/java/fr/lkdm/homelink/energy/verification/ClientSmoke.java
A docs/UI_MIGRATION.md
A src/verification/java/fr/lkdm/homelink/energy/verification/EnergyGuiSmoke.java
```

### Tasks — HomeLinkTask

```text
M README.md
M build.gradle
M docs/adapters.md
M docs/architecture.md
M docs/validation.md
M gradle.properties
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
D src/main/java/fr/lkdm/homelink/tasks/client/TaskButton.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskDisplayRenderer.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskMenuScreen.java
M src/main/java/fr/lkdm/homelink/tasks/client/TaskScreen.java
D src/main/java/fr/lkdm/homelink/tasks/client/TaskTheme.java
M src/verification/java/fr/lkdm/homelink/tasks/verification/InGameValidation.java
A src/main/java/fr/lkdm/homelink/tasks/client/TaskAvailabilityStyle.java
A src/main/java/fr/lkdm/homelink/tasks/client/TaskItemButton.java
```
