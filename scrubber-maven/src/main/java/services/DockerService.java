package services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Properties;

public class DockerService implements Runnable {

    public static void main(String[] args) {
        new DockerService();
    }

    public DockerService() {
        Thread t = new Thread(this, "DockerService Thread");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread

    }

    public void run() {

        try {
            while(true) {
                if (isDockerRunning()) {
                    System.out.println("Launching Docker for LocalAI....");
                    String containerId = checkIfContainerExists();
                    if (containerId == null || containerId.isEmpty() || !doesContainerExist(containerId)) {
                        containerId = createAndStartContainer();
                    } else {
                        checkISStartedContainerOrStart(containerId);
                    }
                    saveContainerId(containerId);
                } else {
                    System.out.println("Docker is not running or not installed....");
                }
                Thread.sleep(1000*60*10); // Sleep for 10 seconds before checking again
            }
        } catch (Exception e) {
            System.out.println("Docker localai integration error.");
            e.printStackTrace();
        }
    }

    public static boolean isDockerRunning() {
        try {
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(new URI("unix:///var/run/docker.sock"))
                    .sslConfig(null)
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            DockerClient dockerClient = DockerClientBuilder.getInstance().withDockerHttpClient(httpClient).build();

            dockerClient.pingCmd().exec();
            dockerClient.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkISStartedContainerOrStart(String containerId) throws URISyntaxException {

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(new URI("unix:///var/run/docker.sock"))
                .sslConfig(null)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance().withDockerHttpClient(httpClient).build();

        Boolean isRunning=dockerClient.inspectContainerCmd(containerId)
                .exec()
                .getState()
                .getRunning();
        if (!isRunning) {
            // Start container if it is not running
            System.out.println("Container is not running, starting it now...");
            dockerClient.startContainerCmd(containerId).exec();
        }

    }

    public boolean doesContainerExist(String containerId) throws IOException, URISyntaxException {
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(new URI("unix:///var/run/docker.sock"))
                .sslConfig(null)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance().withDockerHttpClient(httpClient).build();

        try {
            dockerClient.inspectContainerCmd(containerId).exec();
            return true; // Container exists
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            return false; // Container does not exist
        } finally {
            dockerClient.close();
        }
    }

    private void saveContainerId(String containerId) throws IOException {
        // ... inside your method
        Properties props = new Properties();
        File f = new File(".."+File.separator+"rtserver"+File.separator+"config"+File.separator+"www-server.properties");
        if (f.exists()) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
                props.load(is);
            }
            props.setProperty("docker.localai.container.id", containerId);
            try (OutputStream os = new FileOutputStream(f)) {
                props.store(os, "Updated by DockerService");
            }
        }
    }

    private String checkIfContainerExists() throws Exception {
        Properties props = new Properties();
        File f = new File (".." + File.separator + "rtserver" + File.separator + "config" + File.separator + "www-server.properties");
        String dockerLocalAIContainerId = null;
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String prop = props.getProperty("docker.localai.container.id");
            if (prop != null) {
                dockerLocalAIContainerId = prop;
            }
        }
        if (dockerLocalAIContainerId != null && !dockerLocalAIContainerId.isEmpty()) {
            System.out.println("Found existing LocalAI container ID: " + dockerLocalAIContainerId);
            return dockerLocalAIContainerId;
        } else {
            System.out.println("No existing LocalAI container found.");
            return null;
        }
    }

    private String createAndStartContainer() throws InterruptedException, IOException, URISyntaxException {
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(new URI("unix:///var/run/docker.sock"))
                .sslConfig(null)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance().withDockerHttpClient(httpClient).build();

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

        System.out.println("LocalAI Docker Install Dependencies: " + outputStream.toString());

        String[] command2 = {"bash", "-c", "/local-ai models install whisper-base"};
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

        System.out.println("LocalAI Docker Install whisper-base model: " + outputStream2.toString());

        dockerClient.close();

        return containerId;
    }

}
