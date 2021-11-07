/**
 * NBodyBH.java
 *
 * Reads in a universe of N bodies from stdin, and performs an
 * N-Body simulation in O(N log N) using the Barnes-Hut algorithm.
 *
 * Compilation:  javac NBodyBH.java
 * Execution:    java NBodyBH < inputs/[filename].txt
 * Dependencies: BHTree.java Body.java Quad.java StdDraw.java
 * Input files:  ./inputs/*.txt
 *
 * @author chindesaurus
 * @version 1.00
 */

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NBodyBH {

    static boolean isParallelExecutor = true;
    static boolean isParallelStream = false;
    static boolean isSingle = false;
    static boolean enableDisplay = false;
    static int simulationDuration = 100;
    static int threadNum = 4;

    public static void main(String[] args) {

        ExecutorService pool = Executors.newFixedThreadPool(threadNum);
        
        // for reading from stdin
        Scanner console = new Scanner(System.in);

        Instant timeStarted = Instant.now();
        Instant timeEnding = timeStarted.plus(Duration.ofSeconds(simulationDuration));

        final double dt = 0.1;                     // time quantum
        int N = console.nextInt();                 // number of particles
        double radius = console.nextDouble();      // radius of universe

        if (enableDisplay) {
            // turn on animation mode and rescale coordinate system
            StdDraw.show(0);
            StdDraw.setXscale(-radius, +radius);
            StdDraw.setYscale(-radius, +radius);
        }

        // read in and initialize bodies
        Body[] bodies = new Body[N];               // array of N bodies
        for (int i = 0; i < N; i++) {
            double px   = console.nextDouble();
            double py   = console.nextDouble();
            double vx   = console.nextDouble();
            double vy   = console.nextDouble();
            double mass = console.nextDouble();
            int red     = console.nextInt();
            int green   = console.nextInt();
            int blue    = console.nextInt();
            Color color = new Color(red, green, blue);
            bodies[i] = new Body(px, py, vx, vy, mass, color);
        }

        double t = 0.0;

        // simulate the universe
        for (t = 0.0; true; t = t + dt) {

            Quad quad = new Quad(0, 0, radius * 2);
            BHTree tree = new BHTree(quad);

            final CountDownLatch latch = new CountDownLatch(threadNum);
            for (int i = 0; i < threadNum; i++) {
                final int offsetStart = (N / threadNum) * i;
                final int offsetEnd = ((N / threadNum) * (i+1));
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (latch) {
                            latch.countDown();
                        }
                        synchronized (tree) {
                            for (int j = offsetStart; j < offsetEnd; j++) {
                                if (bodies[j].in(quad))
                                    tree.insert(bodies[j]);
                            }
                        }
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            final CountDownLatch latch2 = new CountDownLatch(threadNum);
            for (int i = 0; i < threadNum; i++) {
                final int offsetStart = (N / threadNum) * i;
                final int offsetEnd = ((N / threadNum) * (i+1));
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (latch2) {
                            latch2.countDown();
                        }
                        synchronized (tree) {
                            for (int j = offsetStart; j < offsetEnd; j++) {
                                bodies[j].resetForce();
                                tree.updateForce(bodies[j]);
                                bodies[j].update(dt);
                            }
                        }
                    }
                });
            }
            try {
                latch2.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (enableDisplay) {
                System.out.println(t);
                // draw the bodies
                StdDraw.clear(StdDraw.BLACK);
                for (int i = 0; i < N; i++)
                    bodies[i].draw();

                StdDraw.show(10);
            }

            if (Instant.now().isAfter(timeEnding)) {
                break;
            }
        }

        System.out.println("time passed: " + t);
        System.exit(0);
    }
}
