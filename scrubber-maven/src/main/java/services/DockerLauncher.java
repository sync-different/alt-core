package services;

public class DockerLauncher implements Runnable {

    boolean bTerminated = false;
    DockerService ds = null;

    public DockerLauncher() {
        Thread t = new Thread(this, "DockerLauncher Thread");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread

    }
    public void run() {
        ds = new DockerService();
        //ds.run();
    }   

    public void terminate() {
        ds.terminate();
    }
}
