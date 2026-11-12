package com.medipote.app.domain

import com.medipote.app.data.GameState

enum class Slot(val label: String) {
    COLOR("Couleurs"),
    HAT("Chapeaux"),
    GLASSES("Lunettes"),
    NECK("Cou"),
    BACKGROUND("Décors"),
}

data class ShopItem(val id: String, val name: String, val slot: Slot, val price: Int)

object Shop {
    val items: List<ShopItem> = listOf(
        ShopItem("color_mint", "Menthe", Slot.COLOR, 0),
        ShopItem("color_peach", "Pêche", Slot.COLOR, 60),
        ShopItem("color_sky", "Ciel", Slot.COLOR, 80),
        ShopItem("color_lavender", "Lavande", Slot.COLOR, 80),
        ShopItem("color_lemon", "Citron", Slot.COLOR, 100),
        ShopItem("color_coral", "Corail", Slot.COLOR, 120),
        ShopItem("color_galaxy", "Galaxie", Slot.COLOR, 350),

        ShopItem("hat_bow", "Nœud", Slot.HAT, 50),
        ShopItem("hat_flower", "Fleur", Slot.HAT, 70),
        ShopItem("hat_cap", "Casquette", Slot.HAT, 90),
        ShopItem("hat_beanie", "Bonnet", Slot.HAT, 110),
        ShopItem("hat_party", "Chapeau de fête", Slot.HAT, 130),
        ShopItem("hat_top", "Haut-de-forme", Slot.HAT, 180),
        ShopItem("hat_crown", "Couronne", Slot.HAT, 400),

        ShopItem("glasses_round", "Lunettes rondes", Slot.GLASSES, 80),
        ShopItem("glasses_sun", "Lunettes de soleil", Slot.GLASSES, 120),
        ShopItem("glasses_heart", "Lunettes cœur", Slot.GLASSES, 150),
        ShopItem("glasses_monocle", "Monocle", Slot.GLASSES, 160),

        ShopItem("neck_bowtie", "Nœud papillon", Slot.NECK, 70),
        ShopItem("neck_scarf", "Écharpe", Slot.NECK, 100),
        ShopItem("neck_stetho", "Stéthoscope", Slot.NECK, 180),
        ShopItem("neck_medal", "Médaille d'or", Slot.NECK, 250),

        ShopItem("bg_room", "Chambre", Slot.BACKGROUND, 0),
        ShopItem("bg_meadow", "Prairie", Slot.BACKGROUND, 100),
        ShopItem("bg_beach", "Plage", Slot.BACKGROUND, 150),
        ShopItem("bg_night", "Nuit étoilée", Slot.BACKGROUND, 200),
        ShopItem("bg_space", "Espace", Slot.BACKGROUND, 300),
    )

    fun item(id: String): ShopItem? = items.firstOrNull { it.id == id }

    fun buy(game: GameState, item: ShopItem): GameState? {
        if (item.id in game.owned || game.points < item.price) return null
        return game.copy(points = game.points - item.price, owned = game.owned + item.id)
            .let { equip(it, item) }
    }

    /** Equips an owned item; tapping an equipped hat, glasses or neck item takes it off. */
    fun equip(game: GameState, item: ShopItem): GameState {
        if (item.id !in game.owned) return game
        val current = game.equipped[item.slot.name]
        val removable = item.slot != Slot.COLOR && item.slot != Slot.BACKGROUND
        val equipped = if (current == item.id && removable) game.equipped - item.slot.name
        else game.equipped + (item.slot.name to item.id)
        return game.copy(equipped = equipped)
    }
}
