package com.resq.app.data.database

import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.model.ContactCategory

/**
 * Provides preloaded emergency contact data for initial database population.
 * Contains Philippine national emergency hotlines and agency contacts.
 */
object PreloadedContactData {

    fun getContacts(): List<EmergencyContactEntity> = listOf(
        // National Emergency Hotlines
        EmergencyContactEntity(
            id = 1,
            name = "National Emergency Hotline",
            phoneNumber = "911",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "24/7 emergency services for police, fire, and medical emergencies",
            displayOrder = 1
        ),
        EmergencyContactEntity(
            id = 2,
            name = "Philippine Red Cross",
            phoneNumber = "143",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Disaster response, blood services, and humanitarian aid",
            displayOrder = 2
        ),
        EmergencyContactEntity(
            id = 3,
            name = "NDRRMC Operations Center",
            phoneNumber = "(02) 8911-5061",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "National Disaster Risk Reduction and Management Council",
            displayOrder = 3
        ),
        EmergencyContactEntity(
            id = 4,
            name = "DOH Health Emergency",
            phoneNumber = "(02) 8651-7800",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Department of Health emergency hotline",
            displayOrder = 4
        ),
        EmergencyContactEntity(
            id = 5,
            name = "PNP Hotline",
            phoneNumber = "117",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Philippine National Police emergency response",
            displayOrder = 5
        ),
        EmergencyContactEntity(
            id = 6,
            name = "Bureau of Fire Protection",
            phoneNumber = "(02) 8426-0219",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Fire emergency and rescue services",
            displayOrder = 6
        ),
        EmergencyContactEntity(
            id = 7,
            name = "PAGASA Weather",
            phoneNumber = "(02) 8927-1541",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Weather forecasts and storm warnings",
            displayOrder = 7
        ),
        EmergencyContactEntity(
            id = 8,
            name = "PHIVOLCS",
            phoneNumber = "(02) 8426-1468",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Earthquake and volcanic activity information",
            displayOrder = 8
        ),
        EmergencyContactEntity(
            id = 9,
            name = "Philippine Coast Guard",
            phoneNumber = "(02) 8527-3877",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Maritime emergencies and sea rescue",
            displayOrder = 9
        ),
        EmergencyContactEntity(
            id = 10,
            name = "DSWD Disaster Response",
            phoneNumber = "(02) 8931-8101",
            category = ContactCategory.NATIONAL,
            isCustom = false,
            description = "Social welfare and disaster relief assistance",
            displayOrder = 10
        ),
        // Local emergency contacts (examples - users can add their own)
        EmergencyContactEntity(
            id = 11,
            name = "Metro Manila Development Authority",
            phoneNumber = "(02) 8882-4151",
            category = ContactCategory.LOCAL,
            isCustom = false,
            description = "MMDA traffic and emergency assistance (Metro Manila)",
            displayOrder = 1
        ),
        EmergencyContactEntity(
            id = 12,
            name = "MMDA Flood Control",
            phoneNumber = "(02) 8882-0925",
            category = ContactCategory.LOCAL,
            isCustom = false,
            description = "Flood monitoring and emergency response (Metro Manila)",
            displayOrder = 2
        )
    )
}