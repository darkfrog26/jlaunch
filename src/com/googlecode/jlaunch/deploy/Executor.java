package com.googlecode.jlaunch.deploy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Executor {
	public static final boolean exec(List<String> list, AtomicReference<Process> process) throws InterruptedException, IOException {
		StringBuilder b = new StringBuilder();
		for (String s : list) {
			b.append(s);
			b.append(' ');
		}
		System.out.println(b.toString());
		ProcessBuilder pb = new ProcessBuilder(list);
		Process p = pb.start();
		if (process != null) process.set(p);
		String line;
		BufferedReader r1 = new BufferedReader(new InputStreamReader(p.getInputStream()));
		while ((line = r1.readLine()) != null) {
			System.out.println(line);
		}
		r1.close();
		BufferedReader r2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		while ((line = r2.readLine()) != null) {
			System.err.println(line);
		}
		r2.close();
		
		int response = p.waitFor();
		
		return response == 0;
	}
}
