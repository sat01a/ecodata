package au.org.ala.ecodata

import grails.converters.JSON
import grails.test.spock.IntegrationSpec

class ProjectControllerSpec extends IntegrationSpec {

    def projectController = new ProjectController()

    def setup() {

    }

    def cleanup() {
    }

    void "test create project"() {

        setup:
        def project = [name: 'Test Project', description: 'Test description', dynamicProperty: 'dynamicProperty']
        projectController.request.contentType = 'application/json;charset=UTF-8'
        projectController.request.content = (project as JSON).toString().getBytes('UTF-8')
        projectController.request.method = 'POST'

        when: "creating a project"
        def resp = projectController.update('') // Empty or null ID triggers a create

        then: "ensure we get a response including a projectId"

        def projectId = resp.projectId
        projectController.response.contentType == 'application/json;charset=UTF-8'
        resp.message == 'created'
        projectId != null


        when: "retrieving the new project"
        projectController.response.reset()
        def savedProject = projectController.get(projectId) // To support JSONP the controller returns a model object, which is transformed to JSON via a filter.

        then: "ensure the properties are the same as the original"
        savedProject.projectId == projectId
        savedProject.name == project.name
        savedProject.description == project.description
        savedProject.dynamicProperty == project.dynamicProperty

    }

}
