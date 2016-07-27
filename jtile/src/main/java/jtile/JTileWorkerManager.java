package jtile;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JTileWorkerManager {

    private void defaultTile(TranscoderInput input,
                             Integer posX,
                             Integer posY,
                             Integer posZ,
                             File destinationDir) throws IOException, TranscoderException {
        PNGTranscoder trans = new PNGTranscoder();
        trans.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(256));
        trans.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(256));
        trans.addTranscodingHint(PNGTranscoder.KEY_AOI, new Rectangle(512, 512, 256 * (int) (Math.pow(2, 12 - posZ)), 256 * (int) (Math.pow(2, 12 - posZ))));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(bos);

        trans.transcode(input, output);
        if (bos.size() > 446) {
            Path destinationPath = Paths.get(destinationDir.getAbsolutePath(), posZ.toString(), posX.toString());

            File outputFolder = new File(destinationPath.toString());
            outputFolder.mkdirs();

            OutputStream outputStream = new FileOutputStream(Paths.get(destinationPath.toString(), posY.toString() + ".png").toString());
            bos.writeTo(outputStream);

            outputStream.flush();
            outputStream.close();
        }
    }

    private void tileWorker(File svgFile, File destinationDir) {

        String fileName = svgFile.getName().replace(".svg", "");
        String[] position = fileName.split("-");

        Integer posX = Integer.parseInt(position[0]);
        Integer posY = Integer.parseInt(position[1]);
        Integer posZ = Integer.parseInt(position[2]);

        try {
            TranscoderInput input = new TranscoderInput(svgFile.toURI().toURL().toString());
            defaultTile(input, posX, posY, posZ, destinationDir);
            svgFile.delete();
        } catch (IOException | TranscoderException ex) {
            System.err.println(ex.getMessage());
        }
    }

    public void startWorkers(File[] filesToRender, File destinationDir) throws InterruptedException {
        ExecutorService tileWorkerPool = Executors.newFixedThreadPool(64);

        for (File file: filesToRender) {
            tileWorkerPool.execute(() -> tileWorker(file, destinationDir));
        }

        tileWorkerPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

}
