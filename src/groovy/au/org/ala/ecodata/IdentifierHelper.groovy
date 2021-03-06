package au.org.ala.ecodata

class IdentifierHelper {

    public static String getEntityIdentifier(Object domainObject) {
        String entityId
        switch (domainObject.class.name) {
            case Project.class.name:
                entityId = domainObject.projectId
                break
            case Site.class.name:
                entityId = domainObject.siteId
                break
            case Activity.class.name:
                entityId = domainObject.activityId
                break
            case Output.class.name:
                entityId = domainObject.outputId
                break
            case Document.class.name:
                entityId = domainObject.documentId
                break
            default:
                // Last chance to find a 'real' entity id, rather than the internal mongo id.
                // try a synthesized id member user the <class name>Id pattern
                entityId = getIdPropertyValue(domainObject)
                if (!entityId) {
                    entityId = domainObject.id
                }
                break
        }
        return entityId
    }

    private static String getIdPropertyValue(Object object) {
        if (object) {
            def name = object.class.simpleName
            def idMemberName = name[0].toLowerCase() + name.substring(1) + "Id"
            if (object.hasProperty(idMemberName)) {
                return object[idMemberName]
            }
        }
        return null
    }

}
