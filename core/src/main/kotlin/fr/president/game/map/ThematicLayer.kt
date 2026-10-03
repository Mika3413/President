package fr.president.game.map

/** Couches thématiques proposées au joueur. */
enum class ThematicLayer(val label: String, val heatmap: Boolean, val legendLow: String = "", val legendHigh: String = "") {
    ADMIN("Administratif", false),
    OPINION("Opinion", true, "Hostile", "Favorable"),
    UNEMPLOYMENT("Chômage", true, "Élevé", "Faible"),
    POPULATION("Population", true, "Peu dense", "Très dense"),
    INCOME("Revenus", true, "Modestes", "Élevés"),
    HEALTH("Santé", true, "Désert médical", "Bien doté"),
    SECURITY("Sécurité", true, "Délinquance forte", "Calme"),
    INDUSTRY("Industrie", true, "Peu industriel", "Très industriel"),
    AGRICULTURE("Agriculture", true, "Peu agricole", "Très agricole"),
    POLLUTION("Pollution", true, "Air pollué", "Air pur"),
    ENERGY("Énergie", false),
    TRANSPORT("Transports", false),
    MILITARY("Militaire", false),
    EVENTS("Crises", false),
}
