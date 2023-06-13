package edu.illinois.cs.dt.tools.plugin;

import com.google.gson.Gson;
import com.reedoei.eunomia.io.files.FileUtil;
import edu.illinois.cs.dt.tools.detection.TestShuffler;
import edu.illinois.cs.dt.tools.utility.Level;
import edu.illinois.cs.dt.tools.utility.Logger;
import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.testrunner.data.results.TestResult;
import edu.illinois.cs.testrunner.data.results.TestRunResult;
import edu.illinois.cs.testrunner.runner.Runner;
import edu.illinois.cs.testrunner.runner.RunnerFactory;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.util.Precision;
import org.json.JSONArray;
import org.json.JSONObject;
import scala.Option;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

@Mojo(name = "random-stat", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RandomStatMojo extends AbstractIDFlakiesMojo {

    private Path originalOrderPath;
    private static final int OrdersCOUNT = 100;
    private static final int NumRuns = 10;

    @Override
    public void execute() {
        super.execute();
        final Option<Runner> runnerOption = RunnerFactory.from(mavenProject);

        if (runnerOption.isDefined()) {
            originalOrderPath = Paths.get(Configuration.config().properties().getProperty("original.order_path"));

            try {
                //create directory structure to save the stats
                File statsDir = new File(mavenProject.getBasedir() + "/stats/");
                if (!statsDir.exists()) statsDir.mkdirs();
//                if(!createStat){
//                    System.out.println("Error creating the stat directory");
//                    return;
//                }

                File ordersDir = new File(statsDir, "order-runs");
                if (!ordersDir.exists()) ordersDir.mkdirs();
//                if(!createOrders){
//                    System.out.println("Error creating the order-runs directory");
//                    return;
//                }

                //*********
                //directories to save the gc and comp logs

                //*********
                File gc_logs = new File(statsDir + File.separator + "info_logs" + File.separator + "gc_logs");
                if (!gc_logs.exists()) gc_logs.mkdirs();

                File comp_logs = new File(statsDir + File.separator + "info_logs" + File.separator + "comp_logs");
                if (!comp_logs.exists()) comp_logs.mkdirs();
                //*********

                //*******

                final Runner runner = runnerOption.get(); // safe because we checked above
                Gson gson = new Gson();
                Map<List<String>, Pair<TestRunResult[],Double>> runTimes = new LinkedHashMap<>();  //map an order to its runs
                List<String> originalOrder = testOrder();


                //run 10 orders to filter out tests (in case we have flaky tests. Just to get a stable set of tests).
                Set<String> difference = new HashSet<>();
                Map<String, String[]> diffMap = new HashMap<>();
                int c = 1;
                while(!originalOrder.isEmpty()) {
                    //create a shuffler from the original order
                    TestShuffler testShuffler = new TestShuffler("random", 100, originalOrder, mavenProject.getBasedir());
                    TestRunResult filterRes = runner.runList(originalOrder).get();
                    Map<String, TestResult> filterResults = filterRes.results();
                    for (int i = 1; i < 7; i++) {
                        originalOrder = testShuffler.shuffledOrder(5);
                        TestRunResult res = runner.runList(originalOrder).get();
                        diffTests(filterResults, res.results(), difference, diffMap);
                        filterResults = res.results();
                    }

                    //consistant results
                    if(difference.isEmpty()){
                        Files.write(Paths.get(statsDir.getAbsolutePath() + "/original-order-used"), originalOrder, Charset.defaultCharset());
                        break;
                    }

                    //remove and repeat
                    Path removalPath = Paths.get(statsDir.getAbsolutePath() + "/after-" + c + "-removal");
                    System.out.println(String.format("\nCreated the removalPath directory at %s", removalPath));
                    Files.write(removalPath, originalOrder, Charset.defaultCharset());
                    originalOrder.removeAll(difference);
                    String textToAppend = "\nAfter removal:\n";
                    Files.write(removalPath, textToAppend.getBytes(), StandardOpenOption.APPEND);
                    Files.write(removalPath, originalOrder, Charset.defaultCharset(), StandardOpenOption.APPEND);
                    FileWriter fw = new FileWriter(removalPath.toString(), true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw);
                    out.println();
                    out.println("Difference:");
                    out.println(difference);
                    out.println("\ncurrent map: ");
                    for(Map.Entry<String, String[]> entry: diffMap.entrySet()){
                        //put key and value separated by a colon
                        out.println(entry.getKey() + ":" + Arrays.toString(entry.getValue()));
                    }
                    difference.clear();
                    diffMap.clear();
                    out.flush();
                    out.close();
                    bw.close();
                    fw.close();
                    c++;
                }

                //run the original order (or created order) and save its individual results and write it to a file as well;
                JSONObject origJson = new JSONObject();
                JSONArray origTestRunsArr = new JSONArray();
                TestRunResult[] origTestRuns = new TestRunResult[NumRuns];
                TestRunResult firstRun = runner.runList(originalOrder).get();
                origTestRuns[0] = firstRun;
                JSONObject firstOrigJson = new JSONObject(gson.toJson(firstRun));
                origTestRunsArr.put(firstOrigJson);
                double origRunsTime = firstRun.time();
                Map<String, TestResult> originalResults = firstRun.results(); //holds the individual run results
                for(int j = 1; j < NumRuns; j++){
                    TestRunResult res = runner.runList(originalOrder).get();

                    //get the results of this testRunResults
                    Map<String, TestResult> resResults = res.results();
                    int cmp = compareRes(originalResults, resResults);

                    if(!(cmp == 0)){
                        System.out.println("****** Original run results are flaky. Bad subject? *****");
                        return;
                    }
                    JSONObject curJson = new JSONObject(gson.toJson(res));
                    origTestRunsArr.put(curJson);
                    origTestRuns[j] = res;
                    origRunsTime += res.time();
                }

                double origAverage = origRunsTime / NumRuns;
                Pair<TestRunResult[],Double> origPair = new Pair<>(origTestRuns, Precision.round(origAverage, 3));
                runTimes.put(originalOrder, origPair);
                origJson.put("TestRunResults", origTestRunsArr);
                Files.write(Paths.get(statsDir.getAbsolutePath() + "/original-order.json"), origJson.toString().getBytes());

                //we want to run 100 random orders
                TestShuffler testShuffler = new TestShuffler("random", 100, originalOrder, mavenProject.getBasedir());
                int threshold = 0; // maybe if we fail on so many orders, it's a bad subject?
                int counter = 1;
                while(runTimes.size() < OrdersCOUNT){
                    //generate a new order
                    originalOrder = testShuffler.shuffledOrder(5);
                    TestRunResult[] curTestRuns = new TestRunResult[NumRuns];
                    boolean check = true;
                    for(int j = 0; j < NumRuns; j++){
                        TestRunResult res = runner.runList(originalOrder).get();
                        int cmp = compareRes(originalResults, res.results());
                        if(!(cmp == 0)){    //check all results are the same as the original order
                            //write the testrunresult to a .json file for later
                            threshold ++;
                            File dir = new File(statsDir, "failing-orders");
                            if (!dir.exists()) dir.mkdirs();
                            FileWriter writer = new FileWriter(dir.getAbsolutePath() + "/testrun-" + threshold+".json");
                            gson.toJson(res, writer);
                            writer.flush();
                            writer.close();
                            check = false;
                            break;
                        }
                        curTestRuns[j] = res;
                    }
                    //if there is not a passing testrunresult, continue
                    if(!check){
                        if(threshold == 30){
                            System.out.println("****** " + threshold + " TestRuns failed, bad subject? *****");
                            return;
                        }
                        continue;
                    }

                    double runsTime = 0.0;
                    for(TestRunResult run: curTestRuns){
                        double time = Precision.round(run.time(), 3);
                        runsTime += time;
                    }
                    double average = runsTime / NumRuns;

                    Pair<TestRunResult[],Double> pair = new Pair<>(curTestRuns, Precision.round(average, 3));
                    runTimes.put(originalOrder, pair);

                    //write out the json file for this order
                    JSONObject testRunJson = new JSONObject();
                    JSONArray testRunsArr = new JSONArray();

                    for(TestRunResult run: curTestRuns){
                        JSONObject curJson = new JSONObject(gson.toJson(run));
                        testRunsArr.put(curJson);
                    }

                    testRunJson.put("TestRunResults", testRunsArr);
                    Files.write(Paths.get(ordersDir.getAbsolutePath() + "/order-" + counter + ".json"), testRunJson.toString().getBytes());

                    //move the gc and comp logs
                    if(Files.exists(Paths.get("gc.log"))){
                        Files.move(Paths.get("gc.log"), Paths.get(String.format("stats/info_logs/gc_logs/gc_%d.log", counter)), StandardCopyOption.REPLACE_EXISTING);
                    }
                    if(Files.exists(Paths.get("comp.log"))) {
                        Files.move(Paths.get("comp.log"), Paths.get(String.format("stats/info_logs/comp_logs/comp_%d.log", counter)), StandardCopyOption.REPLACE_EXISTING);
                    }

                    counter++;
                }

                //now, get the mean and sd (based on all orders)
                DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
                for (Pair<TestRunResult[], Double> v : runTimes.values()) {
                    double average = v.getSecond();
                    descriptiveStatistics.addValue(average);
                }

                double mean = Precision.round(descriptiveStatistics.getMean(), 3);
                double median = Precision.round(descriptiveStatistics.getPercentile(50),3);
                double standardDeviation = Precision.round(descriptiveStatistics.getStandardDeviation(), 3);
                double variance = Precision.round(standardDeviation * standardDeviation, 3);
                double min = Precision.round(descriptiveStatistics.getMin(), 3);
                double max = Precision.round(descriptiveStatistics.getMax(), 3);

                //to control the order of the objects in json
                JSONObject overallStatsJson = new JSONObject();
                try {
                    Field changeMap = overallStatsJson.getClass().getDeclaredField("map");
                    changeMap.setAccessible(true);
                    changeMap.set(overallStatsJson, new LinkedHashMap<>());
                    changeMap.setAccessible(false);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    System.out.println(e.getMessage());
                }

                JSONObject overallJson = new JSONObject();
                try {
                    Field changeMap = overallJson.getClass().getDeclaredField("map");
                    changeMap.setAccessible(true);
                    changeMap.set(overallJson, new LinkedHashMap<>());
                    changeMap.setAccessible(false);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    System.out.println(e.getMessage());
                }

                //write the stats
                JSONArray overallStatsArray = new JSONArray();
                overallStatsJson.put("min", min);
                overallStatsJson.put("max", max);
                overallStatsJson.put("mean", mean);
                overallStatsJson.put("median", median);
                overallStatsJson.put("sd", standardDeviation);
                overallStatsJson.put("variance", variance);
                overallStatsArray.put(overallStatsJson);
                overallJson.put("Overall Stats", overallStatsArray);

                JSONArray allOrdersArr = new JSONArray();
                //int counter = 1;
                for(Map.Entry<List<String>, Pair<TestRunResult[],Double>> e: runTimes.entrySet()){
                    List<String> key = e.getKey();
                    Pair<TestRunResult[],Double> value = e.getValue();
                    JSONObject order = new JSONObject();
                    try {
                        Field changeMap = order.getClass().getDeclaredField("map");
                        changeMap.setAccessible(true);
                        changeMap.set(order, new LinkedHashMap<>());
                        changeMap.setAccessible(false);
                    } catch (IllegalAccessException | NoSuchFieldException ex) {
                        System.out.println(ex.getMessage());
                    }
                    DescriptiveStatistics orderStats = new DescriptiveStatistics();

                    //write the testruns for this order to a json file
//                    JSONObject testRunJson = new JSONObject();
//                    JSONArray testRunsArr = new JSONArray();
                    //FileWriter writer = new FileWriter(orders.getAbsolutePath() + "/order-" + counter+".json");
                    double[] times = new double[NumRuns];
                    int m = 0;
                    for(TestRunResult run: value.getFirst()){
                        double time = Precision.round(run.time(), 3);
                        orderStats.addValue(time);
//                        JSONObject curJson = new JSONObject(gson.toJson(run));
//                        testRunsArr.put(curJson);
                        times[m] = time;
                        m++;
                    }
//                    testRunJson.put("TestRunResults", testRunsArr);
//                    Files.write(Paths.get(ordersDir.getAbsolutePath() + "/order-" + counter + ".json"), testRunJson.toString().getBytes());
                    //writer.close();

                    order.put("order", key);
                    order.put("times", times);
                    order.put("average", value.getSecond());
                    order.put("median", Precision.round(orderStats.getPercentile(50),3));
                    double sd = orderStats.getStandardDeviation();
                    order.put("sd", Precision.round(sd,3));
                    order.put("variance", Precision.round(sd * sd, 3));
                    order.put("min", Precision.round(orderStats.getMin(), 3));
                    order.put("max", Precision.round(orderStats.getMax(), 3));
                    allOrdersArr.put(order);
                    counter++;
                }
                overallJson.put("Orders", allOrdersArr);
                Files.write(Paths.get(statsDir.getAbsolutePath() + "/random_Stats.json"), overallJson.toString().getBytes());

            } catch (IOException e) {
                Logger.getGlobal().log(Level.SEVERE, e.toString());
            }
        } else {
        Logger.getGlobal().log(Level.INFO, "Module is not using a supported test framework (probably not JUnit).");
    }

    }

    private List<String> testOrder() throws IOException {
        try {
            return new Gson().fromJson(FileUtil.readFile(originalOrderPath), TestRunResult.class).testOrder();
        } catch (Exception e) {
            return Files.readAllLines(originalOrderPath);
        }
    }

    //set of tests with different results
    private void diffTests(Map<String, TestResult> m1, Map<String, TestResult> m2, Set<String> diff, Map<String, String[]> map){
        //cmp the results
        for(Map.Entry<String, TestResult> entry: m1.entrySet()){
            String key = entry.getKey();
            TestResult val = entry.getValue();
            TestResult val2 = m2.get(key);
            if(!val.result().equals(val2.result())){
                if(!diff.contains(key)){
                    diff.add(key);
                    String[] arr = {val.result().name(), val2.result().name()};
                    map.put(key, arr);
                }
            }
        }
    }

    //compare two maps (for comparing the results)
    private int compareRes(Map<String, TestResult> m1, Map<String, TestResult> m2){
        //if not the same set of tests, smth is wrong
        if(!m1.keySet().equals(m2.keySet())){
            return -1;
        }

        //cmp the results
        for(Map.Entry<String, TestResult> entry: m1.entrySet()){
            String key = entry.getKey();
            TestResult val = entry.getValue();
            TestResult val2 = m2.get(key);
            if(!val.result().equals(val2.result())){
                return 1;
            }
        }
        return 0;
    }

}
