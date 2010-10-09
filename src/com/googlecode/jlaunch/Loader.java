package com.googlecode.jlaunch;

import java.awt.Canvas;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

public class Loader extends Thread {
	private JLaunch launch;
	
	private String title;
	private String version = "1.0";
	private String mainClass;
	private String baseURL;
	private List<String> jars;
	private List<String> linux;
	private List<String> mac;
	private List<String> solaris;
	private List<String> freebsd;
	private List<String> windows;
	
	private List<String> downloads;
	
	private File directory;
	
	private ClassLoader classLoader;
	
	public Loader(JLaunch launch) {
		this.launch = launch;
	}
	
	public void run() {
		try {
			// Load configuration data
			loadProperties();
			launch.setProgress(0, "Loaded configuration");
			
			// Configure path
			loadPath();
			launch.setProgress(0, "Configured download path");
			
			// Build file list
			buildFileList();
			launch.setProgress(0, "Build List created");
			
			// Update max progress
			launch.setMaxProgress(downloads.size() * 100);
			
			// Begin download
			downloadFiles();

			unpackFiles();
			
			launch.setMessage("Extracting Native Libraries");
			extractNatives();
			
			launch.setMessage("Installing Application Dependencies");
			updateClassPath();
			
			launch.setMessage("Initializing Application");
			startApplication();
		} catch(Exception exc) {
			exc.printStackTrace();
		}
	}
	
	private void loadProperties() throws IOException {
		URL url = new URL(launch.propertiesURL);
		
		jars = new ArrayList<String>();
		linux = new ArrayList<String>();
		mac = new ArrayList<String>();
		solaris = new ArrayList<String>();
		windows = new ArrayList<String>();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				processLine(line.trim());
			}
		} finally {
			reader.close();
		}
	}
	
	private void processLine(String line) {
		if (line.startsWith("#")) {
			// Ignore, it's a comment
			return;
		} else if (line.length() == 0) {
			// Line is blank
			return;
		}
		String key = line.substring(0, line.indexOf('=')).trim();
		String value = line.substring(line.indexOf('=') + 1).trim();
		if (key.equals("title")) {
			title = value;
		} else if (key.equals("version")) {
			version = value;
		} else if (key.equals("mainClass")) {
			mainClass = value;
		} else if (key.equals("baseURL")) {
			baseURL = value;
		} else if (key.equals("jar")) {
			jars.add(value);
		} else if (key.equals("linux")) {
			linux.add(value);
		} else if (key.equals("mac")) {
			mac.add(value);
		} else if (key.equals("solaris")) {
			solaris.add(value);
		} else if (key.equals("windows")) {
			windows.add(value);
		}
	}

	private void loadPath() throws PrivilegedActionException {
		final boolean prependHost = true;
		
		String path = AccessController.doPrivileged(new PrivilegedExceptionAction<String>() {
			public String run() throws Exception {
				String codebase = "";
				if (prependHost) {
					codebase = launch.getCodeBase().getHost();
					if ((codebase == null) || (codebase.length() == 0)) {
						codebase = "localhost";
					}
					codebase += File.separator;
				}
				return System.getProperty("java.io.tmpdir") + codebase + title + File.separator + version + File.separator;
			}
		});
		
		directory = new File(path);
		System.out.println(directory.getAbsolutePath());
		directory.mkdirs();
	}

	private void buildFileList() {
		downloads = new ArrayList<String>();
		downloads.addAll(jars);
		String os = System.getProperty("os.name");
		if (os.startsWith("Win")) {
			downloads.addAll(windows);
		} else if (os.startsWith("Linux")) {
			downloads.addAll(linux);
		} else if (os.startsWith("Mac")) {
			downloads.addAll(mac);
		} else if ((os.startsWith("Solaris")) || (os.startsWith("SunOS"))) {
			downloads.addAll(solaris);
		} else if (os.startsWith("FreeBSD")) {
			downloads.addAll(freebsd);
		}
	}

	private void downloadFiles() throws IOException {
		int progress = 0;
		for (int index = 0; index < downloads.size(); index++) {
			String filename = downloads.get(index);
			launch.setProgress((progress * 100), "Initiating Download: " + filename + " (" + (index + 1) + " of " + downloads.size() + ")");
			
			File local = new File(directory, filename);
			URL url = new URL(baseURL + filename);
			URLConnection connection = url.openConnection();
			int contentLength = connection.getContentLength();
			BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
			BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(local));
			byte[] b = new byte[512];
			int len;
			int written = 0;
			while ((len = input.read(b)) != -1) {
				output.write(b, 0, len);
				written += len;
				int percent = Math.round(((float)written / (float)contentLength) * 100);
				launch.setProgress((progress * 100) + percent, "Downloading " + filename + " (" + (index + 1) + " of " + downloads.size() + ")");
			}
			output.flush();
			output.close();
			try {
				input.close();
			} catch(Exception exc) {
				exc.printStackTrace();
			}
			
			progress++;
		}
	}
	
	private void unpackFiles() throws IOException {
		for (int index = 0; index < downloads.size(); index++) {
			String filename = downloads.get(index);
			if (filename.endsWith("pack.gz")) {
				launch.setMessage("Unpacking " + filename);
				File packgz = new File(directory, filename);
				File pack = new File(directory, filename.substring(0, filename.length() - 3));
				File jar = new File(directory, filename.substring(0, filename.length() - 8));
				ungzip(packgz, pack);
				unpack(pack, jar);
			}
		}
	}

	private void extractNatives() throws IOException {
		List<String> natives = null;
		String os = System.getProperty("os.name");
		if (os.startsWith("Win")) {
			natives = windows;
		} else if (os.startsWith("Linux")) {
			natives = linux;
		} else if (os.startsWith("Mac")) {
			natives = mac;
		} else if ((os.startsWith("Solaris")) || (os.startsWith("SunOS"))) {
			natives = solaris;
		} else if (os.startsWith("FreeBSD")) {
			natives = freebsd;
		}
		File nativeFolder = new File(directory, "natives");
		nativeFolder.mkdirs();
		
		for (String filename : natives) {
			if (filename.endsWith("pack.gz")) {		// Remove packed info
				filename = filename.substring(0, filename.length() - 8);
			}
			File nativeFile = new File(directory, filename);
			JarFile jarFile = new JarFile(nativeFile, true);
			
			Enumeration<JarEntry> entities = jarFile.entries();
			while (entities.hasMoreElements()) {
				JarEntry entry = entities.nextElement();
				if ((entry.isDirectory()) || (entry.getName().indexOf('/') != -1)) {
					continue;
				}
				File f = new File(nativeFolder, entry.getName());
				if (f.exists()) {
					if (!f.delete()) {
						continue;		// Can't delete, so don't bother
					}
				}
				
				InputStream input = jarFile.getInputStream(jarFile.getEntry(entry.getName()));
				OutputStream output = new FileOutputStream(f);
				byte[] b = new byte[512];
				int len;
				while ((len = input.read(b)) != -1) {
					launch.setMessage("Extracting Native Libraries: " + f.getName());
					
					output.write(b, 0, len);
				}
				
				input.close();
				output.flush();
				output.close();
			}
			
			jarFile.close();
			nativeFile.delete();
		}
	}
	
	private void updateClassPath() throws MalformedURLException {
		URL[] urls = new URL[jars.size()];
		for (int index = 0; index < jars.size(); index++) {
			String filename = jars.get(index);
			if (filename.endsWith("pack.gz")) {		// Remove packed info
				filename = filename.substring(0, filename.length() - 8);
			}
			urls[index] = new File(directory, filename).toURI().toURL();
		}
		
		classLoader = new URLClassLoader(urls) {
			protected PermissionCollection getPermissions(CodeSource codeSource) {
				PermissionCollection perms = null;
				try {
					Method method = SecureClassLoader.class.getDeclaredMethod("getPermissions", new Class[] {CodeSource.class});
					method.setAccessible(true);
					perms = (PermissionCollection)method.invoke(getClass().getClassLoader(), new Object[] {codeSource});
					
					String host = launch.getCodeBase().getHost();
					if ((host != null) && (host.length() > 0)) {
						perms.add(new SocketPermission(host, "action"));
					} else if (codeSource.getLocation().getProtocol().equals("file")) {
						String path = codeSource.getLocation().getFile().replace('/', File.separatorChar);
						perms.add(new FilePermission(path, "read"));
					}
				} catch(Exception exc) {
					exc.printStackTrace();
				}
				return perms;
			}
		};
		
//		unloadNatives();

		System.setProperty("java.library.path", directory.getAbsolutePath() + File.separator + "natives");
		System.setProperty("org.lwjgl.librarypath", directory.getAbsolutePath() + File.separator + "natives");
		System.setProperty("net.java.games.input.librarypath", directory.getAbsolutePath() + File.separator + "natives");
	}
	
	private void startApplication() throws ClassNotFoundException, InstantiationException, IllegalAccessException, SecurityException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException {
		Class<?> c = classLoader.loadClass(mainClass);

		// See if there's a createCanvas method
		try {
			Method create = c.getMethod("createCanvas", new Class[0]);
			if (Modifier.isStatic(create.getModifiers())) {
				Canvas canvas = (Canvas)create.invoke(null, new Object[0]);
				launch.setCanvas(canvas);
				return;
			}
		} catch(NoSuchMethodException exc) {
		}
		
		// See if there's a static main method
		try {
			Method main = c.getMethod("main", new Class[] {String[].class});
			if (Modifier.isStatic(main.getModifiers())) {
				main.invoke(null, new Object[] {new String[0]});
				return;
			}
		} catch(NoSuchMethodException exc) {
		}
	}
	
	public static final void ungzip(File in, File out) throws IOException {
		GZIPInputStream input = new GZIPInputStream(new FileInputStream(in));
		FileOutputStream output = new FileOutputStream(out);
		int len;
		byte[] buf = new byte[512];
		while ((len = input.read(buf)) != -1) {
			output.write(buf, 0, len);
		}
		output.flush();
		output.close();
		input.close();
	}
	
	public static final void unpack(File in, File out) throws IOException {
		JarOutputStream output = new JarOutputStream(new FileOutputStream(out));
		Pack200.newUnpacker().unpack(in, output);
		output.flush();
		output.close();
	}
}