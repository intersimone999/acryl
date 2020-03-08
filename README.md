# ACRyL

ACRyL is a data-driven tool for detecting compatibility issues in Android apps. ACRyL requires dex2jar in order to be executed.

## Build
To build ACRyL, just run `mvn package`. The JAR file will be available in the `target` directory.

## Learning rules
ACRyL requires a ruleset file containing all the rules that it will check in the target Android app. If you have such a ruleset, simply skip this step. Otherwise, you can build one following the next steps.

### Step 1: Extracting the CAUs
To build the ruleset it is necessary to extract CAUs (Conditional API Usages) from the training APKs. To do this, run this command on each APK:
```bash
java -cp acryl.jar it.unimol.acryl.runnable.APIVersionExtractor /path/to/android-tools/ /path/to/android-sdk/ /path/to/dex2jar /path/to/target-apk.apk /path/to/output-filename.csv
```
The class output a CSV file (`/path/to/output-filename.csv`) containing all the CAUs of the chosen APK (`/path/to/target-apk.apk`).
Please, put all the extracted CSV files in a folder (e.g, `/path/to/caus/`, from now on)

### Step 2: Extracting API usages
Another piece of information needed to build the ruleset is represented by the API usages. To extract API usages, run
```bash
java -cp acryl.jar it.unimol.acryl.runnable.AndroidApiExtractor /path/to/android-tools/ /path/to/android-sdk/ /path/to/dex2jar /path/to/target-apk.apk /path/to/output-filename.csv
```

Please, put all the API usages CSV file in a folder (e.g., `path/to/api-usages/`, from now on)

### Step 3: Building the ruleset
To build the ruleset, you can just run a Ruby script from the `ruleset-builder` folder:
```bash
ruby workflow_build_ruleset.rb /path/to/caus/ /path/to/ruleset.csv /path/to/api-usages/
```

A CSV file containing the ruleset will be created at `/path/to/ruleset.csv`.


## Run
ACRyL detector can be run on the target Android app (APK) to find compatibility issues using the following command:
```bash
java -cp acryl.jar it.unimol.acryl.runnable.Detector /path/to/android-tools/ /path/to/android-sdk/ /path/to/dex2jar /path/to/target-apk.apk /path/to/output-filename.csv /path/to/ruleset.csv {min-confidence} 0 {api-level} 
```

Where:
- `/path/to/android-tools/`, `/path/to/android-sdk/`, `/path/to/dex2jar` are the same previously described;
- `/path/to/target-apk.apk` is the path to the APK of the app on which ACRyL should find compatibility issues;
- `/path/to/output-filename.csv` is the path to the output file containing the warnings found by ACRyL;
- `path/to/ruleset.csv` is the path to the ruleset;
- `{min-confidence}` is the minimum confidence level for the rules (suggested: 5). The higher this number, the lower the number of warnings.
- `0`: this argument is there for legacy reasons and it will be ignored, but you need to keep it if you want to specify the other argument;
- `{api-level}`: optional parameter indicating the target Android API level. Use a numeric value (e.g., 27).