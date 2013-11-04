package au.org.ala.ecodata.reporting

/**
 * An Aggregator is responsible for aggregating a list of scores.
 * It is capable of grouping the results by a single value.
 */
class Aggregator {

    static String DEFAULT_GROUP = ""

    Map<String, Aggregators.OutputAggregator> aggregatorsByGroup
    Score score
    Closure groupingFunction
    String title
    String outputListName

    public Aggregator(String title, groupingFunction, Score score, AggregatorBuilder builder) {

        this.groupingFunction = groupingFunction
        this.score = score
        this.title = title

        aggregatorsByGroup = [:].withDefault { key ->
            builder.createAggregator(score, key)
        }
    }

    def aggregate(activity) {

        activity.outputs?.each { output ->
            if (outputListName) {
                output.data[outputListName].each{
                    Aggregators.OutputAggregator aggregator = aggregatorFor(activity, it)
                    aggregator.aggregate(output)
                }
            }
            else {
                Aggregators.OutputAggregator aggregator = aggregatorFor(activity, output)
                aggregator.aggregate(output)
            }
        }

    }

    /**
     *  Returns the results of the aggregation.
     *  The results will be formatted like:
     *     {
     *        //TODO document me
     *     }
     */
    def results() {
        def results = aggregatorsByGroup.values().collect { it.result() }.findAll{ it.count > 0 }

        return [groupTitle:title, score:score, results:results]
    }

    /**
     * Classifies the supplied output according to the groupingFunction and returns the
     * Aggregator(s) that are aggregrating results for that group.
     * @param activity the activity to be aggregated - this is optionally used to perform the grouping.
     * @param output the output containing the scores to be aggregated. The output itself may also be used by the
     * grouping function.
     */
    Aggregators.OutputAggregator aggregatorFor(activity, output) {

        // TODO the grouping function should probably specify the default group.
        String group = groupingFunction(activity, output)
        if (!group) {
            group = DEFAULT_GROUP
        }

        return aggregatorsByGroup[group]
    }


}