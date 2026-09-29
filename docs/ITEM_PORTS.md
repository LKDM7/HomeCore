# Ports d'objets HomeLink

HomeCore 1.10.0, API 1.6.0, fournit le contrat `fr.lkdm.homecore.api.item`.
La capability `ItemApi.BLOCK` expose un `ItemPort` par face du bloc. Le sens est
défini du point de vue de la machine :

| Type | Insertion | Extraction |
| --- | --- | --- |
| `INPUT` | Oui | Non |
| `OUTPUT` | Non | Oui |
| `BOTH` | Oui | Oui |

`ItemPort` étend `IItemHandler`. Les indices, les limites des emplacements, les
reliquats et le paramètre `simulate` suivent le contrat NeoForge. Une simulation
ne change aucun inventaire. Le demandeur ne doit pas modifier les piles renvoyées
par `getStackInSlot`.

Créer et conserver un adaptateur dans la BlockEntity :

```java
private final ItemPort input = ItemApi.of(inventory, ItemPortType.INPUT);
```

L'inscrire pendant `RegisterCapabilitiesEvent`, en respectant les faces physiques
de la machine :

```java
event.registerBlockEntity(ItemApi.BLOCK, MY_BLOCK_ENTITY.get(),
    (block, side) -> block.acceptsItemsFrom(side) ? block.inputPort() : null);
```

Le fournisseur choisit aussi le comportement lorsque `side` vaut `null`. Il ne
doit pas exposer un inventaire interne par une face qui devrait rester fermée.
L'adaptateur garde les restrictions et notifications de l'inventaire d'origine.
La capability NeoForge standard peut rester disponible en parallèle pour les
entonnoirs et les tuyaux d'autres mods.

Un producteur consulte la face opposée du bloc voisin avec `ItemApi.BLOCK`, puis
vérifie `port.type().canReceive()`. Il ne retire que la quantité effectivement
acceptée. Les transferts s'exécutent sur le thread serveur, entre chunks déjà
chargés. Cette API ne fournit pas de transaction persistante entre inventaires.

Le standard prévu pour les consommateurs est `INPUT` sur Storage Deposit,
`OUTPUT` sur FarmBot Station et `OUTPUT` sur Quarry. Le type remplace les tags de
noms de blocs pour la reconnaissance des entrées compatibles. Chaque mod doit
déclarer HomeCore 1.10.0 au minimum lorsqu'il utilise cette API.
