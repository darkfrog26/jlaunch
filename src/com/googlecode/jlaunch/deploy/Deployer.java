package com.googlecode.jlaunch.deploy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Deployer {
	public static final boolean UsePack = true;
	
	private static final String BuildTemplate = loadTemplate("template.build.xml");
	private static final String RepackTemplate = loadTemplate("template.repack.xml");
	private static final String SignJarTemplate = loadTemplate("template.signjars.xml");
	private static final String Pack200Template = loadTemplate("template.pack200.xml");
	private static final String HTMLTemplate = loadTemplate("template.jlaunch.html");
	
	private String buildXML = BuildTemplate;
	private File buildFile;
	private File baseDirectory;
	private File dist;
	private Element configuration;
	
	private List<File> natives = new ArrayList<File>();
	
	private void rt(String key, String value) {
		buildXML = buildXML.replaceAll("[$]" + key, value);
	}
	
	public void loadConfiguration(File configFile) throws ParserConfigurationException, SAXException, IOException {
		baseDirectory = configFile.getParentFile();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(configFile);
		configuration = document.getDocumentElement();
		
		// Replace title
		rt("title", elemt(configuration, "title"));
		rt("preview", elemt(configuration, "preview"));
		
		// Make sure dist exists
		dist = new File(baseDirectory, "dist");
		dist.mkdirs();
	}
	
	public void yguard() throws IOException {
		// Configure main-class exclusions
		StringBuilder b = new StringBuilder();
		for (Element e : elems(configuration, "main-class")) {
			b.append("<method name=\"void main(java.lang.String[])\" class=\"" + e.getTextContent().trim() + "\"/>\r\n");
			b.append("<method name=\"java.awt.Canvas createCanvas()\" class=\"" + e.getTextContent().trim() + "\"/>\r\n");
		}
		rt("keepMainClass", b.toString());
		
		// Make sure obfuscated directory exists
		new File(baseDirectory, "obfuscated").mkdirs();
	}
	
	public void generateNativeJars() throws FileNotFoundException, IOException {
		String natives = elemt(configuration, "natives");
		if (natives != null) {
			File nativesOutput = new File(baseDirectory, "nativejars");
			nativesOutput.mkdirs();
			
			File directory = new File(baseDirectory, natives);
			File linux = new File(directory, "linux");
			File macosx = new File(directory, "macosx");
			File solaris = new File(directory, "solaris");
			File windows = new File(directory, "windows");
			createNativeJar(linux, new File(nativesOutput, "linux.jar"));
			createNativeJar(macosx, new File(nativesOutput, "macosx.jar"));
			createNativeJar(solaris, new File(nativesOutput, "solaris.jar"));
			createNativeJar(windows, new File(nativesOutput, "windows.jar"));
		}
	}
	
	private void createNativeJar(File directory, File jar) throws FileNotFoundException, IOException {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files.length > 0) {
				JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar));
				try {
					byte[] buf = new byte[512];
					int len;
					for (File f : files) {
						JarEntry entry = new JarEntry(f.getName());
						jos.putNextEntry(entry);
						FileInputStream fis = new FileInputStream(f);
						try {
							while ((len = fis.read(buf)) != -1) {
								jos.write(buf, 0, len);
							}
						} finally {
							fis.close();
						}
					}
				} finally {
					jos.flush();
					jos.close();
				}
				natives.add(jar);
			}
		}
	}
	
	public void repack() {
		// Configure jar repacking
		StringBuilder b = new StringBuilder();
		Element jars = elem(configuration, "jars");
		for (Element e : elems(jars, "jar")) {
			String jar = e.getTextContent().trim();
			String repack = RepackTemplate;
			repack = repack.replaceAll("[$]jar", "obfuscated/" + jar);
			repack = repack.replaceAll("[$]repackjar", "repack/" + jar);
			b.append(repack);
			b.append("\r\n");
		}
		// repack native jars
		for (File f : natives) {
			String jar = f.getName();
			String repack = RepackTemplate;
			repack = repack.replaceAll("[$]jar", "nativejars/" + jar);
			repack = repack.replaceAll("[$]repackjar", "repack/" + jar);
			b.append(repack);
			b.append("\r\n");
		}
		// repack jlaunch
		String repack = RepackTemplate;
		repack = repack.replaceAll("[$]jar", elemt(configuration, "jlaunch"));
		repack = repack.replaceAll("[$]repackjar", "repack/jlaunch.jar");
		b.append(repack);
		b.append("\r\n");
		
		rt("repack", b.toString());
		
		// Make sure repack directory exists
		new File(baseDirectory, "repack").mkdirs();
	}
	
	public void sign() {
		// Configure generating keystore
		Element signature = elem(configuration, "signature");
		rt("keystore", elemt(signature, "keystore"));
		rt("alias", elemt(signature, "alias"));
		rt("keypass", elemt(signature, "keypass"));
		rt("storepass", elemt(signature, "storepass"));
		rt("cn", elemt(signature, "cn"));
		rt("ou", elemt(signature, "ou"));
		rt("o", elemt(signature, "o"));
		rt("c", elemt(signature, "c"));
		
		// Configure jar signing
		StringBuilder b = new StringBuilder();
		Element jars = elem(configuration, "jars");
		for (Element e : elems(jars, "jar")) {
			String jar = e.getTextContent().trim();
			String signjar = SignJarTemplate;
			signjar = signjar.replaceAll("[$]keystore", elemt(signature, "keystore"));
			signjar = signjar.replaceAll("[$]alias", elemt(signature, "alias"));
			signjar = signjar.replaceAll("[$]keypass", elemt(signature, "keypass"));
			signjar = signjar.replaceAll("[$]storepass", elemt(signature, "storepass"));
			signjar = signjar.replaceAll("[$]jar", "repack/" + jar);
			signjar = signjar.replaceAll("[$]signedjar", "signed/" + jar);
			b.append(signjar);
			b.append("\r\n");
		}
		
		// Sign native jars
		for (File f : natives) {
			String jar = f.getName();
			String signjar = SignJarTemplate;
			signjar = signjar.replaceAll("[$]keystore", elemt(signature, "keystore"));
			signjar = signjar.replaceAll("[$]alias", elemt(signature, "alias"));
			signjar = signjar.replaceAll("[$]keypass", elemt(signature, "keypass"));
			signjar = signjar.replaceAll("[$]storepass", elemt(signature, "storepass"));
			signjar = signjar.replaceAll("[$]jar", "repack/" + jar);
			signjar = signjar.replaceAll("[$]signedjar", "signed/" + jar);
			b.append(signjar);
			b.append("\r\n");
		}
		
		// Sign jlaunch jar
		String signjar = SignJarTemplate;
		signjar = signjar.replaceAll("[$]keystore", elemt(signature, "keystore"));
		signjar = signjar.replaceAll("[$]alias", elemt(signature, "alias"));
		signjar = signjar.replaceAll("[$]keypass", elemt(signature, "keypass"));
		signjar = signjar.replaceAll("[$]storepass", elemt(signature, "storepass"));
		signjar = signjar.replaceAll("[$]jar", "repack/jlaunch.jar");
		signjar = signjar.replaceAll("[$]signedjar", "signed/jlaunch.jar");
		b.append(signjar);
		b.append("\r\n");
		
		rt("signjars", b.toString());
		
		// Make sure signed directory exists
		new File(baseDirectory, "signed").mkdirs();
	}
	
	public void pack() {
		// Configure jar packing
		StringBuilder b = new StringBuilder();
		Element jars = elem(configuration, "jars");
		for (Element e : elems(jars, "jar")) {
			String jar = e.getTextContent().trim();
			String pack200 = Pack200Template;
			pack200 = pack200.replaceAll("[$]jar", "signed/" + jar);
			pack200 = pack200.replaceAll("[$]packedjar", "pack200/" + jar + ".pack.gz");
			b.append(pack200);
			b.append("\r\n");
		}
		// pack native jars
		for (File f : natives) {
			String jar = f.getName();
			String pack200 = Pack200Template;
			pack200 = pack200.replaceAll("[$]jar", "signed/" + jar);
			pack200 = pack200.replaceAll("[$]packedjar", "pack200/" + jar + ".pack.gz");
			b.append(pack200);
			b.append("\r\n");
		}
		// pack jlaunch
		String pack200 = Pack200Template;
		pack200 = pack200.replaceAll("[$]jar", "signed/jlaunch.jar");
		pack200 = pack200.replaceAll("[$]packedjar", "pack200/jlaunch.jar.pack.gz");
		b.append(pack200);
		b.append("\r\n");
		
		rt("pack200", b.toString());
		
		// Make sure pack200 directory exists
		new File(baseDirectory, "pack200").mkdirs();
	}
	
	public void generateProperties() throws IOException {
		File props = new File(baseDirectory, "jlaunch.properties");
		BufferedWriter writer = new BufferedWriter(new FileWriter(props));
		try {
			wp(writer, "title", elemt(configuration, "title"));
			wp(writer, "version", elemt(configuration, "version"));
			wp(writer, "baseURL", elemt(configuration, "baseURL"));
			Element jars = elem(configuration, "jars");
			for (Element e : elems(jars, "jar")) {
				String jar = e.getTextContent().trim();
				if (UsePack) {
					jar += ".pack.gz";
				}
				wp(writer, "jar", jar);
			}
			for (File f : natives) {
				String filename = f.getName();
				if (UsePack) {
					filename += ".pack.gz";
				}
				if (f.getName().startsWith("linux")) {
					wp(writer, "linux", filename);
				} else if (f.getName().startsWith("mac")) {
					wp(writer, "mac", filename);
				} else if (f.getName().startsWith("solaris")) {
					wp(writer, "solaris", filename);
				} else if (f.getName().startsWith("windows")) {
					wp(writer, "windows", filename);
				}
			}
			wp(writer, "mainClass", elemt(configuration, "main-class"));
		} finally {
			writer.flush();
			writer.close();
		}
	}
	
	private void wp(BufferedWriter writer, String key, String value) throws IOException {
		writer.write(key);
		writer.write(" = ");
		writer.write(value);
		writer.write("\r\n");
	}
	
	public void generateHTML() throws IOException {
		String html = HTMLTemplate;
		html = html.replaceAll("[$]title", elemt(configuration, "title"));
		html = html.replaceAll("[$]baseURL", elemt(configuration, "baseURL"));
		html = html.replaceAll("[$]width", elemt(configuration, "width"));
		html = html.replaceAll("[$]height", elemt(configuration, "height"));
		html = html.replaceAll("[$]preview", elemt(configuration, "preview"));
		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(baseDirectory, "jlaunch.html")));
		try {
			writer.write(html);
		} finally {
			writer.flush();
			writer.close();
		}
	}
	
	public void deploy() {
	}
	
	public void generateAnt() throws IOException {
		buildFile = new File(baseDirectory, "deploy.build.xml");
		BufferedWriter writer = new BufferedWriter(new FileWriter(buildFile));
		try {
			writer.write(buildXML);
		} finally {
			writer.flush();
			writer.close();
		}
	}
	
	public void execute() {
		// Create project
		Project p = new Project();
		
		// Configure logging to output
		DefaultLogger consoleLogger = new DefaultLogger();
		consoleLogger.setErrorPrintStream(System.err);
		consoleLogger.setOutputPrintStream(System.out);
		consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
		p.addBuildListener(consoleLogger);
		
		// Configure and run task
		p.setUserProperty("ant.file", buildFile.getAbsolutePath());
		p.init();
		ProjectHelper helper = ProjectHelper.getProjectHelper();
		p.addReference("ant.projecthelper", helper);
		helper.parse(p, buildFile);
		p.executeTarget("deploy");
	}
	
	public static void main(String[] args) throws Exception {
		Deployer d = new Deployer();
		if (args.length != 1) {
			System.err.println("Usage: java -jar jlaunch.deployer.jar <configuration file>");
			System.exit(1);
		}
		d.loadConfiguration(new File(args[0]));
		d.yguard();
		d.generateNativeJars();
		d.repack();
		d.sign();
		d.pack();
		d.generateProperties();
		d.generateHTML();
		d.deploy();
		d.generateAnt();
		d.execute();
	}
	
	private static final Element elem(Element parent, String name) {
		NodeList nodes = parent.getElementsByTagName(name);
		if (nodes.getLength() > 0) {
			return (Element)nodes.item(0);
		} else {
			return null;
		}
	}
	
	private static final String elemt(Element parent, String name) {
		Element e = elem(parent, name);
		if (e != null) {
			return e.getTextContent().trim();
		}
		return null;
	}
	
	private static final List<Element> elems(Element parent, String name) {
		NodeList nodes = parent.getElementsByTagName(name);
		List<Element> list = new ArrayList<Element>();
		for (int index = 0; index < nodes.getLength(); index++) {
			list.add((Element)nodes.item(index));
		}
		return list;
	}
	
	private static final String loadTemplate(String filename) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(Deployer.class.getClassLoader().getResourceAsStream("resource/" + filename)));
			try {
				StringBuilder b = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					b.append(line);
					b.append("\r\n");
				}
				
				return b.toString();
			} finally {
				reader.close();
			}
		} catch(Exception exc) {
			exc.printStackTrace();
			System.exit(1);
			return null;
		}
	}
}