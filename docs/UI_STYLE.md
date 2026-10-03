# HomeLink UI Kit — HomeCore 1.14.0 / API 1.9.0

## Français

Le langage visuel historique de HomeLink Dashboard devient la norme commune,
disponible dans `fr.lkdm.homecore.api.client.ui`. HomeCore fournit les couleurs,
le chrome et les contrôles ; chaque mod conserve ses écrans et sa logique.
Dashboard n'est pas une dépendance du kit ni des autres mods.

### Identité et palette

Les interfaces utilisent du graphite, de l'acier et un accent cuivre discret,
la police Minecraft native et des surfaces dessinées sans texture GUI obligatoire.
Le vert indique un état ; il ne devient pas la couleur dominante du shell.
Éviter les couleurs décoratives sans signification et les grands logos répétés.

| Token `HomeLinkTheme` | ARGB | Usage |
| --- | --- | --- |
| `BACKGROUND` | `0xFF303234` | Surface principale |
| `HEADER` | `0xFF45474A` | Bandeau supérieur |
| `SURFACE` | `0xFF252729` | Panneau encastré |
| `HOVER` | `0xFF4A4D50` | Surface survolée |
| `LINE` | `0xFF626568` | Séparateurs et contours |
| `ACCENT` | `0xFFD2B181` | Sélection, focus clavier |
| `TEXT` | `0xFFE7E5E0` | Texte principal |
| `MUTED` | `0xFFAFB1AD` | Texte secondaire, état neutre |
| `ONLINE` | `0xFFA1BD92` | Disponible |
| `WARNING` | `0xFFD3B16F` | Attention |
| `OFFLINE` | `0xFFD19A8F` | Indisponible ou erreur |

Ces valeurs reproduisent la référence Dashboard 1.6.0. Les teintes de relief des
boutons sont également celles de la référence ; `HOVER` est un token de surface,
pas une instruction de remplacer le rendu interne du bouton.

### Dimensions et disposition

Toutes les dimensions sont des pixels GUI après application du GUI scale.
`CONTROL_HEIGHT=18`, `HEADER_HEIGHT=29`, `FOOTER_HEIGHT=29`,
`CONTENT_PADDING=12`, `OUTER_MARGIN=8`.
La surface préférée par défaut vaut `520 × 340`.

`HomeLinkScreenLayout.fit(screenWidth, screenHeight, preferredWidth, preferredHeight)`
centre une surface qui respecte les dimensions demandées et réserve 8 px de chaque
côté lorsque possible. Les très petites fenêtres restent bornées et les régions
de contenu ne deviennent pas négatives. `contentX/Y/Width/Height` excluent le
header, le footer et le padding. Réserver soi-même l'espace des tabs si nécessaire.
Un contenu vide signifie qu'il faut réduire, paginer ou faire défiler ses contrôles.
Le layout ne crée pas de scrollbar et ne repositionne pas les widgets.

Les cinq menus Energy n'ont aucun slot : leurs surfaces préférées de largeur
304 px s'adaptent avec `fit`. La télémétrie défile dans le panneau, tandis que le
rattachement réseau et les actions d'overlay restent fixes. Ce défilement local
appartient au mod, pas au kit.

Les écrans avec slots peuvent garder leur géométrie fonctionnelle et utiliser
`HomeLinkUi.window(..., headerHeight)` pour un bandeau adapté. Le cadre dessine
un biseau de 3 px autour du rectangle. Ne jamais déplacer un slot uniquement pour
atteindre les dimensions préférées d'un écran sans inventaire.

### Chrome, panneaux et contrôles

- `HomeLinkUi.frame` : biseau industriel, header de 29 px, règle inférieure et quatre vis.
- `HomeLinkUi.window` : même chrome avec hauteur de header explicite ; le consommateur organise son footer.
- `HomeLinkUi.panel` : fond encastré pour des informations regroupées.
- `HomeLinkUi.slot` : logement 18 × 18 pour un item rendu à la position donnée.
- `HomeLinkUi.separator`, `screw`, `mark` : primitives communes de séparation et décoration.
- `HomeLinkUi.statusDot` : voyant, avec ton explicite ou couleur ARGB.
- `HomeLinkUi.progressBar` : progression horizontale ; `gauge` convient à une jauge fine.
- `HomeLinkUi.input` : applique les couleurs du texte à un `EditBox` ; fournir la hauteur officielle lors de sa création.
- `HomeLinkUi.clip` : ellipsis pour un texte trop large.

Le header contient un titre lisible, éventuellement un voyant et une aide `?`.
Les panneaux ont des marges cohérentes et suffisamment de place pour leur texte.
Les boutons restent gris en relief, deviennent plus clairs au survol, puis
enfoncés avec `selected(true)`. Les tabs utilisent `navigation(selected)` :
sélection enfoncée et petit marqueur cuivre. Le focus clavier a un contour ambre.
Un bouton disabled reste lisible et sa cause doit être expliquée si ambiguë.
`selectedWhen(BooleanSupplier)` lie un état de sélection léger évalué au rendu ;
le fournisseur reste pur et ne déclenche ni réseau ni lecture serveur.
Une sélection visuelle et une désactivation sont deux décisions distinctes :
le consommateur gère `active` selon son comportement de navigation.

### États, accessibilité et localisation

Utiliser `HomeLinkStatusTone.ONLINE`, `WARNING`, `OFFLINE` ou `NEUTRAL` dans
les nouveaux écrans. `HomeLinkTheme.statusColor(String)` conserve les anciens
identifiants : `ONLINE/CONNECTED`, `WARNING/UNREACHABLE`,
`OFFLINE/ERROR/CRITICAL`. Une valeur inconnue, null ou non normalisée reste neutre.
Les règles métier qui produisent ces états restent dans le mod consommateur.

Associer chaque couleur à un texte, symbole ou tooltip. Conserver la narration
vanilla, un ordre de tabulation logique et le focus visible. Ajouter une tooltip
si une action est ambiguë ou si un texte de contenu est tronqué. Les boutons
HomeLink conservent le comportement de `Button`, y compris sa narration.

Tous les libellés traduisibles utilisent `Component.translatable`, avec des
ressources `fr_fr.json` et `en_us.json`. Les noms saisis par un joueur peuvent
rester des composants littéraux. Les couleurs métier Tasks (inventaire, Storage,
manque, inconnu), les jauges d'eau et les overlays 3D fonctionnels ne changent
pas le shell commun et restent définis par leurs mods.

### Exemple minimal d'écran client

Cet exemple ne nécessite pas de classe écran de base HomeCore. Il masque le
contrôle si la fenêtre ne lui laisse pas assez de place. Un vrai écran plus
complexe fournit son propre scroll ou une pagination.

```java
package examplemod.client;

import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkScreenLayout;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExampleScreen extends Screen {
    private HomeLinkScreenLayout layout;

    public ExampleScreen() {
        super(Component.translatable("screen.examplemod.machine"));
    }

    @Override
    protected void init() {
        layout = HomeLinkScreenLayout.fit(width, height, 520, 340);
        if (layout.contentWidth() >= 80
                && layout.contentHeight() >= HomeLinkTheme.CONTROL_HEIGHT) {
            addRenderableWidget(HomeLinkButton.builder(
                    Component.translatable("screen.examplemod.close"), button -> onClose())
                .bounds(layout.contentX(), layout.contentY(), 80,
                        HomeLinkTheme.CONTROL_HEIGHT)
                .build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        HomeLinkUi.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
        if (layout.headerHeight() >= 18 && layout.width() >= 32) {
            graphics.drawString(font, HomeLinkUi.clip(font, title.getString(), layout.width() - 28),
                    layout.x() + 14, layout.y() + 11, HomeLinkTheme.TEXT, false);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
```

Ressources du consommateur :

```json
{"screen.examplemod.machine":"Machine", "screen.examplemod.close":"Fermer"}
```

```json
{"screen.examplemod.machine":"Machine", "screen.examplemod.close":"Close"}
```

### Bouton, tab, champ, panneau et état

```java
var action = HomeLinkButton.builder(Component.translatable("screen.examplemod.apply"),
        button -> applySettings())
    .bounds(x, y, 96, HomeLinkTheme.CONTROL_HEIGHT).build();

var tab = HomeLinkButton.builder(Component.translatable("screen.examplemod.settings"),
        button -> openSettings())
    .bounds(x, y, 96, HomeLinkTheme.CONTROL_HEIGHT).build().navigation(settingsOpen);

var input = new EditBox(font, x, y, 120, HomeLinkTheme.CONTROL_HEIGHT,
        Component.translatable("screen.examplemod.name"));
HomeLinkUi.input(input);
addRenderableWidget(input);

HomeLinkUi.panel(graphics, x, y, 160, 48);
HomeLinkUi.statusDot(graphics, x + 8, y + 8, HomeLinkStatusTone.ONLINE);
graphics.drawString(font, Component.translatable("screen.examplemod.online"),
        x + 20, y + 8, HomeLinkTheme.TEXT, false);
HomeLinkUi.progressBar(graphics, x + 8, y + 32, 144, 3, progress, HomeLinkTheme.ACCENT);
```

Ces fragments supposent les imports du package UI et de `EditBox`. Les actions,
la valeur `progress` et l'état `settingsOpen` appartiennent au consommateur.

### Dépendance et frontière client

```groovy
dependencies { implementation 'fr.lkdm.homecore:homecore:1.14.0' }
```

```toml
[[dependencies.examplemod]]
modId="homecore"
type="required"
versionRange="[1.14.0,2.0.0)"
ordering="AFTER"
side="BOTH"
```

Configurer Maven ou le composite local comme décrit dans [DEPENDENCIES.md](DEPENDENCIES.md).
Un composite utilise les sources voisines ; il ne publie rien et ne met pas
automatiquement une dépendance à jour depuis GitHub.

Tout le package UI est une API **client uniquement**, y compris ses tokens et
son layout. L'importer seulement depuis les classes client du consommateur.
Enregistrer les screens et handlers dans un chemin `Dist.CLIENT`, jamais dans
un initialiseur commun. Une classe common/server ne doit ni importer le kit,
ni exposer `HomeLinkButton`, `Screen`, `Font` ou `GuiGraphics` dans ses contrats.
Les helpers ne s'enregistrent pas sur un event bus et ne lancent aucun réseau,
polling ou tick serveur.

Le kit n'accueille pas navigation Dashboard, pages métier, données de machine,
permissions, transport, recettes, state management, overlays monde ou HUD métier.
Les futurs Storage Pipes et HomeLink Furnace utilisent directement cette API,
sans recopier de thème et sans installer Dashboard.

## English

HomeCore 1.14.0 / public API 1.9.0 supplies the official HomeLink visual language
through `fr.lkdm.homecore.api.client.ui`. The palette table and Java examples
above are shared by both languages. Dashboard 1.6.0 is the historical reference;
HomeCore and consumers do not require Dashboard to render their interfaces.

Use graphite and steel surfaces with a restrained copper accent and Minecraft's
native font. `HomeLinkTheme` owns the shared palette and dimensions. Buttons and
text fields default to 18 GUI pixels; header/footer are 29, content padding 12,
and the outside margin 8. The preferred maximum surface is 520 × 340.

`HomeLinkScreenLayout.fit` centers the requested surface and shrinks it for the
scaled viewport. Its content region excludes the header, footer and padding.
Reserve tab space in the consumer and provide scrolling or pagination for
overflow. Inventory screens may keep their slot positions and functional size.
Energy's five menus have no slots: they fit their preferred 304-pixel surfaces
to the viewport, scroll telemetry locally and keep network/overlay actions fixed.
`HomeLinkUi.window` supports a custom header height, while `frame` supplies the
standard shell, footer rule and screws. The bevel extends 3 pixels beyond the
surface bounds. Recessed panels, separators, status indicators, progress bars
and field styling come from `HomeLinkUi`.

Create controls with `HomeLinkButton.builder(Component, OnPress)`, as shown
above. `selected(true)` provides the pressed appearance;
`navigation(selected)` also supplies the copper tab marker. Set `active` in the
consumer when a control should be disabled. Preserve vanilla narration, logical
tab order and visible amber keyboard focus. Explain ambiguous actions and
disabled states, and provide tooltips for truncated content. Never communicate
a state through color alone.

Prefer `HomeLinkStatusTone` in new screens. Legacy string mapping is
case-sensitive and uses a neutral fallback. Consumers retain their own domain
colors, including Tasks stock availability and water gauges; these colors do
not replace the shell palette. Keep all translatable labels in both `fr_fr.json`
and `en_us.json` and pass them through `Component.translatable`.

`selectedWhen(BooleanSupplier)` can bind a lightweight client toggle without
rebuilding the widget. Keep the supplier pure. A specialized consumer button may
extend `HomeLinkButton` and override the protected `renderLabel` hook to add an
item icon while inheriting the official button shell and vanilla behavior.

The dependency snippets above require HomeCore 1.14.0 explicitly and declare
the runtime minimum in NeoForge metadata. See [DEPENDENCIES.md](DEPENDENCIES.md)
for Maven and local composite configuration. Building does not publish an
artifact or automatically update another mod's dependency.

Import the entire UI package only from client code, including theme and layout
classes. Register screens through a `Dist.CLIENT` path. Common/server code must
not reference client helpers or expose Minecraft client types in its API.
Stateless helpers require no event-bus registration and add no server tick,
network activity or polling. Business navigation, machine state, permissions,
recipes, transport, world overlays and domain HUDs remain in their consumers.
New Storage Pipes and Furnace screens can use this kit directly without copying
a theme or depending on Dashboard.
