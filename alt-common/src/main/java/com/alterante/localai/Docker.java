package com.alterante.localai;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Docker {

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("hello localai");

        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // Pull image if not present
        dockerClient.pullImageCmd("localai/localai:latest").start().awaitCompletion();

        ExposedPort port= ExposedPort.tcp(8080);
        Ports ports= new Ports();
        ports.bind(port, Ports.Binding.bindPort(8080));
        // Create container
        CreateContainerResponse container = dockerClient.createContainerCmd("localai/localai:latest")
                .withCmd("sleep","500000")
                .withExposedPorts(port)
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(ports).withAutoRemove(true))
                .exec();

        String containerId = container.getId();

        // Start container
        dockerClient.startContainerCmd(containerId).exec();

        // Exec shell command inside the container
        //String[] command = {"bash", "-c", "apt update &&  apt install -y ffmpeg &&  apt -y install llvm &&  apt -y install curl && cd / && curl https://localai.io/install.sh | sh && local-ai models install whisper-base &&  local-ai run whisper-base"};
        String[] command = {"bash", "-c", "apt update && apt -y install curl && cd / && curl https://localai.io/install.sh | sh"};
        String execId = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec()
                .getId();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        dockerClient.execStartCmd(execId)
                .exec(new ExecStartResultCallback(outputStream, System.err))
                .awaitCompletion();

        System.out.println("Output: " + outputStream.toString());

        String[] command2 = {"bash", "-c", "local-ai models install whisper-base"};
        String execId2 = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command2)
                .exec()
                .getId();

        ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
        dockerClient.execStartCmd(execId2)
                .exec(new ExecStartResultCallback(outputStream2, System.err))
                .awaitCompletion();

        System.out.println("Output2: " + outputStream2.toString());

        // Stop and remove container
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();

        dockerClient.close();

        System.out.println("bye...");
    }
}
