package au.org.ala.ecodata.converter

import au.org.ala.ecodata.Activity
import au.org.ala.ecodata.Output
import groovy.util.logging.Log4j
import org.apache.commons.lang.StringUtils

@Log4j
class RecordConverter {

    static List<Map> convertRecords(Activity activity, Output output, Map data, Map outputMetadata) {
        // Outputs are made up of multiple 'dataModels', where each dataModel could map to one or more Record fields
        // and/or one or more Records. For example, a dataModel with a type of 'list' will map to one Record per item in
        // the list. Further, each item in the list is a 'dataModel' on it's own, which will map to one or more fields.

        // First create the skeleton of the Record. This contains the various object identifiers for related data
        Map baseRecord = [
                outputId: output.outputId,
                projectId: activity.projectId,
                projectActivityId: activity.projectActivityId,
                activityId: activity.activityId,
                userId: activity.userId
        ]

        // Populate the skeleton with Record attributes which are derived from the Activity. These attributes are shared
        // by all Records that are generated from this Output.
        baseRecord << extractActivityDetails(activity)

        // Split the dataModels in the output into those which produce Record FIELDS, and those which produce multiple
        // Records. When there is a mix of both, the fields generated by the 'singleItemModels' will be shared by all
        // Records that are generated from this Output.
        List singleItemModels
        List multiItemModels
        (singleItemModels, multiItemModels) = outputMetadata?.dataModel?.split {
            it.dataType != "list" && it.dataType != "masterDetail"
        }

        // For each singleItemModel, get the appropriate field converter for the data type, generate the individual
        // Record fields and add them to the skeleton Record
        singleItemModels?.each { Map dataModel ->
            RecordFieldConverter converter = getFieldConverter(dataModel.dataType)
            List<Map> recordFieldSets = converter.convert(data, dataModel)
            baseRecord << recordFieldSets[0]
        }

        List<Map> records = []
        if (multiItemModels) {

            // For each multiItemModel, get the appropriate field converter for the data type and generate the list of field
            // sets which will be converted into Records. For each field set, add a copy of the skeleton Record so it has
            // all the common fields
            multiItemModels?.each { Map dataModel ->
                RecordFieldConverter converter = getFieldConverter(dataModel.dataType)
                List<Map> recordFieldSets = converter.convert(data, dataModel)

                recordFieldSets.each {
                    it << baseRecord
                }

                records.addAll(recordFieldSets)
            }
        } else {
            // If there are no multiItemModels, then the 'skeleton' record has all that we need, so return it.
            records << baseRecord
        }

        // We are now left with a list of one or more Maps, where each Map contains all the fields for an individual Record.
        records
    }

    protected static RecordFieldConverter getFieldConverter(String outputDataType) {
        String packageName = RecordFieldConverter.class.package.getName()
        String className = "${StringUtils.capitalize(outputDataType).replaceAll("[ _\\-]", "")}Converter"

        RecordFieldConverter converter
        try {
            converter = Class.forName("${packageName}.${className}")?.newInstance()
        } catch (ClassNotFoundException e) {
            log.warn "No specific converter found for output data type ${outputDataType} with class name ${packageName}.${className}, using generic converter"
            converter = new GenericFieldConverter()
        }

        converter
    }


    private static Map extractActivityDetails(Activity activity) {
        Map dwcFields = [:]
        dwcFields.userId = activity.userId
        dwcFields.recordedBy = activity.userId

        dwcFields
    }
}
