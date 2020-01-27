package ch.uzh.ifi.access.student.evaluation.runner;

import ch.uzh.ifi.access.coderunner.CodeRunner;
import ch.uzh.ifi.access.coderunner.RunResult;
import ch.uzh.ifi.access.course.model.CodeExecutionLimits;
import ch.uzh.ifi.access.course.model.Exercise;
import ch.uzh.ifi.access.course.model.VirtualFile;
import ch.uzh.ifi.access.student.model.CodeSubmission;
import ch.uzh.ifi.access.student.model.ExecResult;
import com.spotify.docker.client.exceptions.DockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubmissionCodeRunner {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionCodeRunner.class);

    private static final int MAX_LOG_LENGTH = 100000;

    private static final String LOCAL_RUNNER_DIR = "./runner";

    private static final String PUBLIC_FOLDER = "public";

    private static final String RESOURCE_FOLDER = "resource";

    private static final String PRIVATE_FOLDER = "private";

    private static final String INIT_FILE = "__init__.py";

    private static final String DELIMITER = "======================----------------------======================";
    private static final String DELIMITER_CMD = String.format("echo \"%s\" >&2", DELIMITER);

    private CodeRunner runner;

    private FSHierarchySerializer hierarchySerializer;

    @Autowired
    public SubmissionCodeRunner(CodeRunner runner, FSHierarchySerializer hierarchySerializer) {
        this.runner = runner;
        this.hierarchySerializer = hierarchySerializer;
    }


    public ExecResult execSubmissionForExercise(CodeSubmission submission, Exercise exercise) throws InterruptedException, DockerException, IOException {

        Path path = Paths.get(LOCAL_RUNNER_DIR + "/" + submission.getId());
        logger.debug(path.toAbsolutePath().normalize().toString());

        CodeExecutionLimits executionLimits = exercise.getExecutionLimits();
        ExecResult res = submission.isGraded() ? executeSubmission(path, submission, exercise, executionLimits) : executeSmokeTest(path, submission, exercise, executionLimits);

        hierarchySerializer.removeDirectory(path);

        return res;
    }

    private ExecResult executeSmokeTest(Path workPath, CodeSubmission submission, Exercise exercise, CodeExecutionLimits executionLimits) throws IOException, DockerException, InterruptedException {
        hierarchySerializer.persistFilesIntoFolder(String.format("%s/%s", workPath.toString(), PUBLIC_FOLDER), submission.getPublicFiles());
        hierarchySerializer.persistFilesIntoFolder(String.format("%s/%s", workPath.toString(), RESOURCE_FOLDER), exercise.getResource_files());
        Files.createFile(Paths.get(workPath.toAbsolutePath().toString(), INIT_FILE));

        VirtualFile selectedFileForRun = submission.getSelectedFile();
        String executeScriptCommand = buildExecScriptCommand(selectedFileForRun);
        String testCommand = buildExecTestSuiteCommand(PUBLIC_FOLDER);

        List<String> commands = List.of(executeScriptCommand, DELIMITER_CMD, testCommand)
                .stream()
                .filter(cmd -> !StringUtils.isEmpty(cmd))
                .collect(Collectors.toList());

        final String fullCommand = String.join(" ; ", commands);
        return mapSmokeToExecResult(runner.attachVolumeAndRunBash(workPath.toString(), fullCommand, executionLimits), executeScriptCommand, testCommand);
    }

    private ExecResult executeSubmission(Path workPath, CodeSubmission submission, Exercise exercise, CodeExecutionLimits executionLimits) throws IOException, DockerException, InterruptedException {
        hierarchySerializer.persistFilesIntoFolder(String.format("%s/%s", workPath.toString(), PUBLIC_FOLDER), submission.getPublicFiles());
        hierarchySerializer.persistFilesIntoFolder(String.format("%s/%s", workPath.toString(), RESOURCE_FOLDER), exercise.getResource_files());
        hierarchySerializer.persistFilesIntoFolder(String.format("%s/%s", workPath.toString(), PRIVATE_FOLDER), exercise.getPrivate_files());

        Files.createFile(Paths.get(workPath.toAbsolutePath().toString(), INIT_FILE));

        String testCommand = buildExecTestSuiteCommand(PRIVATE_FOLDER);
        String setupScriptCommand = exercise.hasGradingSetupScript() ? buildSetupScriptCommand(exercise.getGradingSetup()) : "";

        List<String> commands = List.of(DELIMITER_CMD, setupScriptCommand, testCommand)
                .stream()
                .filter(cmd -> !StringUtils.isEmpty(cmd))
                .collect(Collectors.toList());

        final String fullCommand = String.join(" ; ", commands);
        return mapSubmissionToExecResult(runner.attachVolumeAndRunBash(workPath.toString(), fullCommand, executionLimits));
    }

    protected ExecResult mapSubmissionToExecResult(RunResult runResult) {
        if (!runResult.isTimeout() && !runResult.isOomKilled()) {
            return ExecResult.builder()
                    .stdout("").testLog("")
                    .evalLog(extractEvalLog(runResult))
                    .build();
        }

        return ExecResult.builder()
                .stdout(runResult.getConsole())
                .testLog("").evalLog("")
                .build();
    }

    protected ExecResult mapSmokeToExecResult(RunResult runResult, String exeCommand, String testCommand) {
        if (!runResult.isTimeout() && !runResult.isOomKilled()) {
            return ExecResult.builder()
                    .stdout(extractConsole(runResult))
                    .testLog(extractTestOutput(runResult))
                    .evalLog("")
                    .usedConsoleCommand(exeCommand).usedTestCommand(testCommand)
                    .build();
        }

        return ExecResult.builder()
                .stdout(runResult.getConsole())
                .testLog(runResult.getStdErr())
                .evalLog("")
                .usedConsoleCommand(exeCommand).usedTestCommand(testCommand)
                .build();
    }

    private String extractConsole(RunResult res) {
        String extracted = "";
        String console = res.getConsole();

        if (console != null && !console.trim().isEmpty()) {

            int indexOfDelimiterStdOut = console.contains(DELIMITER) ? console.lastIndexOf(DELIMITER) : console.length();
            extracted = console.substring(0, indexOfDelimiterStdOut);

            if (extracted.length() > MAX_LOG_LENGTH) {
                logger.warn(String.format("Trim console log (keep beginning) to max length of %s.", MAX_LOG_LENGTH));
                extracted = extracted.substring(0, MAX_LOG_LENGTH) + " ... Logs size exceeded limit. Log has been truncated.";
            }
        }

        return extracted;
    }

    private String extractEvalLog(RunResult res) {
        String extracted = "";
        String stderr = res.getStdErr();

        if (stderr != null && !stderr.trim().isEmpty()) {
            if (stderr.contains(DELIMITER)) {
                extracted = stderr.substring(stderr.lastIndexOf(DELIMITER));
                if (extracted.length() > MAX_LOG_LENGTH) {
                    logger.warn(String.format("Trim stdErr log (keep end) to max length of %s.", MAX_LOG_LENGTH));
                    extracted = " ... Logs size exceeded limit. Beginning of log has been truncated ...\n" + extracted.substring(0, MAX_LOG_LENGTH);
                }
            } else {
                logger.warn("Did not find a delimiter to extract private suite evaluation log");
            }
        }

        return extracted;
    }

    private String extractTestOutput(RunResult res) {
        String extracted = "";
        String stderr = res.getStdErr();

        if (stderr != null && !stderr.trim().isEmpty()) {
            int indexOfDelimiter = stderr.contains(DELIMITER) ? stderr.lastIndexOf(DELIMITER) : stderr.length();
            extracted = stderr.substring(indexOfDelimiter).replace(DELIMITER + "\n", "");

            if (extracted.length() > MAX_LOG_LENGTH) {
                logger.warn(String.format("Trim stdErr log (keep end) to max length of %s.", MAX_LOG_LENGTH));
                extracted = extracted.substring(0, MAX_LOG_LENGTH) + " ... Logs size exceeded limit. Log has been truncated.";
            }

        }

        return extracted;
    }

    private String buildSetupScriptCommand(String pathToScript) {
        return String.format("./%s", pathToScript);
    }

    private String buildExecScriptCommand(VirtualFile script) {
        return String.format("python -m %s.%s", PUBLIC_FOLDER, script.getName());
    }

    private String buildExecTestSuiteCommand(String testFolder) {
        return String.format("python -m unittest discover %s -v", testFolder);
    }
}
