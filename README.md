# iDFlakies

This repository contains tools for detecting/classifying flaky tests.

More details about iDFlakies can be found in its [paper](http://mir.cs.illinois.edu/winglam/publications/2019/LamETAL19iDFlakies.pdf) and [website](https://sites.google.com/view/flakytestdataset).

# OS
Linux based systems only (in Windows system there are some problems).

# Quickstart

iDFlakies may work on Maven-based or Gradle-based projects. Instructions are [here](#Using-iDFlakies-on-a-Maven-project) for Maven-based projects and [here](https://github.com/idflakies/iDFlakies/blob/master/README-gradle.md) for Gradle-based projects.

## Using iDFlakies on a Maven project

One may use iDFlakies on a specific Maven project by getting it from Maven Central or building it themselves.


### Automatically setting up the pom.xml for iDFlakies

Run the following command to automatically setup the pom.xml for iDFlakies.


```shell
bash pom-modify/modify-project.sh path_to_maven_project
```

By default, modify-project.sh will use the version of iDFlakies from Maven Central. If you wish to use
the version of iDFlakies built locally, you can run the following instead. 

```shell
bash pom-modify/modify-project.sh path_to_maven_project 2.0.1-SNAPSHOT
```

### Manually setting up the pom.xml for iDFlakies

Copy the following plugin into the Maven project's pom.xml.
You do not need to perform this step if you have already completed the instructions
in [Automatically setting up the pom.xml for iDFlakies](#automatically-setting-up-the-pomxml-for-idflakies).

```xml
<build>
    ...
    <plugins>
        ...
        <plugin>
            <groupId>edu.illinois.cs</groupId>
            <artifactId>idflakies-maven-plugin</artifactId>
            <version>2.0.1-SNAPSHOT</version>
        </plugin>
    </plugins>
</build>
```

### Running iDFlakies

Once iDFlakies is added to a Maven project, one can then run iDFlakies on the project with the following command.

```shell
mvn idflakies:detect -Ddetector.detector_type=random-class-method -Ddt.randomize.rounds=10 -Ddt.detector.original_order.all_must_pass=false
```

iDFlakies configuration options:
* ```detector.detector_type``` - Configurations of iDFlakies as described on pages 3 and 4 of our [paper](http://mir.cs.illinois.edu/winglam/publications/2019/LamETAL19iDFlakies.pdf). Default is ```random``` (random-class-method).
* ```dt.randomize.rounds``` - Number of times to run the test suite. Default is ```20```.
* ```dt.detector.original_order.all_must_pass``` - Controls whether iDFlakies must use an original order of tests where all of them pass or not. Default is ```true``` (i.e., iDFlakies will exit if within three runs of the test suite, it does observe all tests to pass in one of the runs).
* ```dt.original.order``` - Enables one to specify to iDFlakies the exact list of tests that should be run. Test names should be fully-qualified, use only ```.``` to separate different parts of the test name (e.g., ```com.github.kevinsawicki.http.EncodeTest.encode```), and test names are separated from each other by line breaks. This option is unlikely to be useful when running multiple modules at once and is best suited for running the tests of a specific module. Absolute paths should be used or the file path should be relative to the module that contains the tests.

## Running iDFlakies framework

The main script is located in `scripts/docker/create_and_run_dockers.sh`.
To use the script, you will need Docker and Python (e.g., Python version 2.7.17) installed and a `csv` file containing the GitHub URL and SHA that you wish to run iDFlakies on with one subject per line.

For example:
```
https://github.com/kevinsawicki/http-request,2d62a3e9da726942a93cf16b6e91c0187e6c0136
```

Once you have created the csv file, you can simply run:

```shell
bash create_and_run_dockers.sh <path to csv> <round num> <timeout (seconds)>
```

The script creates a new Docker image for each project/SHA, if one does not already exist.
Otherwise, the script will reuse the existing Docker image for the project.
When the script starts up the Docker container, it will checkout this repository (or update it to the latest version if it is already checked out).
To make changes to the scripts and tools and have them be used within the container, one may need to change `scripts/docker/update.sh`.

### Example

```shell
cd scripts/docker
bash create_and_run_dockers.sh icst-dataset/comprehensive-individual-split/kevinsawicki.http-request.csv 10 1000000
```

This example takes about 22 minutes to run.
Running the above will output `scripts/docker/all-output/kevinsawicki.http-request_output` as soon as iDFlakies is running.

An example of the log and output directory generated can be found in the [here](https://drive.google.com/drive/folders/1sVf0PNKjZbDG5wlZnYWkhTuQYiNNZrTv).
Note that due to the inherent nondeterminism of the tool (e.g., use of random test orderings) and flaky tests, the log and output directory generated from another run may not exactly match the example log and output directory.

To see all of the flaky tests detected by iDFlakies one can refer simply to the `scripts/docker/all-output/kevinsawicki.http-request_output/all_flaky_tests_list.csv` file.
The file lists all the flaky tests detected for each round of each configuration (e.g., `com.github.kevinsawicki.http.HttpRequestTest.basicProxyAuthentication,./lib/detection-results/random/round0.json`).
Note that multiple rounds may detect the same test.

To see more information about a specific test (e.g., a passing and failing ordering of the test), please see the JSON file(s) that detected the test.
The output of iDFlakies is explained in depth in `scripts/README.md` and the csv files used to evaluate iDFlakies are in `scripts/docker/icst-dataset`.

# Cite

If you use iDFlakies, please cite our corresponding [ICST paper](http://mir.cs.illinois.edu/winglam/publications/2019/LamETAL19iDFlakies.pdf):
```
@inproceedings{LamETAL2019ICST,
    author    = {Lam, Wing and Oei, Reed and Shi, August and Marinov, Darko and Xie, Tao},
    title     = {{iDFlakies}: {A} Framework for Detecting and Partially Classifying Flaky Tests},
    booktitle = {12th {IEEE} International Conference on Software Testing, Verification and Validation},
    pages     = {312--322},
    year      = {2019},
}
```
