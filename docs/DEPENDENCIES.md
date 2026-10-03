# Dépendances et publication

La version de développement de ce checkout est **HomeCore 1.14.0**, avec l’API **1.9.0**, pour Minecraft **1.21.1** et NeoForge **21.1.250**. Le kit UI client nécessite cette version. Une modification locale ne publie aucun artefact et ne met pas automatiquement les autres mods à jour ; ce document n’annonce pas la disponibilité de 1.14.0 dans un registry distant.

La publication HomeCore **1.10.0 / API 1.6.0** est documentée dans le [rapport historique](PUBLICATION_1_10.md). Ses preuves de publication et de CI concernent les versions et commits de ce rapport.

## Publication Maven

Les coordonnées de cette livraison sont `fr.lkdm.homecore:homecore:1.14.0`. La configuration de publication peut envoyer le JAR, les sources, la Javadoc et les métadonnées Maven sur `https://maven.pkg.github.com/LKDM7/HomeCore` lorsqu’une publication est explicitement exécutée.

Le workflow **Publish Maven package**, déclenché manuellement depuis `main`, demande la version exacte. Il exécute build, tests unitaires, contrôle du JAR et GameTests de persistance dans deux processus avant l’envoi. Il utilise le `GITHUB_TOKEN` du workflow avec `packages: write`. Sa présence dans le dépôt ne signifie pas que le package est déjà disponible.

Pour préparer et examiner une publication sans l’envoyer au registre :

```text
gradlew.bat publishMavenJavaPublicationToStagingRepository
```

Le résultat est placé dans `build/staging-repository`. Cette tâche dépend de `check`, mais les deux passes `runPersistence` doivent également réussir avant publication manuelle.

Vérifier ensuite la résolution du paquet par un consommateur indépendant :

```text
gradlew.bat -p gradle/publication-smoke verifyPublishedArtifacts --no-configuration-cache
```

Ce projet utilise uniquement le dépôt de staging, sans `mavenLocal()` ni build
composite. Il contrôle le JAR, les sources, la Javadoc, le POM et les métadonnées
Gradle, puis compare les archives aux artefacts produits par HomeCore.

Pour publier depuis un environnement autorisé :

```text
gradlew.bat publishMavenJavaPublicationToGitHubPackagesRepository --no-configuration-cache
```

Les identifiants sont lus depuis `githubUser` et `githubToken` dans les propriétés Gradle utilisateur, ou `GITHUB_ACTOR` et `GITHUB_TOKEN` dans l’environnement. Ne pas les placer dans les fichiers suivis par Git. GitHub Packages demande une authentification également pour lire les packages Maven publics ; un jeton de lecture doit disposer de `read:packages`. Les workflows d’autres dépôts peuvent nécessiter un accès explicite au package.

## Consommateurs

Chaque consommateur doit déclarer une version exacte dans Gradle et une plage compatible dans `neoforge.mods.toml`. Les mods qui utilisent le [HomeLink UI Kit](UI_STYLE.md) nécessitent HomeCore **1.14.0**, avec la plage `[1.14.0,2.0.0)`. ItemPort existe depuis HomeCore 1.10.0 ; `NetworkMember` et `DashboardAPI.bindDevice()` depuis HomeCore 1.9.0.

Après publication du paquet, un consommateur peut configurer son dépôt ainsi :

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/LKDM7/HomeCore')
        credentials {
            username = providers.gradleProperty('githubUser')
                .orElse(providers.environmentVariable('GITHUB_ACTOR')).getOrElse('')
            password = providers.gradleProperty('githubToken')
                .orElse(providers.environmentVariable('GITHUB_TOKEN')).getOrElse('')
        }
        content { includeGroup 'fr.lkdm.homecore' }
    }
}
dependencies {
    implementation 'fr.lkdm.homecore:homecore:1.14.0'
}
```

Les settings des consommateurs Dashboard, Storage, Farm, Quarry, Energy et Tasks privilégient les sources HomeCore voisines lorsqu’elles existent. Leur version déclarée doit correspondre exactement à la version demandée. Les chemins peuvent être précisés avec `-Phomecore_dir=<chemin>` et, pour les consommateurs Energy, `-Penergy_dir=<chemin>` ; un chemin explicite inexistant provoque une erreur. `-PuseLocalDependencies=false` demande la résolution des artefacts Maven ; elle ne réussit que si la version demandée a réellement été publiée et est accessible. Un build composite utilise les sources locales ; il ne les télécharge pas et ne les publie pas.

Historiquement, le retrait de `mavenLocal()` des consommateurs et la publication de staging Energy
ont été validés dans des copies de travail, avec Energy 0.2.2 et HomeCore 1.10.0.
Le [rapport de validation](VALIDATION.md) détaille les tests et la résolution Maven
indépendante. Les correctifs préparés sont conservés dans
`build/homelink-migration-patches`, hors Git ; ils sont maintenant appliqués et
poussés aux dépôts consommateurs. La publication Maven et les CI de ces versions historiques
sont décrites dans [PUBLICATION_1_10.md](PUBLICATION_1_10.md). Ces résultats ne valident
pas HomeCore 1.14.0 ni le nouveau kit UI. Ils remplaçaient notamment le bootstrap
Energy historique qui pointait vers HomeCore 1.9.0.

## Vérifications

La CI HomeCore exécute `build test verifyReleaseJar`, puis `runPersistence -PpersistencePass=write` et `runPersistence -PpersistencePass=read`, et prépare le dépôt Maven de staging. Les rapports et JAR sont conservés comme artefacts du workflow.

Le projet [integration-tests](../integration-tests/README.md) charge HomeCore,
Dashboard, Storage, Farm, Quarry, Energy et Tasks ensemble. Le [workflow intermods](INTEGRATION_CI.md)
demande sept commits explicitement choisis et vérifie les dépendances réelles des
consommateurs. Ses GameTests et son smoke client doivent être exécutés pour
valider ces commits ; la présence du workflow ne prouve pas leur compatibilité.
Le [rapport historique](PUBLICATION_1_10.md) donne les anciennes exécutions exactes.

## English

This checkout targets HomeCore **1.14.0 / public API 1.9.0**. Consumers of the
client UI kit must declare `fr.lkdm.homecore:homecore:1.14.0` and the NeoForge
runtime range `[1.14.0,2.0.0)`. The adjacent composite uses local sources; it does
not publish an artifact or fetch updates. Maven resolution requires the requested
version to have actually been published and to be accessible. This document does
not claim HomeCore 1.14.0 is available in a remote registry.

The linked 1.10 publication report is historical evidence for its recorded versions
and commits. The current integration workflow takes seven explicit commit SHAs
and loads HomeCore with all six consumers; its configuration alone is not a
successful validation run.

Références : [publication Gradle dans GitHub Actions](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-gradle), [authentification GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry), [builds composites Gradle](https://docs.gradle.org/current/userguide/composite_builds.html).
