package au.org.ala.ecodata

import org.bson.types.ObjectId

/**
 * Represents an organisation that manages projects in fieldcapture.
 * Allows some branding as well as grouping / ownership of projects.
 */
class Organisation {


    ObjectId id
    String organisationId

    String name
    String description
    String announcements // or news and events?  (Announcables...)

    String status = 'active'

    String collectoryInstitutionId // Reference to the Collectory

    Date dateCreated
    Date lastUpdated


    static mapping = {
        organisationId index: true
        version false
    }

    static constraints = {
        announcements nullable: true
        description nullable: true
        collectoryInstitutionId nullable: true
    }
}
