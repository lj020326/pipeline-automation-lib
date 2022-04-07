
// ref: https://issues.jenkins-ci.org/browse/JENKINS-40154
@NonCPS
def call(Date startDate, Date endDate) {
    use(groovy.time.TimeCategory) {
        def duration = endDate - startDate
        return duration.days
    }
}
