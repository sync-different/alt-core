package services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import utils.Appendage;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Calendar;
import java.util.Properties;
import java.util.Date;


public class DockerService implements Runnable {

    static String appendage = "";
    static String appendageRW = "";
    public static boolean bConsole = true;
    
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [SC.DockerService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

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
            Appendage app = new Appendage();
            appendage = app.getAppendage();
            appendageRW = app.getAppendageRW();

            pw("****  DockerService appendage: " + appendage);

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
        //File f = new File(appendage + ".." + File.separator+"scrubber"+ File.separator + "config" + File.separator+"www-docker.properties");
        pw("****  saveContainerId appendage: " + appendage);
        File f = new File (appendage + "../scrubber/config/www-docker.properties");
        if (f.exists()) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
                props.load(is);
            }
            props.setProperty("docker.localai.container.id", containerId);
            try (OutputStream os = new FileOutputStream(f)) {
                props.store(os, "Updated by DockerService");
            }
        } else {
            pw("WARNING: Could not save Docker container ID to properties file because it does not exist: " + f.getAbsolutePath());
        }
    }

    private String getModelName() throws IOException {
        Properties props = new Properties();
        //File f = new File (appendage + ".." + File.separator + "scrubber" + File.separator + "config" + File.separator + "www-docker.properties");
        pw ("****  getModelName appendage: " + appendage);
        File f = new File (appendage + "../scrubber/config/www-docker.properties");
        String dockerLocalAIModelName = null;
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String prop = props.getProperty("docker.localai.modelname");
            if (prop != null) {
                dockerLocalAIModelName = prop;
            }
        } else {
            pw("WARNING: Could not load Docker LocalAI model name from properties file because it does not exist: " + f.getAbsolutePath());
        }
        return dockerLocalAIModelName;
    }
    
    private String getImageName() throws IOException {
        Properties props = new Properties();
        //File f = new File (appendage + ".." + File.separator + "scrubber" + File.separator + "config" + File.separator + "www-docker.properties");
        pw("****  getImageName appendage: " + appendage);
        File f = new File (appendage + "../scrubber/config/www-docker.properties");
        String dockerLocalAIImageName = null;
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String prop = props.getProperty("docker.localai.imagename");
            if (prop != null) {
                dockerLocalAIImageName = prop;
            }
        } else {
            pw("WARNING: Could not load Docker LocalAI image name from properties file because it does not exist: " + f.getAbsolutePath());
        }
        return dockerLocalAIImageName;
    }
    
    private String checkIfContainerExists() throws Exception {
        Properties props = new Properties();
        //File f = new File (appendage + ".." + File.separator + "scrubber" + File.separator + "config" + File.separator + "www-docker.properties");
        pw("****  checkIfContainerExists appendage: " + appendage);
        File f = new File (appendage + "../scrubber/config/www-docker.properties");
        String dockerLocalAIContainerId = null;
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String prop = props.getProperty("docker.localai.container.id");
            if (prop != null) {
                dockerLocalAIContainerId = prop;
            }
        } else {
            pw("WARNING: Could not load Docker LocalAI container ID from properties file because it does not exist: " + f.getAbsolutePath());   
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

        String sImageName = getImageName();
        if (sImageName == null || sImageName.isEmpty()) {
            sImageName = "localai/localai:v3.1.1"; // Default image name if not specified
        }
        System.out.println("****  LocalAI Image : " + sImageName);

        // Pull image if not present
        pw("Pulling Docker image: " + sImageName);
        dockerClient.pullImageCmd(sImageName).start().awaitCompletion();
        pw("After Pulling Docker image: " + sImageName);

        ExposedPort port= ExposedPort.tcp(8080);
        Ports ports= new Ports();
        ports.bind(port, Ports.Binding.bindPort(8080));
        
        // Create container
        CreateContainerResponse container = dockerClient.createContainerCmd(sImageName)
                .withCmd("sleep","500000")
                .withExposedPorts(port)
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(ports).withAutoRemove(true))
                .exec();

        String containerId = container.getId();

        // Start container
        dockerClient.startContainerCmd(containerId).exec();

        // Exec shell command inside the container
        //String[] command = {"bash", "-c", "apt update &&  apt install -y ffmpeg &&  apt -y install llvm &&  apt -y install curl && cd / && curl https://localai.io/install.sh | sh && local-ai models install whisper-base &&  local-ai run whisper-base"};
        //String[] command = {"bash", "-c", "apt update && apt -y install curl && cd / && curl https://localai.io/install.sh | sh"};
        //String[] command = {"bash", "-c", "apt update && apt -y install curl && apt -y install llvm && curl https://localai.io/install.sh | sh"};
        String[] command = {"bash", "-c", "apt update | sh"};
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

        String sModelName = getModelName();
        if (sModelName == null || sModelName.isEmpty()) {
            sModelName = "whisper-base"; // Default model name if not specified
        }
        System.out.println("****  LocalAI Model : " + sModelName);
        String[] command2 = {"bash", "-c", "/local-ai models install " + sModelName};
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
