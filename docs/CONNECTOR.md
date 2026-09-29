# Connecteur HomeLink

`homecore:homelink_connector` est l'outil commun pour rattacher un appareil à un
HomeNetwork. Il fonctionne avec les appareils découverts par un provider HomeCore
et implémentant `NetworkMember`.

Le connecteur se fabrique à la table de craft avec un module de communication
HomeLink en haut à droite et un lingot de fer en bas à gauche d'une grille 2 × 2.

1. Utiliser le connecteur dans le vide pour parcourir les réseaux que le joueur
   peut administrer. Le nom du réseau sélectionné apparaît dans le message.
2. Cliquer sur un appareil compatible pour le rattacher au réseau sélectionné.
3. Accroupi, cliquer sur un appareil déjà relié pour sélectionner son réseau.
4. Accroupi, utiliser dans le vide pour effacer la sélection de l'outil.

Effacer la sélection ne détache aucun appareil. La sélection est conservée sur
l'objet ; les permissions sont contrôlées à nouveau à chaque utilisation.

Le connecteur appelle `DashboardAPI.bindDevice()`. Il exige le droit de configurer
la machine et `MANAGE_NETWORK` sur le réseau d'arrivée ainsi que sur le réseau
précédent. Un refus conserve le rattachement existant. `homeNetworkChanged`
informe l'appareil afin qu'il sauvegarde son nouveau réseau et actualise son écran.
Le propriétaire de la machine n'est pas transféré avec son rattachement.

Les outils spécialisés restent utiles pour définir les zones de culture ou de
minage et les associations fonctionnelles propres à un mod. Les câbles d'énergie
et les ports d'objets sont des connexions physiques distinctes du HomeNetwork.
