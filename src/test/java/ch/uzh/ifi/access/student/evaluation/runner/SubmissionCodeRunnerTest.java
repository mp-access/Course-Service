package ch.uzh.ifi.access.student.evaluation.runner;

import ch.uzh.ifi.access.coderunner.CodeRunner;
import ch.uzh.ifi.access.course.model.Exercise;
import ch.uzh.ifi.access.course.model.ExerciseType;
import ch.uzh.ifi.access.course.model.VirtualFile;
import ch.uzh.ifi.access.student.model.CodeSubmission;
import ch.uzh.ifi.access.student.model.ExecResult;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SubmissionCodeRunnerTest {

    private VirtualFile src;
    private VirtualFile test;
    private VirtualFile init;

    @Before
    public void setUp() throws IOException {
        Path path = Paths.get("./runner/s1");
        FileSystemUtils.deleteRecursively(path);

        Path psrc = Paths.get("./src/test/resources/test_code/solutioncode.py");
        src = new VirtualFile(psrc.toAbsolutePath().normalize().toString(), "");

        Path ptest = Paths.get("./src/test/resources/test_code/test_suite.py");
        test = new VirtualFile(ptest.toAbsolutePath().normalize().toString(), "");

        Path pInit = Paths.get("./src/test/resources/test_code/__init__.py");
        init = new VirtualFile(pInit.toAbsolutePath().normalize().toString(), "");
    }

    @Test
    public void test() throws DockerCertificateException, InterruptedException, DockerException, IOException {
        Exercise ex = Exercise.builder()
                .id("e1")
                .private_files(Arrays.asList(init, test))
                .type(ExerciseType.code).build();

        CodeSubmission sub = CodeSubmission.builder()
                .id("s1")
                .exerciseId(ex.getId())
                .publicFiles(Arrays.asList(init, src))
                .build();

        ExecResult result = new SubmissionCodeRunner(new CodeRunner()).execSubmissionForExercise(sub, ex);
        Assertions.assertThat(result.getStderr()).containsIgnoringCase("Ran 8 tests in");

        Assertions.assertThat(new File("./runner/" + sub.getId() + "/code/" + src.getName() + "." + src.getExtension())).exists();
        Assertions.assertThat(new File("./runner/" + sub.getId() + "/code/" + init.getName() + "." + init.getExtension())).exists();

        Assertions.assertThat(new File("./runner/" + sub.getId() + "/test/" + test.getName() + "." + test.getExtension())).exists();
        Assertions.assertThat(new File("./runner/" + sub.getId() + "/test/" + init.getName() + "." + init.getExtension())).exists();
    }

}