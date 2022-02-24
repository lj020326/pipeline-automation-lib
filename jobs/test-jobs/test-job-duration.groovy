#!/usr/bin/env groovy

@Library("pipeline-automation-lib@develop")

import com.dettonville.api.pipeline.utils.logging.LogLevel
import com.dettonville.api.pipeline.utils.logging.Logger

import com.dettonville.api.pipeline.utils.Utilities

//import groovy.time.Duration
import java.time.*

import java.util.concurrent.TimeUnit

import groovy.time.TimeCategory
import groovy.time.TimeDuration
//import java.time.LocalDate
//import java.time.LocalDateTime

//import org.joda.time.Seconds

//Logger.init(this, LogLevel.INFO)
Logger.init(this, LogLevel.DEBUG)
Logger log = new Logger(this)

Integer sleepSeconds = 5

log.info("getting duration")

//String duration = getDuration(log)
//String duration = getDuration2(log)

// WORKS!!!
String duration = getDuration2_1(log)

//String duration = getDuration3(log)

//String duration = getDuration4(log)

//String duration = getDuration5(log)

//String duration = getDuration6(log)

//String duration = getDuration7(log)

//String duration = getDuration8(log)

log.info("duration=${duration}")

// ref: https://www.baeldung.com/java-date-difference
// https://www.mkyong.com/java8/java-8-period-and-duration-examples/
// https://stackoverflow.com/questions/24491243/why-cant-i-get-a-duration-in-minutes-or-hours-in-java-time
// https://stackoverflow.com/questions/1555262/calculating-the-difference-between-two-java-date-instances
// https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html
// https://dzone.com/articles/groovy-additions-to-the-java-8-datetime-api
// https://www.java67.com/2016/03/how-to-convert-date-to-localdatetime-in-java8-example.html
// https://stackoverflow.com/questions/36294446/groovys-timecategory-with-localdate-and-localdatetime
//
@NonCPS
static String getDuration1(Logger log) {

    ZoneId zoneId = ZoneId.of ( "America/Montreal" );
    ZonedDateTime now = ZonedDateTime.now ( zoneId );
    ZonedDateTime future = now.plusMinutes ( 63 );
    Duration duration = Duration.between ( now , future );
    log.info( "now: " + now + " to future: " + now + " = " + duration );

    return duration.toString()
}

String getDuration2(Logger log) {

    Integer sleepSeconds = 5
    long t1 = System.nanoTime()

    log.info("sleep for ${sleepSeconds} seconds")
    sleep sleepSeconds

    long t2 = System.nanoTime()

    long elapsedTimeInSeconds = (t2 - t1) / 1000000000;

    log.info("elapsedTimeInSeconds=${elapsedTimeInSeconds}")
    return String.format("%d", elapsedTimeInSeconds)
}

String getDuration2_1(Logger log) {

    Integer sleepSeconds = 5
    long t1 = System.currentTimeMillis()

    log.info("sleep for ${sleepSeconds} seconds")
    sleep sleepSeconds

    long t2 = System.currentTimeMillis()

//    long elapsedTimeInSeconds = (t2 - t1) / 1000;
    long milliseconds = t2 - t1
    long elapsedTimeInSeconds = milliseconds / 1000;

    log.info("milliseconds=${milliseconds}")
    log.info("elapsedTimeInSeconds=${elapsedTimeInSeconds}")

    String durationInSeconds = String.format("%d seconds", elapsedTimeInSeconds)
    log.info("durationInSeconds=${durationInSeconds}")

//    String duration = getDurationString(milliseconds)
    String duration = Utilities.getDurationString(milliseconds)

    log.info("duration=${duration}")
    return duration
}

@NonCPS
static String getDurationString(long milliseconds) {
//    return String.format("%d min, %d sec",
//            TimeUnit.millisecondsECONDS.toMinutes(milliseconds),
//            TimeUnit.millisecondsECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.millisecondsECONDS.toMinutes(milliseconds)))
//    return String.format("%d min, %d sec",
//            TimeUnit.toMinutes(milliseconds),
//            TimeUnit.toSeconds(milliseconds) - TimeUnit.toSeconds(TimeUnit.toMinutes(milliseconds)))

    long seconds = milliseconds / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long remainingSeconds = seconds % 60;
    long remainingMinutes = minutes % 60;
    String duration = "${remainingSeconds} seconds."
    if (minutes > 0) {
        duration = "${minutes} minutes and ${remainingSeconds} seconds."
    }
    if (hours > 0) {
        duration = "${hours} hours and ${remainingMinutes} minutes and ${remainingSeconds} seconds."
    }
    return duration
//    return String.format("%d minutes and %d seconds.", minutes, remainingSeconds);
}

String getDuration3(Logger log) {

    int sleepSeconds = 5
    LocalDateTime startDate = LocalDateTime.now()

    sleep sleepSeconds

    LocalDateTime endDate = LocalDateTime.now()

    Duration duration = Duration.between(startDate, endDate)
    log.info("duration=${duration}")
    return String.format("%d", duration.getSeconds());
}


// ref: https://stackoverflow.com/questions/1555262/calculating-the-difference-between-two-java-date-instances
String getDuration4(Logger log) {
    Date timeStart = new Date()
    Integer sleepSeconds = 5

    sleep sleepSeconds
    Date timeEnd = new Date()

    long diffInMillies = timeStart.getTime() - timeEnd.getTime();
    log.info("diffInMillies=${diffInMillies}")

    return String.format("%d", TimeUnit.MILLISECONDS.toSeconds(diffInMillies));
}

@NonCPS
String getDuration5(Logger log) {
    Date timeStart = new Date()
    Integer sleepSeconds = 5

    sleep sleepSeconds
    Date timeEnd = new Date()

    def duration = timeEnd - timeStart
    log.info("duration=${duration}")
    return String.format("%d", duration.seconds)
}

@NonCPS
String getDuration6(Logger log) {

    Date timeStart = new Date()
    Integer sleepSeconds = 5

    sleep sleepSeconds
    Date timeEnd = new Date()

    def duration = TimeCategory.minus(timeEnd, timeStart)
    log.info("duration=${duration}")
    return duration.toString()
}

String getDuration7(Logger log) {

    Date timeStart = new Date()
    Integer sleepSeconds = 5

    sleep sleepSeconds
    Date timeEnd = new Date()

    def duration = computeDiff(timeStart, timeEnd)
    log.info("duration=${duration}")
    return duration.toString()
}

// ref: https://stackoverflow.com/questions/1555262/calculating-the-difference-between-two-java-date-instances
@NonCPS
Map<TimeUnit,Long> computeDiff(Date date1, Date date2) {

    long diffInMillies = date2.getTime() - date1.getTime();

    //create the list
    List<TimeUnit> units = new ArrayList<TimeUnit>(EnumSet.allOf(TimeUnit.class));
    Collections.reverse(units);

    //create the result map of TimeUnit and difference
    Map<TimeUnit,Long> result = new LinkedHashMap<TimeUnit,Long>();
    long milliesRest = diffInMillies;

    for ( TimeUnit unit : units ) {

        //calculate difference in millisecond
        long diff = unit.convert(milliesRest,TimeUnit.MILLISECONDS);
        long diffInMilliesForUnit = unit.toMillis(diff);
        milliesRest = milliesRest - diffInMilliesForUnit;

        //put the result in the map
        result.put(unit,diff);
    }

    return result;
}

@NonCPS
String getDuration8(Logger log) {

    Integer sleepSeconds = 5
    long t1 = System.currentTimeMillis()

    log.info("sleep for ${sleepSeconds} seconds")
    sleep sleepSeconds

    long t2 = System.currentTimeMillis()

    long diffInMillies = t2 - t1

    log.info("diffInMillies=${diffInMillies}")

//    log.info("EnumSet.allOf(TimeUnit.class)=${EnumSet.allOf(TimeUnit.class)}")
    log.info("Arrays.asList(TimeUnit.class)=${Arrays.asList(TimeUnit.class.values())}")

    //create the list
//    List<TimeUnit> units = new ArrayList<TimeUnit>(EnumSet.allOf(TimeUnit.class));
//    List units = EnumSet.allOf(TimeUnit.class)
    List units = Arrays.asList(TimeUnit.class.values())
    log.info("units=${units}")

    Collections.reverse(units);

    //create the result map of TimeUnit and difference
    Map<TimeUnit,Long> result = new LinkedHashMap<TimeUnit,Long>();
    long milliesRest = diffInMillies;

    for ( TimeUnit unit : units ) {

        //calculate difference in millisecond
        long diff = unit.convert(milliesRest,TimeUnit.MILLISECONDS);
        long diffInMilliesForUnit = unit.toMillis(diff);
        milliesRest = milliesRest - diffInMilliesForUnit;

        log.info("unit=${unit} diff=${diff}")
        //put the result in the map
        result.put(unit,diff);
    }

    log.info("result=${result}")
    return result.toString()
}
