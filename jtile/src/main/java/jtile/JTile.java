package jtile;

import java.io.File;

public class JTile {

    private static JTileWorkerManager tileWorkerManager = new JTileWorkerManager();

    public static void main(String[] args) throws Exception {
        File sourceDir = new File(args[0]);
        File destinationDir = new File(args[1]);

        File[] foundFiles = sourceDir.listFiles(((dir, name) -> name.endsWith(".svg")));

        try {
            tileWorkerManager.startWorkers(foundFiles, sourceDir, destinationDir);
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }

        System.exit(0);
    }

}
