package com.googlecode.jlaunch.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compression {
	public static final void pack200(File in, File out) throws IOException {
		JarFile jar = new JarFile(in);
		try {
			GZIPOutputStream output = new GZIPOutputStream(new FileOutputStream(out));
			try {
				Pack200.newPacker().pack(jar, output);
			} finally {
				output.flush();
				output.close();
			}
		} finally {
			jar.close();
		}
	}
	
	public static final void unpack200(File in, File out) throws IOException {
		GZIPInputStream input = new GZIPInputStream(new FileInputStream(in));
		try {
			JarOutputStream output = new JarOutputStream(new FileOutputStream(out));
			try {
				Pack200.newUnpacker().unpack(input, output);
			} finally {
				output.flush();
				output.close();
			}
		} finally {
			input.close();
		}
	}
	
	public static void main(String[] args) throws Exception {
//		if (args.length != 3) {
//			System.err.println("Compression <pack200/unpack200> <inputjar> <outputjar>");
//			System.exit(1);
//		}
//		File input = new File(args[1]);
//		File output = new File(args[2]);
//		if (args[0].equals("pack200")) {
//			pack200(input, output);
//		} else if (args[0].equals("unpack200")) {
//			unpack200(input, output);
//		}
		
		File original = new File("scala-library.jar");
		File packed = new File("scala-library.jar.pack.gz");
		File unpacked = new File("unpacked.jar");
		pack200(original, packed);
		unpack200(packed, unpacked);
		System.out.println("SIZE: " + original.length() + " / " + unpacked.length());
		
	}
}