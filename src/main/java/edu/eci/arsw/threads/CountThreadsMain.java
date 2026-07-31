/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.eci.arsw.threads;

/**
 *
 * @author hcadavid
 */
public class CountThreadsMain {
    
    public static void main(String a[]){
        //      Punto 2
        CountThread t2 = new CountThread(0, 99);
        CountThread t3 = new CountThread(99, 199);
        CountThread t4 = new CountThread(200, 299);
        /*
        t2.start();
        t3.start();
        t4.start();
        */
        t2.run();
        t3.run();
        t4.run();

    }
    
}
