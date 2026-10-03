# Migration vers HomeLink UI — HomeCore 1.14.0 / API 1.9.0

## Audit initial du workspace

Les dépôts ci-dessous ont été inspectés avant modification. Ces versions
décrivent le point de départ, et non des versions supposées d'après un ancien
document.

| Dépôt local | Version | Classes visuelles locales | Différences à préserver ou harmoniser |
| --- | --- | --- | --- |
| HomeCore | 1.13.0 / API 1.8.0 | Pas de kit UI public partagé | Ajout compatible de l'API client |
| HomeLink Dashboard | 1.6.0 | `DashboardTheme`, `DashboardButton` | Référence exacte du cadre, boutons, tabs et focus |
| HomeLink Storage | 1.4.0 | `StorageTheme`, `StorageButton` | Pipes déjà présents ; seul Deposit contient des slots vanilla, préservés |
| FarmLink | 1.5.0 | `FarmTheme`, `FarmButton` | Header 24 px, aides/toggles dorés ; états Farm locaux |
| HomeLinkQuarry | 1.4.0 | `QuarryTheme`, `QuarryButton` | Header 24 px, aides/toggles dorés ; état Quarry local |
| HomeLinkEnergy | 0.5.0 | `EnergyTheme`, `EnergyButton`, chrome `EnergyScreen` | Largeur 304, cinq hauteurs, header 24 px, jauges d'eau métier |
| HomeLinkTask | 0.2.0 | `TaskTheme`, `TaskButton`, chrome `TaskScreen` | Shell 520 × 340 adaptatif, icônes item, couleurs de disponibilité |

Storage est donc déjà en 1.4.0, avec des Pipes : la migration doit couvrir leurs
interfaces présentes, pas seulement prévoir des composants futurs.
Farm, Quarry et Energy n'ont pas le token local `HOVER` ; leur palette et leurs
panneaux reproduisent par ailleurs le langage commun. Leurs fenêtres à header
24 px ont un footer organisé par le consommateur. `HomeLinkUi.window` permet de
conserver la géométrie de ces écrans avec slots tout en partageant le rendu.

Farm et Quarry avaient un comportement `accentWhen` qui dorait le fond au survol
ou lors de l'activation. Les nouveaux contrôles suivent l'état enfoncé officiel,
avec le focus cuivre, au lieu d'entretenir un deuxième renderer de boutons.
Cette harmonisation est distincte de la migration golden de Dashboard, qui
doit conserver son apparence de référence.

Energy possède cinq écrans : Battery (304 × 210), Solar (304 × 224), Wind
(304 × 286), Hydro Pump (304 × 262), Hydro Turbine (304 × 300). Les valeurs de
jauge, le débit et la couleur fonctionnelle d'eau `0xFF6FA8B8` restent locaux.
Ces tailles sont désormais préférées : les menus Energy n'ont aucun slot,
donc ils utilisent `HomeLinkScreenLayout.fit` avec un header officiel de 29 px.
Le corps télémétrique défile par molette et PageUp/PageDown dans un scissor
absolu ; la liaison HomeNetwork et les actions restent fixes en bas.
Son ancien `EnergyButton.accentWhen` n'a pas de consommateur dans les sources
auditées. Des modifications Hydro existaient déjà dans ce checkout ; elles ne
doivent pas être écrasées ou attribuées à la migration UI.

Tasks utilisait un bouton dont le constructeur imposait 18 px après les bounds,
y compris pour des appels demandant 20, 22 ou 24 px. Ses tabs sélectionnés étaient
désactivés. Préserver le comportement d'action voulu en gérant `active` dans les
écrans ; la sélection visuelle est fournie par le kit. L'icône item de
`TaskButton.item` est effectivement utilisée par CardEditor et ItemPicker : son
contenu reste un composant Tasks et son chrome vient de HomeCore.

## Remplacements

| Ancien code | API HomeCore |
| --- | --- |
| Couleurs de `DashboardTheme`, `StorageTheme`, `FarmTheme`, `QuarryTheme`, `EnergyTheme`, shell `TaskTheme` | `HomeLinkTheme` |
| `frame(...)` | `HomeLinkUi.frame(...)` |
| Fenêtre avec header personnalisé | `HomeLinkUi.window(..., headerHeight)` |
| `panel(...)`, `screw(...)`, `mark(...)`, `slot(...)` | Méthodes correspondantes de `HomeLinkUi` |
| `input(EditBox)` et couleurs/hauteur locales de champ | `HomeLinkUi.input(EditBox)` |
| `divider(...)` | `HomeLinkUi.separator(...)` |
| `statusLight(...)` | `HomeLinkUi.statusDot(...)` |
| Jauge/progression générique | `HomeLinkUi.gauge(...)` / `progressBar(...)` |
| Troncature texte commune | `HomeLinkUi.clip(Font, String, int)` |
| Bouton standard de chaque mod | `HomeLinkButton.builder(...)` |
| Tab sélectionné | Bouton `.navigation(selected)` après `.build()` |
| État enfoncé d'une action | Bouton `.selected(selected)` après `.build()` |
| Calcul manuel de surface centrée | `HomeLinkScreenLayout.fit(...)` si compatible avec l'écran |
| Couleur de status générique | `HomeLinkTheme.statusColor(HomeLinkStatusTone)` |

Les noms Tasks se traduisent ainsi : `ANTHRACITE → BACKGROUND`,
`PANEL → HEADER`, `SURFACE_HOVER → HOVER`, `STEEL → LINE`,
`COPPER → ACCENT`, `TEXT_MUTED → MUTED`. Ne pas confondre son ancien token
`PANEL` (couleur de header) avec le helper `HomeLinkUi.panel` (surface encastrée).

Préférer des imports directs de l'API publique. Un wrapper temporaire déprécié
ne doit contenir que des délégations ; il ne doit recopier ni palette ni renderer.
Un composant métier spécialisé peut conserver une classe locale à condition
d'utiliser réellement le chrome HomeCore.

## Ce qui reste dans les mods

Dashboard garde client state, pages, navigation, profils/widgets spécialisés,
transport et partage d'écran. Storage garde inventaires, filtres, connexions et
comportement des Pipes. Farm et Quarry gardent simulation, actions et overlays
3D. Les fonctions locales `pumpStatus`, `farmBotStatus` et status Quarry restent
locales ; elles peuvent retourner un ton visuel HomeCore depuis le code client.
Energy garde ses métriques HE, eau et vent. Tasks garde Kanban, drag/drop,
mind map, HUD et rendu du display physique.

Dans Tasks, conserver les couleurs `READY=0xFF4CAF50`,
`IN_STORAGE=0xFFE59B3D`, `MISSING=0xFFE05252`, `UNVERIFIED=0xFF8A9099`,
avec leurs symboles et traductions. Ce sont des états métier, pas une nouvelle
palette de shell.

## Dépendances et livraison

Les consommateurs migrés déclarent `homecore_version=1.14.0` et
`homecore_version_range=[1.14.0,2.0.0)`. Leurs composites vérifient la version
locale exacte et Minecraft 1.21.1. Ils consomment
`fr.lkdm.homecore:homecore:1.14.0` ; les classes HomeCore ne doivent pas être
embarquées dans leurs JAR. L'ajout du kit ne nécessite pas de dépendance vers
Dashboard et ne modifie pas à lui seul les versions des artefacts consommateurs.

HomeCore devient 1.14.0, avec `DashboardAPI.API_VERSION="1.9.0"`.
Le build local ne signifie pas que cette version a été publiée sur un registry.
Installer le JAR HomeCore correspondant avec les mods migrés, ou utiliser les
sources locales par composite pour développer simultanément.

Les futurs écrans HomeLink Furnace et les nouvelles vues Storage Pipes utilisent
directement `HomeLinkTheme`, `HomeLinkUi`, `HomeLinkButton` et le layout facultatif,
sans créer une nouvelle classe thème.

## Validation à effectuer

Ce guide décrit les vérifications requises ; il n'annonce aucun résultat
d'exécution. Consulter les rapports et logs produits par la livraison pour les
résultats effectivement obtenus.

1. HomeCore : build, tests layout/status, Javadocs et serveur dédié/GameTestServer.
2. Dashboard : build/tests/GameTests, smoke et comparaison des captures avant/après
   pour frame, normal/hover/disabled/selected/navigation/focus, champs et statuses.
3. Chaque mod : build/tests et smokes existants, principaux screens en FR/EN,
   petite/grande fenêtre et navigation clavier.
4. Intégration : métadonnées, absence de classes HomeCore dans les JAR, serveur
   et client avec plusieurs mods lorsqu'une infrastructure de test le permet.

Energy fournit `runSmoke` et `runSmoke -PsmokeLanguage=en_us`, ainsi que
`runGameTestServer`. Tasks fournit `runGameTestServer`, éventuellement
`-PwithStorage`, `runInGame` / `runInGameGuest` et `-PguiReview` pour ses captures
à plusieurs tailles et aux GUI scales 2 et 3. Le profil historique `guiReview`
imposait le français ; `-PguiReviewLanguage=fr_fr` ou `en_us` permet maintenant
de choisir la langue. Exécuter les deux parcours avant d’annoncer une couverture
FR/EN. Les scripts Tasks de relevé des dépendances contenaient
des noms de JAR historiques : vérifier qu'ils correspondent aux artefacts réels.

Rechercher les palettes complètes restantes et les références à l'API publique,
sans interdire des couleurs métier isolées. Ne jamais référencer les classes du
kit depuis du code common/server : la totalité de `api.client.ui` est client-only.

## English migration notes

The table above records the actual starting workspace versions. In particular,
Storage was already 1.4.0 with Pipes. HomeCore 1.14.0 / API 1.9.0 owns the shared
client palette, stateless drawing helpers, vanilla-backed buttons and optional
adaptive layout. Consumers depend explicitly on HomeCore 1.14.0 and declare its
runtime minimum; neither HomeCore nor another consumer needs Dashboard installed.

Replace local shell colors/renderers with `HomeLinkTheme` and `HomeLinkUi`,
standard buttons with `HomeLinkButton`, and tabs with `.navigation(selected)`.
The replacement table applies to every audited repository. Custom inventory
geometry may remain, using `window` with its existing header height. Dashboard
is the golden reference and must retain its visual appearance. Farm/Quarry gold
toggle behavior is harmonized to the official pressed selection style.

Keep domain state, networking, navigation, recipe/inventory logic and world
overlays inside the consumer. Tasks stock colors, symbols and localized labels,
Energy water colors and item-button content remain domain-specific. A retained
local component delegates its shell to HomeCore; it must not duplicate the
palette or a second button renderer. New Furnace and Storage Pipes interfaces
can consume the kit directly.

Energy's slot-free menus use their existing sizes as preferences and fit the
scaled viewport. Their header is 29 pixels and buttons 18 pixels. Telemetry
scrolls locally while network/overlay controls stay fixed; water gauge colors
and machine logic are preserved.

Builds, unit tests, Javadocs, dedicated-server checks, existing GameTests and
client smokes must be reported from actual executions. This guide makes no
claim that a test ran or passed. Compare Dashboard captures and check each
consumer at multiple window sizes, keyboard focus and both languages. Verify
dependency metadata and that consumer JARs do not embed HomeCore classes.
