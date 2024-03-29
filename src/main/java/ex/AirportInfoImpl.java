package ex;

import ex.deserialization.objects.Flight;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import scala.Tuple2;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.explode;
import static org.apache.spark.sql.functions.desc;

import org.apache.spark.SparkConf;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AirportInfoImpl implements AirportInfo {

    private SparkSession sparkSession;

    /**
     * Usage of some example operations.
     *
     * @param flights dataframe of flights
     */
    public void sparkExample(Dataset<Row> flights) {
        System.out.println("Example printSchema");
        flights.printSchema();

        System.out.println("Example select and filter");
        // operations on a dataframe or rdd do not modify it, but return a new one
        Dataset<Row> selectAirlineDisplayCode = flights.select("flight.operatingAirline.iataCode", "flight.aircraftType.icaoCode").filter(r -> !r.anyNull());
        selectAirlineDisplayCode.show(false);

        System.out.println("Example groupBy and count");
        Dataset<Row> countOfAirlines = selectAirlineDisplayCode.groupBy("iataCode").count();
        countOfAirlines.show(false);

        System.out.println("Example where");
        Dataset<Row> selectOnlyLufthansa = selectAirlineDisplayCode.where("iataCode = 'LH'");
        selectOnlyLufthansa.show(false);

        System.out.println("Example map to String");
        Dataset<String> onlyAircraftIcaoCodeAsString = selectOnlyLufthansa.map(r -> r.getString(1), Encoders.STRING());
        onlyAircraftIcaoCodeAsString.show(false);

        System.out.println("Example mapToPair and reduceByKey");
        JavaRDD<Row> rdd = selectOnlyLufthansa.toJavaRDD();
        JavaPairRDD<String, Long> paired = rdd.mapToPair(r -> Tuple2.apply(r.getString(1), 1L));
        JavaPairRDD<String, Long> reducedByKey = paired.reduceByKey((a, b) -> a + b);
        reducedByKey.take(20).forEach(t -> System.out.println(t._1() + " " + t._2()));
    }

    /**
     * Task 1
     * Return a dataframe, in which every row contains the destination airport and its count over all available data
     * of departing flights, sorted by count in descending order.
     * The column names are (in this order):
     * arrivalAirport | count
     * Remove entries that do not contain an arrivalAirport member (null or empty).
     * Use the given Dataframe.
     *
     * @param departingFlights Dataframe containing the rows of departing flights
     * @return a dataframe containing statistics of the most common destinations
     */
    @Override
    public Dataset<Row> mostCommonDestinations(Dataset<Row> departingFlights) {
        // TODO: Implement
        SparkConf conf = new SparkConf().setAppName("SparkExercise").setMaster("local");
        sparkSession = SparkSession.builder().config(conf).getOrCreate();
        Dataset<Row> nonEmptyArrivalAirports = departingFlights.select("flight.arrivalAirport").filter(r -> !r.anyNull());
        JavaRDD<Row> rdd = nonEmptyArrivalAirports.toJavaRDD();
        JavaPairRDD<String, Long> paired = rdd.mapToPair(r -> Tuple2.apply(r.getString(0), 1L));
        JavaPairRDD<String, Long> reducedByKey = paired.reduceByKey((a, b) -> a + b);
        Dataset<Row> res = sparkSession.createDataset(reducedByKey.collect(), Encoders.tuple(Encoders.STRING(),Encoders.LONG()))
            .toDF("arrivalAirport", "count")
            .sort(desc("count"));
        res.show(5);  // check dataset looks in console
        return res;

        // not sure if we need to create an spark entry point in here, but the createDataset function which
        // converts JavaPairRDD to Dataset is under SparkSession class. 
    }

    /**
     * Task 2
     * Return a dataframe, in which every row contains a gate and the amount at which that gate was used for flights to
     * Berlin ("count"), sorted by count in descending order. Do not include gates with 0 flights to Berlin.
     * The column names are (in this order):
     * gate | count
     * Remove entries that do not contain a gate member (null or empty).
     * Use the given Dataframe.
     *
     * @param departureFlights Dataframe containing the rows of departing flights
     * @return dataframe with statistics about flights to Berlin per gate
     */
    @Override
    public Dataset<Row> gatesWithFlightsToBerlin(Dataset<Row> departureFlights) {
        // TODO: Implement
        SparkConf conf = new SparkConf().setAppName("SparkExercise").setMaster("local");
        sparkSession = SparkSession.builder().config(conf).getOrCreate();
        Dataset<Row> nonEmptyGatesToBerlin = departureFlights
            .where("flight.arrivalAirport = 'SXF' || 'BER'")  // former and current berlin airport
            .select("flight.departure.gates")
            .filter(r -> !r.anyNull());
        JavaRDD<Row> rdd = nonEmptyGatesToBerlin.toJavaRDD();
        JavaPairRDD<String, Long> paired = rdd.mapToPair(r -> Tuple2.apply(r.getString(0), 1L));
        JavaPairRDD<String, Long> reducedByKey = paired.reduceByKey((a, b) -> a + b);
        Dataset<Row> res = sparkSession
            .createDataset(reducedByKey.collect(), Encoders.tuple(Encoders.STRING(),Encoders.LONG()))
            .toDF("gate", "count")
            .sort(desc("count"));
        res.show(5, false);  // check dataset looks in console
        return res;
    }

    /**
     * Task 3
     * Return a JavaPairRDD with String keys and Long values, containing count of flights per aircraft on the given
     * originDate. The String keys are the modelNames of each aircraft and their Long value is the amount of flights for
     * that modelName at the given originDate. Do not include aircrafts with 0 flights.
     * Remove entries that do not contain a modelName member (null or empty).
     * The date string is of the form 'YYYY-MM-DD'.
     * Use the given dataframe.
     *
     * @param flights    Dataframe containing the rows of flights
     * @param originDate the date to find the most used aircraft for
     * @return tuple containing the modelName of the aircraft and its total number
     */
    @Override
    public JavaPairRDD<String, Long> aircraftCountOnDate(Dataset<Row> flights, String originDate) {
        // TODO: Implement
        SparkConf conf = new SparkConf().setAppName("SparkExercise").setMaster("local");
        sparkSession = SparkSession.builder().config(conf).getOrCreate();
        Dataset<Row> nonEmptyAircraftsOnDay = flights
            .where(col("flight.originDate").equalTo(originDate))
            .select("flight.aircraftType.modelName")
            .filter(r -> !r.anyNull());
        JavaRDD<Row> rdd = nonEmptyAircraftsOnDay.toJavaRDD();
        JavaPairRDD<String, Long> paired = rdd.mapToPair(r -> Tuple2.apply(r.getString(0), 1L));
        JavaPairRDD<String, Long> reducedByKey = paired.reduceByKey((a, b) -> a + b);
        return reducedByKey;
    }

    /**
     * Task 4
     * Returns the date string of the day at which Ryanair had a strike in the given Dataframe.
     * The returned string is of the form 'YYYY-MM-DD'.
     * Hint: There were strikes at two days in the given period of time. Both are accepted as valid results.
     *
     * @param flights Dataframe containing the rows of flights
     * @return day of strike
     */
    @Override
    public String ryanairStrike(Dataset<Row> flights) {
        // TODO: Implement
        // select flights operated by ryanair
        Dataset<Row> ryanairFlights = flights
            .where("flight.operatingAirline.iataCode = 'FR'")
            .select("flight.flightStatus", "flight.originDate");
        //ryanairFlights.show(false);
        
        // select canceled flights and group by dates
        Dataset<Row> ryanairStrikes = ryanairFlights
            .where("flightStatus = 'X'")
            .select("originDate")
            .groupBy("originDate").count()
            .sort(desc("count"));
        //ryanairStrikes.show(false);

        // get the most occurred date
        String res = ryanairStrikes.select("originDate").first().toString();
        //System.out.println(res);
        return res;
    }

    /**
     * Task 5
     * Returns a dataset of Flight objects. The dataset only contains flights of the given airline with at least one
     * of the given status codes. Uses the given Dataset of Flights.
     *
     * @param flights            Dataset containing the Flight objects
     * @param airlineDisplayCode the display code of the airline
     * @param status1            the status code of the flight
     * @param status             more status codes
     * @return dataset of Flight objects matching the required fields
     */
    @Override
    public Dataset<Flight> flightsOfAirlineWithStatus(Dataset<Flight> flights, String airlineDisplayCode, String status1, String... status) {
        // TODO: Implement
        // create Spark Seesion object
        SparkConf conf = new SparkConf().setAppName("SparkExercise").setMaster("local");
        sparkSession = SparkSession.builder().config(conf).getOrCreate();

        // select matching flights
        Dataset<Flight> matchFlights = flights
            .where(col("airlineDisplayCode").equalTo(airlineDisplayCode));
        matchFlights.show(false);

        // select flights with desired status
        Dataset<Flight> flightsWithStatus1 = matchFlights
            .where(col("flightStatus").equalTo(status1));
        flightsWithStatus1.show(false);

        // more status
        /*if (status != null){
            for (String s : status) {
                // how to select matching flights without knowing the actual number of status input
            } 
        } */

        return flightsWithStatus1;
    }

    /**
     * Task 6
     * Returns the average number of flights per day between the given timestamps (both included).
     * The timestamps are of the form 'hh:mm:ss'. Uses the given Dataset of Flights.
     * Hint: You only need to consider "scheduledTime" and "originDate" for this. Do not include flights with
     * empty "scheduledTime" or "originDate" fields. You can assume that lowerLimit is always before or equal
     * to upperLimit.
     *
     * @param flights Dataset containing the arriving Flight objects
     * @param lowerLimit     start timestamp (included)
     * @param upperLimit     end timestamp (included)
     * @return average number of flights between the given timestamps (both included)
     */
    @Override
    public double avgNumberOfFlightsInWindow(Dataset<Flight> flights, String lowerLimit, String upperLimit) {
        // TODO: Implement
       // initialize variables for calculating average
        Duration d = Duration.between(LocalTime.parse(lowerLimit), LocalTime.parse(upperLimit));
        double duration = d.toHours();
        List<Integer> count = new ArrayList<Integer>(); 

        // counting matching flights
        Dataset<Flight> notEmptyTime = flights.where(col("scheduledTime").isNotNull());
        notEmptyTime.show(false);

        notEmptyTime.foreach(f -> {
            if (isBefore(f.getScheduledTime(), upperLimit) && isBefore(lowerLimit, f.getScheduledTime())) count.add(1);
        });

        // calculating average flights per hour
        int num = count.size();
        System.out.println(num);

        double res = num/duration;
        System.out.println(res);
        return res;
    }

    /**
     * Returns true if the first timestamp is before or equal to the second timestamp. Both timestamps must match
     * the format 'hh:mm:ss'.
     * You can use this for Task 6. Alternatively use LocalTime or similar API (or your own custom method).
     *
     * @param before the first timestamp
     * @param after  the second timestamp
     * @return true if the first timestamp is before or equal to the second timestamp
     */
    private static boolean isBefore(String before, String after) {
        for (int i = 0; i < before.length(); i++) {
            char bef = before.charAt(i);
            char aft = after.charAt(i);
            if (bef == ':') continue;
            if (bef < aft) return true;
            if (bef > aft) return false;
        }

        return true;
    }
}
