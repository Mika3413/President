package fr.president.game.map

/** Couches thématiques proposées au joueur. */
enum class ThematicLayer(val label: String, val heatmap: Boolean) {
    ADMIN("Administratif", false),
    OPINION("Opinion", true),
    UNEMPLOYMENT("Chômage", true),
    POPULATION("Population", true),
    INCOME("Revenus", true),
    ENERGY("Énergie", false),
    TRANSPORT("Transports", false),
    MILITARY("Militaire", false),
    EVENTS("Crises", false),
}
