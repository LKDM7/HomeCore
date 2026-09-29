# Livraison HomeLink du 29 septembre 2026

Les cinq dépôts ci-dessous ont été relus, commités et poussés sans réécriture
d'historique. Leur branche par défaut est `main`. Les configurations de travail
locales restent hors Git et les licences et attributions sont conservées.

| Mod | Version | Commit fonctionnel validé | CI |
| --- | --- | --- | --- |
| HomeCore | 1.10.0 | `49d22d8c0e388b4827d55cce96dc4c106c6fde4d` | [Réussie](https://github.com/LKDM7/HomeCore/actions/runs/36597959830) |
| Energy | 0.2.2 | `fa6b487c880271863ed7c340f4b8e1e5054278fe` | [Réussie](https://github.com/LKDM7/HomeLink-Energy/actions/runs/36598070445) |
| Farm | 1.2.0 | `e0048a86b97262fc80a1ccd09ce30149e4b938d6` | [Réussie](https://github.com/LKDM7/HomeLink-Farm/actions/runs/36598081853) |
| Storage | 1.1.0 | `6c0470000e87143f8724a352c40109b35eb0960d` | [Réussie](https://github.com/LKDM7/HomeLink-Storage/actions/runs/36598087262) |
| Quarry | 1.2.0 | `28656d2de6dcdb6d7767a05f7f70da87de40186b` | [Réussie](https://github.com/LKDM7/HomeLink-Quarry/actions/runs/36598092913) |

## Dépendances publiées

- `fr.lkdm.homecore:homecore:1.10.0` : [publication Maven réussie](https://github.com/LKDM7/HomeCore/actions/runs/36597982755).
- `fr.lkdm.homelink.energy:homelink_energy:0.2.2` : [publication Maven réussie](https://github.com/LKDM7/HomeLink-Energy/actions/runs/36598392434).

Les publications comprennent leurs métadonnées et sources ; HomeCore fournit
aussi sa Javadoc. Les projets consommateurs indépendants vérifient les archives
de staging et les dépendances transitives avant publication.

Les CI consommateurs utilisent `-PuseLocalDependencies=false` et les versions
publiées, sans Maven Local ni substitution composite. Les premières tentatives
ont précédé la disponibilité des dépendances ; les relances référencées ci-dessus
ont réussi après publication de HomeCore, puis d'Energy. L'accès Maven entre
ces dépôts a donc été vérifié avec leurs jetons de workflow.

## Validation intermods

Le [workflow intermods a réussi](https://github.com/LKDM7/HomeCore/actions/runs/36598112543)
avec les cinq commits exacts du tableau. Il couvre Energy vers Farm, FarmBot vers
Storage, Quarry vers Storage, les faces du Deposit et les permissions HomeNetwork.
Les rapports incluent les révisions et les journaux serveur.

Les CI natives vérifient aussi les JAR et les suites propres aux mods. Farm et
Storage exécutent la persistance en deux processus ; HomeCore fait de même.
Les tests visuels et multijoueurs ne font pas partie de cette livraison.

Les modifications préexistantes du checkout Dashboard `HomeLink` n'ont pas été
incluses dans les commits de cette migration. Les branches historiques `master`
des consommateurs sont conservées ; les développements et la CI ciblent `main`.
