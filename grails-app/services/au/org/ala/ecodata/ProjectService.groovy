package au.org.ala.ecodata

import au.org.ala.ecodata.reporting.Score

class ProjectService {

    static transactional = false
    static final ACTIVE = "active"
	static final COMPLETED = "completed"
    static final DELETED = "deleted"
    static final BRIEF = 'brief'
    static final FLAT = 'flat'
    static final ALL = 'all'
	static final PROMO = 'promo'
    static final OUTPUT_SUMMARY = 'outputs'
    static final ENHANCED = 'enhanced'

    def grailsApplication
    def siteService
    def documentService
    def metadataService
    def reportService
    def activityService
    def permissionService

    private def mapAttributesToCollectory(props) {
        def mapKeyProjectDataToCollectory = [
                description: 'pubDescription',
                manager: 'email',
                name: 'name',
                dataSharingLicense: '', // ignore this property (mapped to dataResource)
                organisation: '', // ignore this property
                projectId: 'uid',
                urlWeb: 'websiteUrl'
        ]
        def collectoryProps = [
                api_key: grailsApplication.config.api_key
        ]
        def hiddenJSON = [:]
        props.each { k, v ->
            if (v != null) {
                def keyCollectory = mapKeyProjectDataToCollectory[k]
                if (keyCollectory == null) // not mapped to first class collectory property
                    hiddenJSON[k] = v
                else if (keyCollectory != '') // not to be ignored
                    collectoryProps[keyCollectory] = v
            }
        }
        collectoryProps.hiddenJSON = hiddenJSON
        collectoryProps
    }

    def getCommonService() {
        grailsApplication.mainContext.commonService
    }

    def getBrief(listOfIds) {
        Project.findAllByProjectIdInListAndStatusNotEqual(listOfIds, DELETED).collect {
            [projectId: it.projectId, name: it.name]
        }
    }

    def get(String id, levelOfDetail = []) {
        def p = Project.findByProjectId(id)

        return p?toMap(p, levelOfDetail):null
    }

    def list(levelOfDetail = [], includeDeleted = false, citizenScienceOnly = false) {
        def list
        if (!citizenScienceOnly)
            list = includeDeleted ? Project.list(): Project.findAllByStatus(ACTIVE)
        else if (includeDeleted)
            list = Project.findAllByIsCitizenScience(true)
        else
            list = Project.findAllByIsCitizenScienceAndStatus(true, ACTIVE)
        list?.collect { toMap(it, levelOfDetail) }
    }
	
	def promoted(){
		def list = Project.findAllByPromoteOnHomepage("yes")
		list.collect { toMap(it, PROMO) }
	}
	
    /**
     * Converts the domain object into a map of properties, including
     * dynamic properties.
     * @param prj a Project instance
     * @return map of properties
     */
    def toMap(prj, levelOfDetail = [], includeDeletedActivites = false) {

        def dbo = prj.getProperty("dbo")
        def mapOfProperties = dbo.toMap()
        if (levelOfDetail == BRIEF) {
            return [projectId: prj.projectId, name: prj.name, grantId:prj.grantId, externalId:prj.externalId, funding:prj.funding, description:prj.description, status:prj.status, plannedStartDate:prj.plannedStartDate, plannedEndDate:prj.plannedEndDate, associatedProgram:prj.associatedProgram, associatedSubProgram:prj.associatedSubProgram]
        }
		if (levelOfDetail == PROMO) {
			return [projectId: prj.projectId, name: prj.name, organisationName: prj.organisationName, description: prj.description?.take(200), 
					documents:documentService.findAllForProjectIdAndIsPrimaryProjectImage(prj.projectId, ALL)]
		}
        def id = mapOfProperties["_id"].toString()
        mapOfProperties["id"] = id
		mapOfProperties["status"] = mapOfProperties["status"]?.capitalize();
        mapOfProperties.remove("_id")
        if (levelOfDetail != FLAT) {

            mapOfProperties.remove("sites")
            mapOfProperties.sites = siteService.findAllForProjectId(prj.projectId, [SiteService.FLAT])
            mapOfProperties.documents = documentService.findAllForProjectId(prj.projectId, levelOfDetail)

            if (levelOfDetail == ALL) {
                mapOfProperties.activities = activityService.findAllForProjectId(prj.projectId, levelOfDetail, includeDeletedActivites)
            }
            else if (levelOfDetail == OUTPUT_SUMMARY) {
                mapOfProperties.outputSummary = projectMetrics(prj.projectId, false, true)
            }
            if (levelOfDetail == ENHANCED) {
                def activities = activityService.findAllForProjectId(prj.projectId, ActivityService.FLAT, includeDeletedActivites)
                prj.activities = activities

                mapOfProperties.actualStartDate = prj.actualStartDate?:''
                mapOfProperties.actualEndDate = prj.actualEndDate?:''
                mapOfProperties.plannedDurationInWeeks = prj.plannedDurationInWeeks
                mapOfProperties.actualDurationInWeeks = prj.actualDurationInWeeks
                mapOfProperties.contractDurationInWeeks = prj.contractDurationInWeeks
            }
        }
	
        mapOfProperties.findAll {k,v -> v != null}
    }

    /**
     * Converts the domain object into a highly detailed map of properties, including
     * dynamic properties, and linked components.
     * @param prj a Project instance
     * @return map of properties
     */
    def toRichMap(prj) {
        def dbo = prj.getProperty("dbo")
        def mapOfProperties = dbo.toMap()
        def id = mapOfProperties["_id"].toString()
        mapOfProperties["id"] = id
		mapOfProperties["status"] = mapOfProperties["status"]?.capitalize();
        mapOfProperties.remove("_id")
        mapOfProperties.remove("sites")
        mapOfProperties.sites = siteService.findAllForProjectId(prj.projectId, true)
        // remove nulls
        mapOfProperties.findAll {k,v -> v != null}
    }

    def loadAll(list) {
        list.each {
            create(it)
        }
    }

    def create(props) {
        assert getCommonService()
        def o
        try {
            if (props.projectId && Project.findByProjectId(props.projectId)) {
                // clear session to avoid exception when GORM tries to autoflush the changes
                Project.withSession { session -> session.clear() }
                return [status:'error',error:'Duplicate project id for create ' + props.projectId]
            }
            // name is a mandatory property and hence needs to be set before dynamic properties are used (as they trigger validations)
            o = new Project(projectId: props.projectId?: Identifiers.getNew(true,''), name:props.name)
            o.save(failOnError: true)

            props.remove('sites')
            props.remove('id')
            getCommonService().updateProperties(o, props)
        } catch (Exception e) {
            // clear session to avoid exception when GORM tries to autoflush the changes
            Project.withSession { session -> session.clear() }
            def error = "Error creating project - ${e.message}"
            log.error error
            return [status:'error',error:error]
        }

        // create a dataProvider in collectory to hold project meta data
        try {
            def collectoryProps = mapAttributesToCollectory(props)
            def result = webService.doPost(grailsApplication.config.collectory.baseURL + 'ws/dataProvider/', collectoryProps)
            def dataProviderId = webService.extractCollectoryIdFromResult(result)
            if (dataProviderId) {
                // create a dataResource in collectory to hold project outputs
                props.dataProviderId = dataProviderId
                collectoryProps.remove('hiddenJSON')
                collectoryProps.dataProvider = [uid: dataProviderId]
                if (props.collectoryInstitutionId) collectoryProps.institution = [uid: props.collectoryInstitutionId]
                collectoryProps.licenseType = props.dataSharingLicense
                result = webService.doPost(grailsApplication.config.collectory.baseURL + 'ws/dataResource/', collectoryProps)
                props.dataResourceId = webService.extractCollectoryIdFromResult(result)
            }
        } catch (Exception e) {
            def error = "Error creating collectory info for project ${o.projectId} - ${e.message}"
            log.error error
        }

        return [status:'ok',projectId:o.projectId]
    }

    def update(props, id) {
        def a = Project.findByProjectId(id)
        if (a) {
            try {
                getCommonService().updateProperties(a, props)
            } catch (Exception e) {
                Project.withSession { session -> session.clear() }
                def error = "Error updating project ${id} - ${e.message}"
                log.error error
                return [status: 'error', error: error]
            }
            if (a.dataProviderId) { // recreate 'hiddenJSON' in collectory every time (minus some attributes)
                try {
                    a = Project.findByProjectId(id)
                    ['id', 'dateCreated', 'documents', 'lastUpdated', 'organisationName', 'projectId', 'sites'].each {
                        a.remove(it)
                    }
                    webService.doPost(grailsApplication.config.collectory.baseURL + 'ws/dataProvider/' + a.dataProviderId, mapAttributesToCollectory(a))
                    if (a.dataResourceId)
                        webService.doPost(grailsApplication.config.collectory.baseURL + 'ws/dataResource/' + a.dataResourceId, [licenseType: a.dataSharingLicense])
                } catch (Exception e ) {
                    def error = "Error updating collectory info for project ${id} - ${e.message}"
                    log.error error
                }
            }
            return [status: 'ok']
        } else {
            def error = "Error updating project - no such id ${id}"
            log.error error
            return [status:'error',error:error]
        }
    }

    /**
     * Deletes a project and any associated activities, outputs and user permissions.  The
     * project is removed from any sites it it associated with.  Orphaned sites are not
     * deleted.
     * @param id the id of the project to delete.
     * @param destroy if false, all deletes will be status updates (a soft delete).  Note that
     * the permissions will be deleted and site associations removed, even in the soft delete case.
     */
    def delete(String id, destroy) {

        def p = Project.findByProjectId(id)
        if (p) {

            // Delete activities associated with the project.
            def activityIds = getActivityIdsForProject(id)
            activityIds.each {activityService.delete(it, destroy)}

            // Delete any user associations or permissions associated with the project
            permissionService.deleteAllForProject(id)

            // Remove any site associations - maybe orphaned sites should be deleted too?
            siteService.deleteSitesFromProject(id)

            if (destroy) {
                p.delete()
                webService.doDelete(grailsApplication.config.collectory.baseURL + 'ws/dataProvider/' + id)
            } else {
                p.status = 'deleted'
                p.save(flush: true)
            }
            return [status: 'ok']
        } else {
            return [status: 'error', error: 'No such id']
        }
    }


    /**
     * Returns the reportable metrics for a project as determined by the project output targets and activities
     * that have been undertaken.
     * @param id identifies the project.
     * @return a Map containing the aggregated results.  TODO document me better, but it is likely this structure will change.
     *
     */
    def projectMetrics(String id, targetsOnly = false, approvedOnly = false) {
        def p = Project.findByProjectId(id)
        if (p) {
            def project = toMap(p, ProjectService.FLAT)

            def toAggregate = []

            metadataService.activitiesModel().outputs?.each{
                Score.outputScores(it).each { score ->
                    if (!targetsOnly || score.isOutputTarget) {
                        toAggregate << [score: score]
                    }
                }
            }
			
            def outputSummary = reportService.projectSummary(id, toAggregate, approvedOnly)
			

            // Add project output target information where it exists.

            project.outputTargets?.each { target ->

                def score = outputSummary.find{it.score.isOutputTarget && it.score.outputName == target.outputLabel && it.score.label == target.scoreLabel}
                if (score) {
                    score['target'] = target.target
                } else {
               		   // If there are no Outputs recorded containing the score, the results won't be returned, so add
               			// one in containing the target.
                    score = toAggregate.find{it.score?.outputName == target.outputLabel && it.score?.label == target.scoreLabel}
                    if (score) {
                        outputSummary << [score:score.score, target:target.target]
                    } else {
                        // This can happen if the meta-model is changed after targets have already been defined for a project.
                        // Once the project output targets are re-edited and saved, the old targets will be deleted.
                        log.warn "Can't find a score for existing output target: $target.outputLabel $target.scoreLabel, projectId: $project.projectId"
                    }
                }
            }
            return outputSummary
        } else {
            def error = "Error retrieving metrics for project - no such id ${id}"
            log.error error
            return [status:'error',error:error]
        }
    }

    public List<String> getActivityIdsForProject(String projectId) {
        def c = Activity.createCriteria()
        def list = c {
            eq("projectId", projectId)
            projections {
                property("activityId")
            }
        }
        List<String> results = new ArrayList<String>()
        list.each {
            results << it.toString()
        }
        return results
    }

    /**
     * @param criteria a Map of property name / value pairs.  Values may be primitive types or arrays.
     * Multiple properties will be ANDed together when producing results.
     *
     * @return a list of the projects that match the supplied criteria
     */
    public search(Map searchCriteria, levelOfDetail = []) {

        def criteria = Project.createCriteria()
        def projects = criteria.list {
            ne("status", "deleted")
            searchCriteria.each { prop,value ->

                if (value instanceof List) {
                    inList(prop, value)
                }
                else {
                    eq(prop, value)
                }
            }

        }
        projects.collect{toMap(it, levelOfDetail)}
    }

    def updateOrgName(orgId, orgName) {
        Project.collection.update(
            [organisationId: orgId],
            ['$set': [organisationName: orgName]], false, true)
    }

}
