# Tests d'intégration HomeLink

Ce projet charge HomeCore, Energy, Farm, Storage et Quarry ensemble dans un serveur GameTest NeoForge. Les appareils sont les classes de production des mods : aucun dropper de substitution ni injection d'énergie dans Farm.

La [CI d'intégration](../docs/INTEGRATION_CI.md) permet d'exécuter ce harnais avec cinq révisions Git explicites.

Depuis HomeCore :

```powershell
.\gradlew.bat -p integration-tests classes
.\gradlew.bat -p integration-tests runGameTestServer
```

Sous Linux, employer `./gradlew`. Java 21 est nécessaire. Les dépôts frères attendus sont `HomeLinkEnergy` 0.2.2, `FarmLink` 1.2.0, `HomeLink Storage` 1.1.0 et `HomeLinkQuarry` 1.2.0. Leurs chemins sont réglables avec `-Penergy_dir`, `-Pfarm_dir`, `-Pstorage_dir` et `-Pquarry_dir`. Une autre version se sélectionne explicitement avec les propriétés correspondantes ; le harnais refuse tout checkout dont la version ne correspond pas à celle demandée. Ces versions doivent être préparées ou publiées avant utilisation ; les anciens checkouts ne sont pas mis à jour automatiquement.

Le harnais compile leurs sources et ressources réelles dans des source sets séparés, sans exécuter leurs builds ni écrire dans leurs dépôts. HomeCore 1.10.0 vient du build composite parent. Aucun dépôt Maven local n'est utilisé. Les métadonnées de test exigent HomeCore 1.10.0 ; elles ne constituent pas une publication des consommateurs.

Les tests couvrent :

- Production réelle d'un panneau Energy et transfert automatique vers une station Farm, avec conservation des HE.
- Sortie automatique FarmBot Station vers un vrai Storage Deposit et interdiction des opérations contraires aux ports INPUT/OUTPUT.
- Sortie automatique arrière Quarry vers un vrai Deposit presque plein, avec conservation exacte des objets.
- Face écran fermée du Deposit puis reprise du transfert après rotation.
- Même liaison HomeNetwork pour les quatre familles, refus sans permission sur le réseau quitté, migration, propriétaire préservé, détachement et sauvegarde Storage.

Les stocks initiaux de récoltes et minerai sont des fixtures. Ces tests isolent les transferts ; ils ne prétendent pas couvrir une récolte ou un chantier de minage complet. Les GameTests unitaires des mods restent complémentaires.

Les journaux sont dans `integration-tests/build/server/logs`. Un projet compilé n'est pas une preuve de réussite des GameTests : seul le résultat du serveur valide les interactions.

Validation locale du 29 septembre 2026 : **les cinq GameTests ont réussi** sur
NeoForge 21.1.251 avec les versions ci-dessus dans les copies locales de validation.
Les builds natifs et GameTests des consommateurs ont aussi été exécutés séparément.
Voir les résultats et les limites dans le [rapport HomeCore](../docs/VALIDATION.md).
