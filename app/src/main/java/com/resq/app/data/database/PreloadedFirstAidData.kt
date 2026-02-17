package com.resq.app.data.database

import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.data.model.MediaType

/**
 * Provides preloaded first aid data for initial database population.
 */
object PreloadedFirstAidData {

    fun getCategories(): List<FirstAidCategoryEntity> = listOf(
        FirstAidCategoryEntity(
            id = 1,
            name = "CPR & Choking",
            nameTagalog = "CPR at Pagkabulunan",
            description = "Life-saving techniques for cardiac arrest and airway obstruction",
            descriptionTagalog = "Mga teknik na nagliligtas ng buhay para sa cardiac arrest at pagkabulunan",
            iconResName = "ic_cpr",
            imageResName = "img_cpr",
            displayOrder = 1
        ),
        FirstAidCategoryEntity(
            id = 2,
            name = "Wounds & Bleeding",
            nameTagalog = "Sugat at Pagdurugo",
            description = "Treatment for cuts, wounds, and bleeding control",
            descriptionTagalog = "Paggamot sa mga hiwa, sugat, at pagkontrol ng pagdurugo",
            iconResName = "ic_wound",
            imageResName = "img_wound",
            displayOrder = 2
        ),
        FirstAidCategoryEntity(
            id = 3,
            name = "Burns",
            nameTagalog = "Paso",
            description = "First aid for thermal, chemical, and electrical burns",
            descriptionTagalog = "First aid para sa thermal, chemical, at electrical burns",
            iconResName = "ic_burn",
            imageResName = "img_burn",
            displayOrder = 3
        ),
        FirstAidCategoryEntity(
            id = 4,
            name = "Fractures & Sprains",
            nameTagalog = "Bali at Pilay",
            description = "Care for broken bones and joint injuries",
            descriptionTagalog = "Pangangalaga sa mga baling buto at pinsala sa kasukasuan",
            iconResName = "ic_fracture",
            imageResName = "img_fracture",
            displayOrder = 4
        ),
        FirstAidCategoryEntity(
            id = 5,
            name = "Heat & Cold Emergencies",
            nameTagalog = "Heat at Cold Emergency",
            description = "Treatment for heatstroke, hypothermia, and related conditions",
            descriptionTagalog = "Paggamot sa heatstroke, hypothermia, at mga kaugnay na kondisyon",
            iconResName = "ic_temperature",
            imageResName = "img_temperature",
            displayOrder = 5
        ),
        FirstAidCategoryEntity(
            id = 6,
            name = "Drowning",
            nameTagalog = "Pagkalunod",
            description = "Rescue and resuscitation for drowning victims",
            descriptionTagalog = "Pagliligtas at resuscitation para sa mga nalunod",
            iconResName = "ic_drowning",
            imageResName = "img_drowning",
            displayOrder = 6
        )
    )

    fun getContent(): List<FirstAidContentEntity> = listOf(
        // CPR Content
        FirstAidContentEntity(
            id = 1,
            categoryId = 1,
            sectionTitle = "Adult CPR",
            sectionTitleTagalog = "CPR para sa Adulto",
            title = "Check for Response",
            titleTagalog = "Tingnan kung May Tugon",
            content = "Tap the person's shoulder firmly and shout 'Are you okay?' Look for normal breathing. If no response and not breathing normally, call for help and start CPR.",
            contentTagalog = "Tapikin nang malakas ang balikat ng tao at sumigaw ng 'Okay ka lang ba?' Tingnan kung normal ang paghinga. Kung walang tugon at hindi normal ang paghinga, tumawag ng tulong at simulan ang CPR.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        FirstAidContentEntity(
            id = 2,
            categoryId = 1,
            sectionTitle = "Adult CPR",
            sectionTitleTagalog = "CPR para sa Adulto",
            title = "Chest Compressions",
            titleTagalog = "Pagdiin sa Dibdib",
            content = "Place the heel of your hand on the center of the chest. Push hard and fast - at least 2 inches deep, at a rate of 100-120 compressions per minute. Allow full chest recoil between compressions.",
            contentTagalog = "Ilagay ang sakong ng iyong kamay sa gitna ng dibdib. Diinan ng malakas at mabilis - hindi bababa sa 2 pulgada ang lalim, sa bilis na 100-120 compressions bawat minuto. Hayaang bumalik ang dibdib sa normal pagkatapos ng bawat compression.",
            mediaType = MediaType.TEXT,
            displayOrder = 2
        ),
        FirstAidContentEntity(
            id = 3,
            categoryId = 1,
            sectionTitle = "Choking - Adult",
            sectionTitleTagalog = "Pagkabulunan - Adulto",
            title = "Heimlich Maneuver",
            titleTagalog = "Heimlich Maneuver",
            content = "Stand behind the choking person. Place your fist above their navel. Grasp your fist with your other hand. Give quick, upward thrusts until the object is expelled.",
            contentTagalog = "Tumayo sa likod ng nabubulunang tao. Ilagay ang iyong kamao sa itaas ng kanilang pusod. Hawakan ang iyong kamao gamit ang iyong kabilang kamay. Magbigay ng mabilis na pagtulak pataas hanggang mailabas ang bagay.",
            mediaType = MediaType.TEXT,
            displayOrder = 3
        ),
        // Wounds Content
        FirstAidContentEntity(
            id = 4,
            categoryId = 2,
            sectionTitle = "Bleeding Control",
            sectionTitleTagalog = "Pagkontrol ng Pagdurugo",
            title = "Apply Direct Pressure",
            titleTagalog = "Maglagay ng Direktang Presyon",
            content = "Use a clean cloth or bandage to apply firm, direct pressure to the wound. Maintain pressure for at least 15 minutes. Do not remove the cloth if blood soaks through - add more layers.",
            contentTagalog = "Gumamit ng malinis na tela o bandage para maglagay ng matatag at direktang presyon sa sugat. Panatilihin ang presyon ng hindi bababa sa 15 minuto. Huwag alisin ang tela kung napuno ng dugo - magdagdag ng mga layer.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        FirstAidContentEntity(
            id = 5,
            categoryId = 2,
            sectionTitle = "Bleeding Control",
            sectionTitleTagalog = "Pagkontrol ng Pagdurugo",
            title = "Elevate the Wound",
            titleTagalog = "Itaas ang Sugat",
            content = "If possible, raise the injured area above the level of the heart. This helps slow bleeding by reducing blood pressure at the wound site.",
            contentTagalog = "Kung maaari, itaas ang nasugatang bahagi sa itaas ng antas ng puso. Nakakatulong ito na pabagalin ang pagdurugo sa pamamagitan ng pagbabawas ng blood pressure sa lugar ng sugat.",
            mediaType = MediaType.TEXT,
            displayOrder = 2
        ),
        // Burns Content
        FirstAidContentEntity(
            id = 6,
            categoryId = 3,
            sectionTitle = "Minor Burns",
            sectionTitleTagalog = "Maliit na Paso",
            title = "Cool the Burn",
            titleTagalog = "Palamigin ang Paso",
            content = "Hold the burned area under cool (not cold) running water for 10-20 minutes. Do not use ice, butter, or toothpaste on burns.",
            contentTagalog = "Ilagay ang napasoang bahagi sa ilalim ng malamig (hindi nagyeyelo) na dumadaloy na tubig ng 10-20 minuto. Huwag gumamit ng yelo, mantika, o toothpaste sa paso.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        FirstAidContentEntity(
            id = 7,
            categoryId = 3,
            sectionTitle = "Minor Burns",
            sectionTitleTagalog = "Maliit na Paso",
            title = "Cover the Burn",
            titleTagalog = "Takpan ang Paso",
            content = "After cooling, cover the burn with a sterile, non-stick bandage. Wrap loosely to avoid pressure on the burned skin.",
            contentTagalog = "Pagkatapos palamigin, takpan ang paso ng sterile na bandage na hindi dumidikit. Ibalot nang maluwag para iwasan ang presyon sa napasoang balat.",
            mediaType = MediaType.TEXT,
            displayOrder = 2
        ),
        // Fractures Content
        FirstAidContentEntity(
            id = 8,
            categoryId = 4,
            sectionTitle = "Suspected Fracture",
            sectionTitleTagalog = "Pinaghihinalaang Bali",
            title = "Immobilize the Injury",
            titleTagalog = "Huwag Igalaw ang Pinsala",
            content = "Keep the injured area still. Do not try to straighten the limb. Support the injury in the position you find it. Use a splint if trained to do so.",
            contentTagalog = "Panatilihing hindi gumagalaw ang nasugatang bahagi. Huwag subukang ituwid ang baling parte. Suportahan ang pinsala sa posisyon kung paano mo ito natagpuan. Gumamit ng splint kung bihasa ka dito.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        FirstAidContentEntity(
            id = 9,
            categoryId = 4,
            sectionTitle = "Sprains",
            sectionTitleTagalog = "Pilay",
            title = "RICE Method",
            titleTagalog = "Paraan ng RICE",
            content = "Rest the injured area. Ice it for 20 minutes every 2-3 hours. Compress with an elastic bandage. Elevate the injury above heart level when possible.",
            contentTagalog = "Ipahinga ang nasugatang bahagi. Lagyan ng yelo ng 20 minuto tuwing 2-3 oras. I-compress gamit ang elastic bandage. Itaas ang pinsala sa itaas ng antas ng puso kung maaari.",
            mediaType = MediaType.TEXT,
            displayOrder = 2
        ),
        // Heat Emergencies Content
        FirstAidContentEntity(
            id = 10,
            categoryId = 5,
            sectionTitle = "Heat Stroke",
            sectionTitleTagalog = "Heat Stroke",
            title = "Cool the Person Rapidly",
            titleTagalog = "Palamigin ang Tao Agad",
            content = "Heat stroke is life-threatening. Call emergency services immediately. Move person to shade. Remove excess clothing. Apply cool water to skin. Fan the person while wetting skin.",
            contentTagalog = "Ang heat stroke ay nakamamatay. Tumawag agad ng emergency services. Ilipat ang tao sa lilim. Alisin ang sobrang damit. Maglagay ng malamig na tubig sa balat. Paypayan ang tao habang binabasa ang balat.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        // Drowning Content
        FirstAidContentEntity(
            id = 11,
            categoryId = 6,
            sectionTitle = "Water Rescue",
            sectionTitleTagalog = "Pagliligtas sa Tubig",
            title = "Reach or Throw, Don't Go",
            titleTagalog = "Abutin o Ihagis, Huwag Lumangoy",
            content = "Try to rescue from shore first. Reach with a pole, branch, or rope. Throw a flotation device. Only enter water as last resort if trained. Call for help immediately.",
            contentTagalog = "Subukang magligtas mula sa dalampasigan muna. Abutin gamit ang poste, sanga, o lubid. Ihagis ang flotation device. Pumasok lang sa tubig bilang huling paraan kung bihasa ka. Tumawag agad ng tulong.",
            mediaType = MediaType.TEXT,
            displayOrder = 1
        ),
        FirstAidContentEntity(
            id = 12,
            categoryId = 6,
            sectionTitle = "After Rescue",
            sectionTitleTagalog = "Pagkatapos Iligtas",
            title = "Check Breathing",
            titleTagalog = "Tingnan ang Paghinga",
            content = "Once out of water, check if the person is breathing. If not breathing, begin CPR immediately. Even if they seem fine, seek medical attention as complications can occur later.",
            contentTagalog = "Kapag nakalabas na sa tubig, tingnan kung humihinga ang tao. Kung hindi humihinga, simulan agad ang CPR. Kahit mukhang okay sila, magpatingin sa doktor dahil maaaring may komplikasyon sa susunod.",
            mediaType = MediaType.TEXT,
            displayOrder = 2
        )
    )
}