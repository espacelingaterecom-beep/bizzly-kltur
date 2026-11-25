# Bizzly — Gestion (Android)

Application Android native (Kotlin & Jetpack Compose, Material 3) réécrite à partir du projet PWA **Bizzly** (*KLTUR COORPORATION — Jonathan Mambachaka*).

## Fonctionnalités portées

- **Multi-entreprises & sécurité par PIN** : création d'espace, sélecteur d'entreprise et de profil (`Gérant`, `Caisse`, `Serveur`), hachage PBKDF2-HMAC-SHA256, verrouillage temporaire après échecs répétés, autorisation PIN ponctuelle pour actions sensibles, verrouillage automatique après 15 min d'inactivité.
- **Identité visuelle dynamique** : import de logo via Android Photo Picker (`PickVisualMedia`), extraction automatique de palette (Principale, Fond foncé, Accent), personnalisation des couleurs et mode clair/sombre.
- **Tables & Encaissement** : grille adaptative (1 à 200 tables), attribution serveur, top 6 produits rapides, panier, 4 modes de paiement, calcul du rendu monnaie, aperçu ticket et export **Facture PDF**.
- **Ventes, Stock & Argent** : suivi quotidien des ventes, inventaire automatique (`Début`, `Entrées`, `Fin`, alertes stock bas/critique), versements, dépenses liées aux achats de stock, bons du personnel et calcul du reste à verser.
- **Menu QR & Exports** : génération hors-ligne du QR code menu, téléchargement en **PDF (affiche QR + menu 2 colonnes)**, export **Excel (.xlsx à 10 onglets)**, sauvegarde/restauration JSON compatible avec l'édition PWA.
- **Synchronisation multi-téléphones (Supabase)** : liaison par code 20 caractères (`XXXXX-XXXXX-XXXXX-XXXXX`), synchronisation temps réel, rotation de code et restauration des instantanés en ligne sur 14 jours.

## Compilation & tests

```sh
gradle assembleDebug
gradle :app:testDebugUnitTest
```
