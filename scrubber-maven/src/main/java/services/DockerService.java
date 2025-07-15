package services;

public class DockerService implements Runnable {
 
    public DockerService() {
        Thread t = new Thread(this, "DockerService Thread");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread

    }

    public void run() {
        //ACA VA CODIGO PARA DOCKER
        System.out.println("Launching Docker for LocalAI....");
    }
}
