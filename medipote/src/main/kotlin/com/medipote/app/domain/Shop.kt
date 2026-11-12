package com.medipote.app.domain

import com.medipote.app.data.GameState

enum class Slot(val label: String) {
    COLOR("Couleurs"),
    HAT("Chapeaux"),
    GLASSES("Lunettes"),
    NECK("Cou"),
    TOY("Objets"),
    EFFECT("Effets"),
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
        ShopItem("color_choco", "Chocolat", Slot.COLOR, 100),
        ShopItem("color_pink", "Rose bonbon", Slot.COLOR, 120),
        ShopItem("color_night", "Nuit", Slot.COLOR, 250),
        ShopItem("color_galaxy", "Galaxie", Slot.COLOR, 350),
        ShopItem("color_rainbow", "Arc-en-ciel", Slot.COLOR, 450),

        ShopItem("hat_bow", "Nœud", Slot.HAT, 50),
        ShopItem("hat_flower", "Fleur", Slot.HAT, 70),
        ShopItem("hat_cap", "Casquette", Slot.HAT, 90),
        ShopItem("hat_beanie", "Bonnet", Slot.HAT, 110),
        ShopItem("hat_party", "Chapeau de fête", Slot.HAT, 130),
        ShopItem("hat_top", "Haut-de-forme", Slot.HAT, 180),
        ShopItem("hat_headband", "Bandeau", Slot.HAT, 60),
        ShopItem("hat_straw", "Chapeau de paille", Slot.HAT, 120),
        ShopItem("hat_catears", "Oreilles de chat", Slot.HAT, 150),
        ShopItem("hat_headphones", "Casque audio", Slot.HAT, 200),
        ShopItem("hat_witch", "Chapeau de sorcier", Slot.HAT, 220),
        ShopItem("hat_halo", "Auréole", Slot.HAT, 300),
        ShopItem("hat_crown", "Couronne", Slot.HAT, 400),

        ShopItem("glasses_round", "Lunettes rondes", Slot.GLASSES, 80),
        ShopItem("glasses_sun", "Lunettes de soleil", Slot.GLASSES, 120),
        ShopItem("glasses_heart", "Lunettes cœur", Slot.GLASSES, 150),
        ShopItem("glasses_3d", "Lunettes 3D", Slot.GLASSES, 130),
        ShopItem("glasses_monocle", "Monocle", Slot.GLASSES, 160),
        ShopItem("glasses_star", "Lunettes étoiles", Slot.GLASSES, 170),

        ShopItem("neck_bowtie", "Nœud papillon", Slot.NECK, 70),
        ShopItem("neck_scarf", "Écharpe", Slot.NECK, 100),
        ShopItem("neck_stetho", "Stéthoscope", Slot.NECK, 180),
        ShopItem("neck_bandana", "Bandana", Slot.NECK, 80),
        ShopItem("neck_tie", "Cravate", Slot.NECK, 90),
        ShopItem("neck_pearls", "Collier de perles", Slot.NECK, 160),
        ShopItem("neck_medal", "Médaille d'or", Slot.NECK, 250),
        ShopItem("neck_cape", "Cape de héros", Slot.NECK, 260),

        ShopItem("toy_balloon", "Ballon", Slot.TOY, 90),
        ShopItem("toy_pillplush", "Peluche pilule", Slot.TOY, 120),
        ShopItem("toy_butterfly", "Papillon", Slot.TOY, 140),
        ShopItem("toy_teddy", "Ourson", Slot.TOY, 160),
        ShopItem("toy_fishbowl", "Bocal à poisson", Slot.TOY, 200),

        ShopItem("fx_sparkles", "Étincelles", Slot.EFFECT, 150),
        ShopItem("fx_bubbles", "Bulles", Slot.EFFECT, 150),
        ShopItem("fx_hearts", "Cœurs", Slot.EFFECT, 180),
        ShopItem("fx_confetti", "Confettis", Slot.EFFECT, 200),
        ShopItem("fx_aura", "Aura dorée", Slot.EFFECT, 320),

        ShopItem("bg_room", "Chambre", Slot.BACKGROUND, 0),
        ShopItem("bg_meadow", "Prairie", Slot.BACKGROUND, 100),
        ShopItem("bg_beach", "Plage", Slot.BACKGROUND, 150),
        ShopItem("bg_night", "Nuit étoilée", Slot.BACKGROUND, 200),
        ShopItem("bg_clinic", "Cabinet médical", Slot.BACKGROUND, 150),
        ShopItem("bg_autumn", "Forêt d'automne", Slot.BACKGROUND, 180),
        ShopItem("bg_snow", "Neige", Slot.BACKGROUND, 200),
        ShopItem("bg_rainbow", "Arc-en-ciel", Slot.BACKGROUND, 220),
        ShopItem("bg_city", "Ville la nuit", Slot.BACKGROUND, 250),
        ShopItem("bg_ocean", "Fonds marins", Slot.BACKGROUND, 280),
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
