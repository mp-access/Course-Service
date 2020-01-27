package ch.uzh.ifi.access.coderunner;

import ch.uzh.ifi.access.course.model.CodeExecutionLimits;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Configuration
@ConfigurationProperties("access.runner")
public class PythonImageConfig {

    public static final String STUDENT_CODE_FOLDER = "/usr/src/";

    @Value("python")
    private String pythonImage = "hoal/access-python:3.7";

    public ContainerConfig containerConfig(String[] cmd, CodeExecutionLimits executionLimits) {
        executionLimits = Optional.ofNullable(executionLimits).orElse(CodeExecutionLimits.DEFAULTS);

        return ContainerConfig
                .builder()
                .hostConfig(hostConfig(executionLimits))
                .image(pythonImage)
                .networkDisabled(!executionLimits.isNetworking())
                .workingDir(STUDENT_CODE_FOLDER)
                .attachStdout(true)
                .attachStderr(true)
                .cmd(cmd)
                .build();
    }

    private HostConfig hostConfig(CodeExecutionLimits executionLimits) {
        if (executionLimits.isTesting()) {
            return memoryAndCpuLimitsDisabled();
        }

        return withMemoryAndCpuLimits(executionLimits);
    }

    private HostConfig memoryAndCpuLimitsDisabled() {
        return HostConfig.builder().build();
    }

    private HostConfig withMemoryAndCpuLimits(CodeExecutionLimits limits) {
        return HostConfig.builder()
                .memory(limits.getMemoryInMb())
                .cpuQuota(limits.getCpuQuota())
                .build();
    }
}
