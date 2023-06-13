package edu.illinois.cs.dt.tools.plugin;

import com.google.gson.Gson;
import com.reedoei.eunomia.io.files.FileUtil;
import edu.illinois.cs.dt.tools.utility.Level;
import edu.illinois.cs.dt.tools.utility.Logger;
import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.testrunner.data.results.TestRunResult;
import edu.illinois.cs.testrunner.runner.Runner;
import edu.illinois.cs.testrunner.runner.RunnerFactory;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision;
import org.json.JSONArray;
import org.json.JSONObject;
import scala.Option;
import scala.util.Try;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;

@Mojo(name = "replay", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class ReplayMojo extends AbstractIDFlakiesMojo {
    private Path replayPath;
    private static final int NumRuns = 1;

    @Override
    public void execute() {
        super.execute();
        final Option<Runner> runnerOption = RunnerFactory.from(mavenProject);

        if (runnerOption.isDefined()) {
            replayPath = Paths.get(Configuration.config().getProperty("replay.path"));
            final Path outputPath = Paths.get(Configuration.config().properties().getProperty("replay.output_path"));

            try {
                final Runner runner = runnerOption.get(); // safe because we checked above


                //*********
                File gc_logs = new File(mavenProject.getBasedir() + File.separator + "info_logs" + File.separator + "gc_logs");
                if (!gc_logs.exists()) gc_logs.mkdirs();

                File comp_logs = new File(mavenProject.getBasedir() + File.separator + "info_logs" + File.separator + "comp_logs");
                if (!comp_logs.exists()) comp_logs.mkdirs();
                //*********

                //*****
//                String fileName = replayPath.getFileName().toString();
//                String orderNumberStr = fileName.split("-")[1];
//                int orderNumber = Integer.parseInt(orderNumberStr);
                //*****

                Gson gson = new Gson();
                JSONArray testRunsResultsArr = new JSONArray();

                List<String> currentOrder = testOrder();

                Logger.getGlobal().log(Level.INFO, "testOrder length = " + currentOrder.size() + "\n\n");
                System.out.println("testOrder length = " + currentOrder.size() + "\n\n");
                double[] curTimes = new double[NumRuns];
                for(int i = 0; i < NumRuns; i++){
                    TestRunResult res = runner.runList(currentOrder).get();
                    JSONObject curJson = new JSONObject(gson.toJson(res));
                    testRunsResultsArr.put(curJson);
                    double time = res.time();
                    curTimes[i] = Precision.round(time, 3);

                    //move the gc and comp logs
//                    if(Files.exists(Paths.get("gc.log"))){
//                        Files.move(Paths.get("gc.log"), Paths.get(String.format("info_logs/gc_logs/gc_%d.log", orderNumber)), StandardCopyOption.REPLACE_EXISTING);
//                    }
//                    if(Files.exists(Paths.get("comp.log"))) {
//                        Files.move(Paths.get("comp.log"), Paths.get(String.format("info_logs/comp_logs/comp_%d.log", orderNumber)), StandardCopyOption.REPLACE_EXISTING);
//                    }
                }

                DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
                for (Double v : curTimes) {
                    descriptiveStatistics.addValue(v);
                }

                double mean = Precision.round(descriptiveStatistics.getMean(), 3);
                double median = Precision.round(descriptiveStatistics.getPercentile(50),3);
                double standardDeviation = Precision.round(descriptiveStatistics.getStandardDeviation(), 3);
                double variance = Precision.round(standardDeviation * standardDeviation, 3);
                double min = Precision.round(descriptiveStatistics.getMin(), 3);
                double max = Precision.round(descriptiveStatistics.getMax(), 3);

                JSONObject jsonObject = new JSONObject();
                try {
                    Field changeMap = jsonObject.getClass().getDeclaredField("map");
                    changeMap.setAccessible(true);
                    changeMap.set(jsonObject, new LinkedHashMap<>());
                    changeMap.setAccessible(false);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    System.out.println(e.getMessage());
                }

                JSONObject json = new JSONObject();
                try {
                    Field changeMap = json.getClass().getDeclaredField("map");
                    changeMap.setAccessible(true);
                    changeMap.set(json, new LinkedHashMap<>());
                    changeMap.setAccessible(false);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    System.out.println(e.getMessage());
                }
                JSONArray array = new JSONArray();
                jsonObject.put("min", min);
                jsonObject.put("max", max);
                jsonObject.put("mean", mean);
                jsonObject.put("median", median);
                jsonObject.put("sd", standardDeviation);
                jsonObject.put("variance", variance);
                array.put(jsonObject);
                json.put("Stats", array);
                json.put("Order", currentOrder);
                json.put("Times", curTimes);
                json.put("TestRunResults", testRunsResultsArr);
                Files.write(outputPath, json.toString().getBytes());
            } catch (IOException e) {
                Logger.getGlobal().log(Level.SEVERE, e.toString());
            }
        } else {
            Logger.getGlobal().log(Level.INFO, "Module is not using a supported test framework (probably not JUnit).");
        }
    }

    private List<String> testOrder() throws IOException {
        try {
            return new Gson().fromJson(FileUtil.readFile(replayPath), TestRunResult.class).testOrder();
        } catch (Exception e) {
            return Files.readAllLines(replayPath);
        }
    }
}
