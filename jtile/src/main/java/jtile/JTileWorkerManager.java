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

    private ExecutorService tileWorkerPool;

    private void zoomTile(TranscoderInput input,
                          Integer deltaXY,
                          Integer nthX,
                          Integer nthY,
                          Integer posX,
                          Integer posY,
                          Integer posZ,
                          File sourceDir,
                          File destinationDir) throws IOException, TranscoderException {
        PNGTranscoder trans = new PNGTranscoder();
        trans.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(256));
        trans.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(256));
        trans.addTranscodingHint(PNGTranscoder.KEY_AOI, new Rectangle(256 + (nthX * deltaXY), 256+(nthY * deltaXY), deltaXY, deltaXY));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(bos);

        trans.transcode(input, output);
        if (bos.size() > 446) {
            Integer scale = (int) Math.pow(2, posZ - 12);
            Integer posXDir = (scale * posX) + nthX;
            Integer posYDir = (scale * posY) + nthY;

            Path destinationPath = Paths.get(destinationDir.getAbsolutePath(), posZ.toString(), posXDir.toString());

            File outputFile = new File(destinationPath.toString());
            outputFile.mkdirs();

            OutputStream outputStream = new FileOutputStream(Paths.get(destinationPath.toString(), posYDir.toString() + ".png").toString());
            bos.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        }

        if ((posZ < 18) && ((posZ < 16) || (bos.size() > 446))) {
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    final Integer newX = x;
                    final Integer newY = y;

                    tileWorkerPool.execute(() -> {
                        try {
                            Path sourcePath = Paths.get(sourceDir.getAbsolutePath(), posX.toString() + "-" + posX.toString() + "-" + posZ.toString() + ".svg");
                            File newSvgFile = new File(sourcePath.toString());

                            if(newSvgFile.exists()) {
                                TranscoderInput newInput = new TranscoderInput(newSvgFile.toURI().toString());
                                zoomTile(newInput, (deltaXY / 2), (nthX * 2 + newX), (nthY * 2 + newY), posX, posY, (posZ + 1), sourceDir, destinationDir);
                                newSvgFile.delete();
                            }
                        } catch (IOException | TranscoderException ex) {
                            System.out.println(ex.getMessage());
                        }
                    });
                }
            }
        }
    }

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

    private void tileWorker(File svgFile, File sourceDir, File destinationDir) {

        String fileName = svgFile.getName().replace(".svg", "");
        String[] position = fileName.split("-");

        Integer posX = Integer.parseInt(position[0]);
        Integer posY = Integer.parseInt(position[1]);
        Integer posZ = Integer.parseInt(position[2]);

        try {
            TranscoderInput input = new TranscoderInput(svgFile.toURI().toString());

            if(posZ < 12) {
                defaultTile(input, posX, posY, posZ, destinationDir);
            } else if(posZ == 12) {
                zoomTile(input, 256, 0, 0, posX, posY, 12, sourceDir, destinationDir);
            }

            svgFile.delete();
        } catch (IOException | TranscoderException ex) {
            System.err.println(ex.getMessage());
        }
    }

    public void startWorkers(File[] filesToRender, File sourceDir, File destinationDir) throws InterruptedException {
        tileWorkerPool = Executors.newFixedThreadPool(128);

        for (File file: filesToRender) {
            System.out.println("Rendering file: " + file.getName());
            tileWorkerPool.execute(() -> tileWorker(file, sourceDir, destinationDir));
        }

        tileWorkerPool.shutdown();
        tileWorkerPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

}
