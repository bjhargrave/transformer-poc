/*
 */
package dev.hargrave.transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import aQute.bnd.osgi.FileResource;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.JarResource;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.lib.io.IO;
import aQute.lib.utf8properties.UTF8Properties;
import aQute.libg.uri.URIUtil;
import dev.hargrave.transformer.Transformer.ClassTransformer;
import dev.hargrave.transformer.Transformer.JarTransformer;

public class App {
	private static final String	HELP	= "h";
	private static final String	CLASS	= "c";
	private static final String	JAR		= "j";
	private static final String	WAR		= "w";
	private static final String	OUTPUT	= "o";
	private static final String	RULES	= "r";
	private static final String	VERBOSE	= "v";

	private Options				options;

	public static void main(String[] args) throws Exception {
		App app = new App();
		app.run(args);
	}

	public App() {
		options = new Options();
		OptionGroup input = new OptionGroup();
		input.addOption(Option.builder(HELP)
			.longOpt("help")
			.desc("Display help information")
			.build());
		input.addOption(Option.builder(CLASS)
			.longOpt("class")
			.hasArg()
			.desc("Class FILE to transform")
			.build());
		input.addOption(Option.builder(JAR)
			.longOpt("jar")
			.hasArg()
			.desc("Jar FILE to transform")
			.build());
		input.addOption(Option.builder(WAR)
			.longOpt("war")
			.hasArg()
			.desc("War FILE to transform")
			.build());
		input.setRequired(true);
		options.addOptionGroup(input);
		options.addOption(Option.builder(OUTPUT)
			.longOpt("output")
			.hasArg()
			.desc("Write to FILE instead of stdout")
			.build());
		options.addOption(Option.builder(RULES)
			.longOpt("rules")
			.hasArg()
			.desc("URL of transform rules. Built-in rules are used if not specified")
			.build());
		options.addOption(Option.builder(VERBOSE)
			.longOpt("verbose")
			.desc("Verbose output to stderr")
			.build());
	}

	private CommandLine parse(String[] args) throws ParseException {
		CommandLineParser cliParser = new DefaultParser();
		return cliParser.parse(options, args);
	}

	private void help(PrintStream out) {
		try (PrintWriter pw = new PrintWriter(out)) {
			HelpFormatter formatter = new HelpFormatter();
			pw.println();
			formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH, "transformer [options]", "\nOptions:",
				options, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, "\n", false);
			pw.flush();
		}
	}

	public void run(String... args) throws Exception {
		CommandLine cli;
		try {
			cli = parse(args);
		} catch (ParseException e) {
			System.err.printf("Unable to parse command line options %s%n", e.getMessage());
			help(System.err);
			return;
		}
		if (cli.hasOption(HELP)) {
			help(System.err);
			return;
		}

		URL url;
		if (cli.hasOption(RULES)) {
			String uri = cli.getOptionValue(RULES);
			File cwd = IO.work;
			url = URIUtil.resolve(cwd.toURI(), uri)
				.toURL();
		} else {
			url = getClass().getResource("jakarta-rules.properties");
		}
		UTF8Properties properties = new UTF8Properties();
		try (InputStream in = url.openStream()) {
			properties.load(in);
		}

		Processor rules = new Processor(properties, false);
		PrintStream verbose = cli.hasOption(VERBOSE) ? System.err : new PrintStream(IO.nullStream);
		Transformer transformer = new Transformer(verbose, rules);

		try (OutputStream cli_out = cli.hasOption(OUTPUT) ? IO.outputStream(IO.getFile(cli.getOptionValue(OUTPUT)))
			: null) {
			@SuppressWarnings("resource")
			final OutputStream out = (cli_out != null) ? cli_out : System.out;

			if (cli.hasOption(CLASS)) {
				File file = IO.getFile(cli.getOptionValue(CLASS));
				try (Resource clazz = new FileResource(file)) {
					ByteBuffer buffer = clazz.buffer();
					ClassTransformer classTransformer = transformer.new ClassTransformer();
					Optional<ByteBuffer> transformed = classTransformer.transform(buffer);
					if (transformed.isPresent()) {
						IO.copy(transformed.get(), out);
					} else {
						IO.copy(file, out);
					}
				}
				return;
			}

			if (cli.hasOption(JAR)) {
				File file = IO.getFile(cli.getOptionValue(JAR));
				try (Jar jar = new Jar(file)) {
					jar.setDoNotTouchManifest();
					long jarLastModified = jar.lastModified();
					JarTransformer jarTransformer = transformer.new JarTransformer();
					Optional<Jar> transformed = jarTransformer.transform(jar);
					if (transformed.isPresent()) {
						transformed.get()
							.write(out);
					} else {
						IO.copy(file, out);
					}
				}
				return;
			}

			if (cli.hasOption(WAR)) {
				File file = IO.getFile(cli.getOptionValue(WAR));
				try (Jar fileJar = new Jar(file)) {
					Jar jar = fileJar;
					jar.setDoNotTouchManifest();
					long jarLastModified = jar.lastModified();
					verbose.printf("\n%s\n========\n", "WEB-INF/classes");
					JarTransformer jarTransformer = transformer.new JarTransformer("WEB-INF/classes");
					Optional<Jar> transformed = jarTransformer.transform(jar);
					boolean modified = transformed.isPresent();
					if (modified) {
						jar = transformed.get();
					}
					Map<String, Resource> webInfLib = jar.getDirectory("WEB-INF/lib");
					if (webInfLib != null) {
						for (String resourceName : new ArrayList<>(webInfLib.keySet())) {
							if (resourceName.endsWith(".jar")) {
								Resource resource = jar.getResource(resourceName);
								Jar libJar = Jar.fromResource(resourceName, resource);
								libJar.setDoNotTouchManifest();
								long libJarLastModified = libJar.lastModified();
								verbose.printf("\n========\n%s\n", libJar.getName());
								JarTransformer libJarTransformer = transformer.new JarTransformer();
								Optional<Jar> libJarTransformed = libJarTransformer.transform(libJar);
								if (libJarTransformed.isPresent()) {
									modified = true;
									jar.putResource(resourceName, new JarResource(libJarTransformed.get()));
								}
							}
						}
					}
					if (modified) {
						jar.write(out);
					} else {
						IO.copy(file, out);
					}
				}
				return;
			}
		}
	}
}
