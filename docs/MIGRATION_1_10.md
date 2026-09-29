# Migration vers HomeCore 1.10.0

La version du mod et celle de l'API ont des rôles différents :

| Élément | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge de HomeCore | 21.1.250 |
| Java | 21 |
| HomeCore | 1.10.0 |
| API Java (`DashboardAPI.API_VERSION`) | 1.6.0 |
| Protocole réseau | 2 |

Les contrats de réseau existants restent utilisables. `NetworkMember` et
`DashboardAPI.bindDevice()` étaient déjà disponibles dans HomeCore 1.9.0 ; les
consommateurs doivent supprimer leurs mutations de rattachement redondantes et
appeler cette passerelle pour les demandes de joueurs.

`ItemApi.BLOCK`, `ItemPort` et `ItemPortType` sont nouveaux. Un consommateur qui
les importe doit augmenter sa dépendance minimale à HomeCore 1.10.0, même si le
reste de son intégration fonctionne avec une version précédente.

Le [connecteur commun](CONNECTOR.md) utilise la découverte des providers. Pour
qu'une machine le prenne en charge, elle doit exposer un `DashboardDevice` valide
implémentant `NetworkMember` et enregistrer son provider. Sa notification
`homeNetworkChanged` sauvegarde la nouvelle association. Les périphériques d'une
machine à plusieurs blocs doivent résoudre leur contrôleur avant de l'exposer.

## Ordre de livraison

1. Vérifier HomeCore : compilation, tests unitaires, JAR et GameTests serveur.
2. Publier la version HomeCore et vérifier sa résolution depuis un consommateur
   sans `mavenLocal()` ni substitution composite.
3. Aligner et publier Energy avec une dépendance HomeCore explicite.
4. Aligner Farm, Storage et Quarry ; vérifier chaque JAR et lancer les scénarios
   avec les vrais mods chargés ensemble.
5. Utiliser `main` comme branche commune sans réécrire l'historique distant.

Les anciennes versions citées dans les rapports de validation datés décrivent
l'environnement de ces essais. Elles ne constituent pas des instructions
d'installation pour cette version.

## Scénarios inter-mods requis

- Energy → Farm : charge effective des machines, arrêt sans HE, reprise après
  alimentation et conservation de l'énergie.
- FarmBot → Storage : sortie vers un vrai Deposit, face fermée, tampon plein et
  insertion partielle sans perte ni duplication.
- Quarry → Storage : mêmes contrôles, pause volontaire préservée et reprise
  lorsque la sortie se libère.
- HomeNetwork : propriétaire, membre autorisé, joueur sans droits, changement
  entre deux réseaux, détachement et sauvegarde/rechargement.

Les tests unitaires du contrat HomeCore ne remplacent pas ces scénarios. Les
tests visuels des clients et les essais à plusieurs joueurs restent distincts
des GameTests exécutés sur un serveur sans interface.
