# HomeCore 1.14.0

Version anglaise : [README.en.md](README.en.md).

Description CurseForge en anglais : [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md).

Configuration de publication CurseForge : [README anglais](README.en.md#publish-to-curseforge).

API commune pour les appareils et réseaux d'une base Minecraft. HomeCore ne dépend d'aucun mod consommateur : Home Dashboard, Farm Monitor et une carte holographique peuvent utiliser ses contrats sans que HomeCore connaisse leurs implémentations.

Minecraft **1.21.1**, NeoForge **21.1.250**, Java **21**. API : `DashboardAPI.API_VERSION = "1.9.0"`.

## Construire et installer

Configurer `JAVA_HOME` vers un JDK 21 puis utiliser le wrapper :

```powershell
./gradlew.bat clean build test javadoc
./gradlew.bat runClient
```

Sur Linux/macOS, utiliser `./gradlew`. Les JAR sont dans `build/libs` ; installer `homecore-1.14.0.jar` dans `mods` côté client et serveur. Le serveur de jeu normal requiert l'acceptation de l'EULA Minecraft par son administrateur.

Pour développer un mod consommateur, déclarer une version explicite. Utiliser les sources locales par composite, ou l’artefact Maven lorsque cette version a réellement été publiée. Le dépôt Maven et le mode composite local sont décrits dans [DEPENDENCIES.md](docs/DEPENDENCIES.md). Dans son projet ModDevGradle :

```groovy
// Configurer le dépôt Maven selon docs/DEPENDENCIES.md.
dependencies { implementation 'fr.lkdm.homecore:homecore:1.14.0' }
```

Ajouter à son `neoforge.mods.toml`, en remplaçant `examplemod` par son identifiant :

```toml
[[dependencies.examplemod]]
modId="homecore"
type="required"
versionRange="[1.14.0,2.0.0)"
ordering="AFTER"
side="BOTH"
```

Le build ne publie rien. Le développement simultané de plusieurs mods utilise un build composite explicite ; il ne télécharge ni ne met à jour les sources depuis GitHub.

## HomeLink UI Kit

HomeCore 1.14.0 fournit la norme visuelle officielle HomeLink dans `fr.lkdm.homecore.api.client.ui` : palette graphite/acier/cuivre, cadre industriel, panneaux encastrés, boutons accessibles et layout adaptatif facultatif. Dashboard utilise cette API cliente publique ; aucun consommateur n'a besoin de Dashboard pour afficher son GUI.

`HomeLinkTheme`, `HomeLinkStatusTone`, `HomeLinkUi`, `HomeLinkButton` et `HomeLinkScreenLayout` sont réservés au code client. Les écrans conservent leur logique métier et leurs slots. Les nouveaux mods, notamment Furnace, et les vues Storage Pipes utilisent directement ce kit.

Le [guide de style FR/EN](docs/UI_STYLE.md) contient la palette, les dimensions, les règles d'accessibilité et les exemples d'écran, bouton, champ et jauge. Le [guide de migration](docs/UI_MIGRATION.md) documente les thèmes remplacés et les différences réelles entre les dépôts.

Les [notes de version du kit UI](docs/UI_RELEASE_NOTES.md) recensent les fichiers migrés, les versions et les validations effectuées.

## Composants HomeLink

Les nouveaux contrats d'intégration sont décrits dans [Ports d'objets](docs/ITEM_PORTS.md)
et le [guide du connecteur HomeLink](docs/CONNECTOR.md).
La [migration historique 1.10](docs/MIGRATION_1_10.md) décrit sa livraison ; la
[migration UI](docs/UI_MIGRATION.md) décrit la norme cliente actuelle. Les
[tests intermods](integration-tests/README.md) et leur [workflow CI](docs/INTEGRATION_CI.md)
vérifient les vrais appareils de plusieurs mods sur le même serveur.

### HomeLink Circuit Board

Composant électronique de base des appareils HomeLink. Il s'assemble exclusivement dans l'établi électronique avec du cuivre, de la redstone et du quartz vanilla. Chaque assemblage produit deux cartes.

### HomeLink Microprocessor

Composant de traitement des appareils HomeLink complexes. Il s'assemble exclusivement dans l'établi électronique avec une carte HomeLink, des pépites d'or, du cuivre, de la redstone et du quartz.

### HomeLink Communication Module

Module de communication, de réseau et de détection. Il s'assemble exclusivement dans l'établi électronique avec une carte HomeLink, un microprocesseur HomeLink, du cuivre, de la redstone, du quartz et un éclat d'améthyste.

### HomeLink Control Module

Module de commande des moteurs, mécanismes et machines automatisées. Il s'assemble exclusivement dans l'établi électronique avec une carte HomeLink, un microprocesseur HomeLink, un comparateur, du cuivre, de la redstone et du fer.

### Identifiants des composants

| Composant | ID | Rôle |
| --- | --- | --- |
| Carte électronique | `homecore:homelink_circuit_board` | Électronique de base. |
| Microprocesseur | `homecore:homelink_microprocessor` | Traitement et logique. |
| Module de communication | `homecore:homelink_communication_module` | Communication, réseau et détection. |
| Module de contrôle | `homecore:homelink_control_module` | Commande et automatisation. |

Ces quatre composants se fabriquent à l'établi électronique. Les deux modules ont chacun leur fonction propre. Leurs identifiants sont stables : les mods consommateurs peuvent les utiliser sans dépendre les uns des autres. HomeCore ne dépend d'aucun de ces mods.

## Établi électronique HomeLink

`homecore:electronics_workbench` est l'établi commun d'assemblage électronique. Il ne consomme ni énergie ni combustible. Il se fabrique à la table de craft avec cette disposition :

```text
I R I    I = Lingot de fer       R = Redstone
C W C    C = Lingot de cuivre    W = Table de craft
P P P    P = Planches quelconques
```

L'établi occupe deux blocs adjacents, avec un plan de travail continu et un inventaire partagé. Laisser les deux cases libres lors de la pose. Chaque moitié ouvre la même interface ; en survie, casser l'une d'elles rend un seul établi et son contenu. Les anciens établis d'un bloc restent utilisables avec leur inventaire sauvegardé ; les casser puis les reposer applique la nouvelle largeur.

Son cadre en acier et ses panneaux encastrés reprennent le style visuel de HomeLink Farm. Les textures d'acier sont incluses dans HomeCore ; Farm n'est pas requis.

Déposer les matériaux dans les neuf cases d'entrée à gauche, choisir un composant par son onglet, puis le nombre d'**objets finis**, jusqu'à 64. Les cartes se produisent par paires ; les microprocesseurs et modules à l'unité. Les quantités rapides et le bouton MAX simplifient le choix du lot. Les ingrédients affichent les quantités disponibles et nécessaires. La sortie reste visible à côté des matériaux.

Assembler un prototype par lot, par glisser-déposer ou en cliquant sur une pièce puis sur son emplacement. Les placements corrects s'enclenchent ; une erreur ne consomme aucun matériau. Une courte animation suit la validation, puis le lot apparaît dans la sortie.

Le serveur réserve les ingrédients avant le prototype et vérifie chaque placement. Annuler, fermer l'écran du prototype ou se déconnecter avant validation restitue les matériaux. Les prototypes interrompus sont aussi remboursés au rechargement du monde. Une production validée continue après fermeture de l'écran et reprend au chargement de l'établi. L'inventaire est verrouillé pendant la réservation. Casser l'établi restitue les objets stockés, la sortie et les ingrédients réservés. Les quatre composants n'ont pas de recette à la table de craft vanilla.

Les autres mods HomeLink peuvent ajouter des recettes `homecore:electronics` dans leurs propres ressources, sans dépendre les uns des autres. Les contrats Device, Metric, Action, Event et HomeNetwork restent inchangés.

Le [format des recettes électroniques](docs/ELECTRONICS.md) décrit le schéma JSON extensible.

Avec JEI (19.0 ou plus récent) ou REI (16.0 ou plus récent), les recettes `homecore:electronics`, y compris celles des autres mods, s'affichent dans une catégorie « établi électronique » avec les quantités requises par assemblage. L'établi y apparaît comme poste de fabrication. Ces deux mods sont optionnels et côté client : HomeCore fonctionne sans eux. En développement, `-PwithJei` ou `-PwithRei` les charge dans les lancements du client. Chaque objet de HomeCore y a aussi une page d'information (onglet « i » de JEI, « Information » de REI) qui explique son rôle, en français et en anglais.

## Contrats et cycle de vie

Les contrats publics se trouvent dans `fr.lkdm.homecore.api.*`. La JavaDoc est générée dans `build/docs/javadoc/index.html`. Un consommateur n'a pas besoin d'importer les packages d'implémentation hors `api`.

Un `DashboardDevice` est un appareil logique : BlockEntity, système de jeu ou machine sans position. Son UUID doit rester identique après sauvegarde/rechargement. Son type, ses métriques et ses actions restent stables tant qu'il est enregistré ; les valeurs peuvent changer.

Implémentation minimale, avec les types importés depuis l'API :

```java
public final class ExampleDevice implements DashboardDevice {
    private final UUID persistentId;
    public ExampleDevice(UUID persistentId) { this.persistentId = persistentId; }
    @Override public UUID id() { return persistentId; }
    @Override public ResourceLocation deviceType() {
        return ResourceLocation.fromNamespaceAndPath("examplemod", "machine");
    }
    @Override public Component displayName() { return Component.literal("Example machine"); }
    @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
}
```

Sur le thread serveur, appeler `DashboardAPI.devices(server).register(device)`, puis `unregister(device.id())` lors du retrait ou déchargement de la source. Le registre ne balaie pas les chunks. `isValid()` écarte aussi un objet retiré lors d'une consultation. Les UUID dupliqués sont refusés.

L'[exemple compilable complet](src/examples/java/fr/lkdm/homecore/example/ExampleMachineDevice.java) montre métriques, actions, événements, provider, schéma et capability. Il est séparé du JAR distribué. Son état mémoire est une démonstration : une vraie machine sauvegarde son identité et ses valeurs dans sa propre persistance.

## Métriques et schéma

```java
private final DeviceMetric<Double> temperature = DeviceMetric.builder(
        ResourceLocation.fromNamespaceAndPath("examplemod", "temperature"),
        Component.literal("Temperature"), MetricTypes.DOUBLE, 22.5)
    .unit(fr.lkdm.homecore.api.metric.Unit.CELSIUS)
    .updatePolicy(UpdatePolicy.NORMAL)
    .build();

@Override public List<DeviceMetric<?>> metrics() { return List.of(temperature); }
```

`temperature.setValue(23.0)` valide et incrémente la révision uniquement si la valeur change. `MetricRange` utilise des bornes inclusives et un pas exact ; zéro signifie continu. Les types couvrent booléens, entiers, longs, doubles finis, texte, énumérations, pourcentage, durée, position, item, fluide et énergie. Les items/fluides transportent un identifiant et une quantité, sans inventaire complet.

Les unités sont extensibles avec `new Unit(id, symbol)`. Un `MetricType` personnalisé est utilisable localement ; sa valeur doit appartenir aux représentations fermées de `WireValue` pour être exposée sur le réseau.

`device.schema()` décrit métriques, actions et événements pour construire une interface. Conserver leurs instances ; ne pas les recréer à chaque appel. REALTIME et ON_CHANGE sont examinés chaque tick, FAST tous les 5 ticks, NORMAL tous les 20, SLOW tous les 100. Seules les révisions modifiées sont envoyées. STATIC apparaît uniquement dans les snapshots.

## Actions et permissions

Avec `progress` une métrique double exposée par l'appareil :

```java
private final DeviceAction<Double> setProgress = DeviceAction.slider(
        ResourceLocation.fromNamespaceAndPath("examplemod", "set_progress"),
        Component.literal("Set progress"), 0, 100)
    .step(1)
    .requiredPermission(Permission.CONTROL.id())
    .handler((context, value) -> {
        progress.setValue(value);
        return ActionResult.success();
    }).build();
```

Exposer cette instance via `actions()`. Type, bornes, pas, options SELECT et prédicat supplémentaire sont vérifiés avant le handler. Les boutons utilisent `fr.lkdm.homecore.api.action.Unit.INSTANCE`. Les validateurs doivent être purs. Les handlers s'exécutent sur le thread appelant.

Pour une demande d'un joueur, appeler sur le thread serveur :

```java
ActionResult result = DashboardAPI.executeAction(
    serverPlayer, networkId, deviceId, actionId, 73.0);
```

L'identité vient du `ServerPlayer` authentifié. Ne pas exposer directement `DeviceAction.execute` aux requêtes distantes : cette méthode valide le paramètre mais n'accorde aucune autorisation.

La passerelle vérifie existence, appartenance au réseau, CONTROL, permission spécifique, état ONLINE et paramètre. Une permission inconnue est refusée. La limite commune par joueur autorise une rafale de 10 demandes rechargée à 10/s. Les demandes refusées/invalides consomment aussi ce budget ; une reconnexion ne le réinitialise pas.

| Rôle | Permissions par défaut |
| --- | --- |
| OWNER | Toutes les permissions reconnues |
| ADMIN | VIEW, CONTROL, AUTOMATE, CONFIGURE, MANAGE_NETWORK |
| MEMBER | VIEW, CONTROL, AUTOMATE |
| VIEWER | VIEW |

`DashboardAPI.hasPermission` vérifie un droit courant. La mutation directe d'un réseau par Java est une API serveur de confiance : un mod qui l'expose aux joueurs doit vérifier MANAGE_NETWORK lui-même. Un `PermissionValidator` personnalisé s'utilise dans une passerelle construite par le consommateur ; il ne remplace pas automatiquement la politique du transport HomeCore.

## Réseaux persistants

`DashboardAPI.networks(server)` expose `createNetwork`, `deleteNetwork`, `getNetwork`, `getNetworksForPlayer`, `addDevice`, `removeDevice`, `getDevices`, `setMember` et `removeMember` sur le thread serveur. Les snapshots sont immuables. Le propriétaire ne peut pas être retiré ou rétrogradé par ces méthodes.

Les réseaux utilisent les SavedData de l'Overworld, partagées entre dimensions. Leurs UUID d'appareils survivent au redémarrage ; les intégrations réinscrivent les objets Java au chargement. CONNECTED, UNREACHABLE et OFFLINE sont disponibles pour les consommateurs ; aucun calcul radio de portée n'est fourni.

## Événements

Déclarer les identifiants dans `DashboardDevice.eventTypes()`, puis publier sur le thread serveur :

```java
DashboardAPI.events(server).publish(new DeviceEvent(
    eventId, device.id(), Instant.now(), DeviceEvent.Severity.INFO,
    Map.of("state", "completed")));
```

`subscribe(listener)` accepte plusieurs auditeurs et retourne une subscription `AutoCloseable`. La fermer lors du démontage du consommateur pour libérer ses références. Le bus est fermé à l'arrêt du serveur. Les données structurées sont des champs texte nommés, sans objets exécutables. Le réseau transmet l'événement aux abonnés autorisés à voir son appareil.

## Provider de BlockEntity

Pendant l'initialisation commune du mod consommateur :

```java
DashboardAPI.registerDeviceProvider(MY_BLOCK_ENTITY_TYPE.get(),
    blockEntity -> new MyMachineDevice(blockEntity));
```

`MyMachineDevice` implémente `DashboardDevice`. Au chargement serveur de la BlockEntity :

```java
DashboardAPI.providers().discover(blockEntity)
    .ifPresent(device -> DashboardAPI.devices(server).register(device));
```

Éviter les inscriptions répétées et retirer l'appareil au déchargement. Le provider n'installe aucun scanner ; `discover` n'enregistre pas automatiquement son résultat. La surcharge avec `Class<T>` contrôle explicitement le type d'exécution ; l'autre repose sur l'association générique de `BlockEntityType<T>`.

## Capabilities

Définir un contrat Java et l'inscrire une fois via `DashboardAPI.capabilities().register(new DeviceCapability<>(id, MyCapability.class))`. Conserver le `DeviceCapability<MyCapability>` retourné. Dans l'appareil, construire `CapabilitySet.builder().add(descriptor, implementation).build()`, retourner ses `ids()` dans `capabilities()` et déléguer `capability(descriptor)` à `query`.

`device.capability(descriptor)` retourne un `Optional` typé. L'implémentation reste locale au serveur ; seuls les identifiants sont transportés. HomeCore n'impose aucun système énergie/inventaire complet.

## Lecture de stock autorisée

`fr.lkdm.homecore.api.stock` décrit un stock **en lecture seule**. Un mod de stockage
publie `StockProvider.CAPABILITY` sur son appareil ; un consommateur lit ce contrat
sans jamais importer les classes internes du stockage.

`StockProvider.observe(StockRequest)` reçoit une question bornée : un réseau, un joueur
authentifié et au plus `StockRequest.MAX_VARIANTS` variantes. L'implémentation revalide
à chaque appel l'appartenance, la permission `VIEW` sur ce réseau, le rattachement de
l'appareil et sa propre portée. Un échec renvoie `StockSnapshot.unavailable`, jamais les
données d'un autre réseau.

La réponse distingue trois faits que rien ne doit confondre :

- `StockAvailability.COMPLETE` : tout le périmètre a été observé, une variante absente
  est réellement absente ;
- `StockAvailability.PARTIAL` : une partie n'a pas pu être observée, une absence ne
  prouve donc rien ;
- `StockAvailability.UNAVAILABLE` : rien n'a été observé. **Inconnu n'est pas zéro.**

`StockAccess` sépare lire et récupérer : `READ_ONLY` signifie que le joueur voit la
quantité sans pouvoir la retirer, et un consommateur doit l'afficher ainsi.

Chaque `StockEntry` attribue ses quantités à des `StockSourceId` canoniques
(dimension + position de l'inventaire logique, par exemple la première moitié d'un
double coffre). Deux contrôleurs qui couvrent le même coffre renvoient la même identité :
un consommateur qui agrège plusieurs fournisseurs le compte donc une fois, au lieu de
doubler le stock. Lire n'extrait, ne déplace, ne réserve et ne recharge aucun chunk.

## Description publique des recettes

`fr.lkdm.homecore.api.recipe` fournit une description neutre. Le mod propriétaire d'un
type de recette enregistre son adaptateur une fois via
`RecipeDescriptors.register(type, provider)` ; HomeCore le fait pour `homecore:electronics`.
`RecipeDescriptors.describe(holder)` renvoie un `Optional<RecipeDescriptor>` vide pour un
type sans adaptateur : ce cas doit être affiché comme « recette non prise en charge »,
jamais deviné.

Les quantités d'un `RecipeDescriptor` décrivent **une opération** :
`ingredients()` est consommé une fois et `result()` porte le rendement.
`operationsFor(restant)` arrondit au supérieur — il faut 3 opérations pour 10 objets
restants avec un rendement de 4, soit 12 produits dont 2 en surplus. `maxBatchOutput()`
reste la limite du producteur : la demande d'une tâche ne l'élargit pas.

## Reçus de production

`fr.lkdm.homecore.api.production` notifie les lots **réellement terminés**.
`DashboardAPI.production(server)` expose le canal ; l'établi électronique y publie au
moment exact où le résultat apparaît dans son emplacement de sortie.

Un `ProductionReceipt` porte l'identité de transaction du lot, le joueur sous
l'autorité duquel il a été démarré, la recette, le résultat réel et les tics serveur de
démarrage et de fin. `result().getCount()` est la quantité **d'objets finis** : un lot de
64 composants rapporte 64, pas 1. Les tics serveur avancent de façon monotone et ne sont
pas modifiés par `/time set`, ce qui permet à un consommateur de refuser de créditer un
lot démarré avant l'activation de son suivi.

Un lot annulé ou remboursé n'émet aucun reçu, et reprendre l'objet fini dans le slot de
sortie n'en émet pas davantage. Le canal ne conserve pas d'historique : un consommateur
doit créditer chaque `transactionId()` au plus une fois, car un reçu peut être délivré
de nouveau.

```java
// S'abonner une fois par démarrage du serveur ; fermer l'abonnement à son arrêt.
var subscription = DashboardAPI.production(server).subscribe(receipt -> {
    // credited est persisté par le consommateur (par exemple dans son SavedData) : un reçu
    // rejoué après un rechargement ou livré deux fois n'est jamais compté deux fois.
    if (credited.add(receipt.transactionId())) progress.add(receipt.result().getItem(), receipt.quantity());
});
```

## Client Dashboard

Pour un Dashboard, utiliser `HomeCoreClient.subscribeNetwork(networkId)` : abonnement
actif jusqu'à 128 appareils, snapshots initiaux répartis sur plusieurs ticks puis
deltas uniquement. `NetworkWatchResponse.truncated()` signale explicitement les
réseaux dépassant cette limite. Les changements de liste et de permissions sont
transmis sans recharger les appareils conservés ; les événements couvrent tout le
réseau autorisé. HomeCore 1.14.0 utilise le protocole réseau 2, requis sur les deux
côtés. L'ancienne API paginée reste disponible.

Depuis du code client uniquement, `HomeCoreClient.requestDevices(Optional.empty(), 0)` demande les réseaux visibles. Passer ensuite `Optional.of(networkId)` pour recevoir appareils, snapshots et changements. Une page contient au maximum 16 identifiants ; utiliser `nextOffset` pour continuer. Une seule page d'appareils est active par joueur.

`HomeCoreClient.executeAction(networkId, deviceId, actionId, parameter)` retourne l'UUID de corrélation. `ClientDeviceCache.INSTANCE.listen(...)` observe les réponses, résultats et deltas. Fermer l'auditeur et appeler `unsubscribe(networkId)` quand le Dashboard n'en a plus besoin. La déconnexion vide état et auditeurs.

Le cache conserve le snapshot initial dans `devices()` et les dernières modifications dans `metricUpdates()` : appliquer les deltas par clé/révision pour afficher l'état courant. Il ne réécrit pas le NBT initial à chaque changement. Voir le [contrat de transport](docs/PROTOCOL.md).

## Développement et vérification

Lancer `./gradlew.bat runDebugClient`, puis utiliser `/homecore_debug` pour installer l'appareil de développement. Il expose température, énergie, activation, progression et compteur, les actions `toggle_enabled`, `set_progress`, `reset_counter` et l'événement `homecore:test_event`. Le mod de développement, les exemples et les fixtures sont exclus du JAR release principal.

```powershell
./gradlew.bat test
./gradlew.bat runPersistence -PpersistencePass=write
./gradlew.bat runPersistence -PpersistencePass=read
./gradlew.bat runNetworkSmoke
./gradlew.bat runDebugSmoke
./gradlew.bat runWorkbenchSmoke
./gradlew.bat runWorkbenchSmoke -PworkbenchLanguage=fr_fr
```

Le smoke de développement vérifie aussi la commande, l'action `set_progress` et la notification de l'événement. Les cinq valeurs initiales et les trois actions sont couvertes par les tests JUnit. Les passes de persistance utilisent le même monde isolé. Le smoke client crée un monde intégré isolé, vérifie découverte, snapshot, action, résultat et delta sans snapshot supplémentaire, puis ferme le client. Les validations effectivement réalisées sont documentées dans [VALIDATION.md](docs/VALIDATION.md).

## Contraintes de liaison

HomeNetworkManager.setReachabilityPolicy(id, predicate) installe une contrainte de liaison éphémère, à réinstaller au démarrage du serveur. Chaque politique doit accepter le réseau et l'appareil. isReachable vérifie l'appartenance et ces contraintes ; une exception refuse la liaison. Les listes, snapshots, deltas, événements et actions utilisent cette décision, en plus des permissions. HomeCore ne calcule pas de portée : cette logique appartient au consommateur. L'absence de politique conserve le comportement logique existant. Le protocole réseau reste 2.

## Renommage

HomeNetworkManager.renameNetwork(id, name) conserve l'UUID, les membres, les appareils et la date de création. Les snapshots de réseau et la sauvegarde reflètent le nouveau nom. Cette API est réservée au code serveur de confiance : un appel provenant d'un joueur doit vérifier MANAGE_NETWORK avant la mutation. Les noms doivent contenir 1 à 128 caractères et ne pas être vides.

## Rattachement des appareils

Un `DashboardDevice` dont le bloc mémorise son réseau peut implémenter `NetworkMember` : réseau enregistré, propriétaire, droit propre à la machine (`canConfigure`) et notification `homeNetworkChanged`. Un Dashboard peut alors lister le rattachement d'une machine et la déplacer sans désynchroniser son bloc.

Pour une demande de joueur, appeler `DashboardAPI.bindDevice(player, device, Optional.of(networkId))`, ou `Optional.empty()` pour détacher. HomeCore exige `canConfigure` et MANAGE_NETWORK sur le réseau de destination et sur le réseau précédent. L'appareil rejoint la destination avant de quitter l'ancien réseau ; rien ne change si une vérification échoue. Un appareil sans `NetworkMember` renvoie `NOT_SUPPORTED`. API 1.5.0.

## Marche/arrêt et renommage

Un `DashboardDevice` peut implémenter `Switchable` (`powered`, `setPowered`) et `Renamable` (`rename`). HomeCore ajoute alors à son schéma les actions standard `homecore:power` (TOGGLE, permission CONTROL) et `homecore:rename` (TEXT, 50 caractères au plus, permission CONFIGURE), après les actions déclarées par l'appareil ; une action déclarée avec le même identifiant reste prioritaire. Le nom est transmis sans espaces de bord et sans caractères de contrôle ni de formatage ; un nom vide rétablit le nom par défaut.

Ces deux actions restent disponibles tant que l'appareil n'est pas `OFFLINE`, pour pouvoir rallumer une machine éteinte ou renommer une machine en alerte. Les instantanés d'appareil portent l'état `powered`, et un changement de nom ou d'état renvoie la description de l'appareil aux Dashboards ouverts. API 1.7.0.
