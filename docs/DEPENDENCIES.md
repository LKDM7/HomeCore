# Dépendances et publication

La version publiée de HomeCore est **1.10.0**, avec l’API **1.6.0**, pour Minecraft **1.21.1** et NeoForge **21.1.250**. Une modification locale ne publie aucun artefact et ne met pas automatiquement les autres mods à jour.

## Publication Maven

Les coordonnées sont `fr.lkdm.homecore:homecore:1.10.0`. La configuration publie le JAR, les sources, la Javadoc et les métadonnées Maven sur `https://maven.pkg.github.com/LKDM7/HomeCore`.

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

Chaque consommateur doit déclarer une version exacte dans Gradle et une plage compatible dans `neoforge.mods.toml`. Les mods qui appellent ItemPort nécessitent au minimum HomeCore 1.10.0. `NetworkMember` et `DashboardAPI.bindDevice()` existent depuis HomeCore 1.9.0.

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
    implementation 'fr.lkdm.homecore:homecore:1.10.0'
}
```

Les settings préparés pour Farm, Storage, Quarry et Energy privilégient les sources voisines lorsqu’elles existent. Leur version déclarée doit correspondre exactement à la version demandée. Les chemins peuvent être précisés avec `-Phomecore_dir=<chemin>` et `-Penergy_dir=<chemin>` ; un chemin explicite inexistant provoque une erreur. `-PuseLocalDependencies=false` sélectionne les artefacts Maven publiés. Un build composite utilise les sources locales ; il ne les télécharge pas et ne les publie pas.

Le retrait de `mavenLocal()` des consommateurs et la publication de staging Energy
ont été validés dans des copies de travail, avec Energy 0.2.2 et HomeCore 1.10.0.
Le [rapport de validation](VALIDATION.md) détaille les tests et la résolution Maven
indépendante. Les correctifs préparés sont conservés dans
`build/homelink-migration-patches`, hors Git ; ils sont maintenant appliqués et
poussés aux dépôts consommateurs. HomeCore et Energy sont publiés sur Maven,
et les CI des consommateurs ont réussi avec ces artefacts. Ils remplacent notamment le bootstrap
Energy historique qui pointait vers HomeCore 1.9.0.

## Vérifications

La CI HomeCore exécute `build test verifyReleaseJar`, puis `runPersistence -PpersistencePass=write` et `runPersistence -PpersistencePass=read`, et prépare le dépôt Maven de staging. Les rapports et JAR sont conservés comme artefacts du workflow.

Le projet [integration-tests](../integration-tests/README.md) charge Energy, Farm,
Storage et Quarry ensemble. Le [workflow intermods](INTEGRATION_CI.md) permet de
vérifier cinq commits explicitement choisis. Les cinq dépôts utilisent désormais
`main` et le workflow intermods a réussi. Le [rapport de publication](PUBLICATION_1_10.md)
donne les commits et exécutions exacts ; un build de HomeCore seul ne prouve pas
leur compatibilité.

Références : [publication Gradle dans GitHub Actions](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-gradle), [authentification GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry), [builds composites Gradle](https://docs.gradle.org/current/userguide/composite_builds.html).
