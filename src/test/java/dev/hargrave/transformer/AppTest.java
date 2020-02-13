/*
 */
package dev.hargrave.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import aQute.bnd.classfile.ClassFile;
import aQute.bnd.classfile.ExceptionsAttribute;
import aQute.bnd.osgi.FileResource;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;

@ExtendWith(SoftAssertionsExtension.class)
public class AppTest {
	private Path tmp;

	@BeforeEach
	public void before(TestInfo info) throws Exception {
		Method testMethod = info.getTestMethod()
			.get();
		tmp = Paths.get("build/tmp/test", testMethod.getDeclaringClass()
			.getName(), testMethod.getName())
			.toAbsolutePath();
		Files.createDirectories(tmp);
	}

	@Test
	@DisplayName("WAR file from https://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/sample.war")
	public void sample_war(SoftAssertions softly) throws Exception {
		URL url = getClass().getResource("/apache-tomcat-appdev-sample.war");
		assertThat(url).isNotNull();
		Path input = Paths.get(url.toURI())
			.toAbsolutePath();
		assertThat(input).isRegularFile();
		Path output = tmp.resolve("sample.war");
		App app = new App();
		app.run("--war", input.toString(), "--output", output.toString());

		assertThat(output).isRegularFile();
		try (Jar outputWar = new Jar(output.toFile()); Jar inputWar = new Jar(input.toFile())) {
			Resource resource = outputWar.getResource("WEB-INF/classes/mypackage/Hello.class");
			assertThat(resource).isNotNull();
			try (DataInputStream din = new DataInputStream(resource.openInputStream())) {
				ClassFile cf = ClassFile.parseClassFile(din);
				softly.assertThat(cf.this_class)
					.isEqualTo("mypackage/Hello");
				softly.assertThat(cf.super_class)
					.isEqualTo("jakarta/servlet/http/HttpServlet");
				softly.assertThat(cf.methods)
					.filteredOn(m -> m.name.equals("doGet"))
					.hasSize(1)
					.allMatch(m -> m.descriptor.equals(
						"(Ljakarta/servlet/http/HttpServletRequest;Ljakarta/servlet/http/HttpServletResponse;)V"))
					.flatExtracting(m -> Arrays.asList(m.attributes))
					.filteredOn(a -> a.name()
						.equals(ExceptionsAttribute.NAME))
					.asInstanceOf(InstanceOfAssertFactories.list(ExceptionsAttribute.class))
					.hasSize(1)
					.flatExtracting(a -> Arrays.asList(a.exceptions))
					.containsExactlyInAnyOrder("jakarta/servlet/ServletException", "java/io/IOException");
				softly.assertThat(outputWar.getResources())
					.hasSameSizeAs(inputWar.getResources());
			}
		}
	}

	@Test
	@DisplayName("Transform a single servlet class")
	public void servlet_class(SoftAssertions softly) throws Exception {
		URL url = getClass().getResource("/test/sample/javaee/JavaEEServlet.class");
		assertThat(url).isNotNull();
		Path input = Paths.get(url.toURI())
			.toAbsolutePath();
		assertThat(input).isRegularFile();
		Path output = tmp.resolve("output.class");
		App app = new App();
		app.run("--class", input.toString(), "--output", output.toString());

		assertThat(output).isRegularFile();
		try (Resource resource = new FileResource(output);
			DataInputStream din = new DataInputStream(resource.openInputStream())) {
			ClassFile cf = ClassFile.parseClassFile(din);
			softly.assertThat(cf.this_class)
				.isEqualTo("test/sample/javaee/JavaEEServlet");
			softly.assertThat(cf.super_class)
				.isEqualTo("jakarta/servlet/http/HttpServlet");
			softly.assertThat(cf.interfaces)
				.containsExactly("jakarta/servlet/Servlet");
			softly.assertThat(cf.methods)
				.filteredOn(m -> m.name.equals("doGet"))
				.hasSize(1)
				.allMatch(m -> m.descriptor
					.equals("(Ljakarta/servlet/http/HttpServletRequest;Ljakarta/servlet/http/HttpServletResponse;)V"))
				.flatExtracting(m -> Arrays.asList(m.attributes))
				.filteredOn(a -> a.name()
					.equals(ExceptionsAttribute.NAME))
				.asInstanceOf(InstanceOfAssertFactories.list(ExceptionsAttribute.class))
				.hasSize(1)
				.flatExtracting(a -> Arrays.asList(a.exceptions))
				.containsExactlyInAnyOrder("jakarta/servlet/ServletException", "java/io/IOException");
			softly.assertThat(cf.fields)
				.filteredOn(f -> f.name.equals("xaResource"))
				.hasSize(1)
				.allMatch(f -> f.descriptor.equals("Ljavax/transaction/xa/XAResource;"));
			softly.assertThat(cf.fields)
				.filteredOn(f -> f.name.equals("transactionSynchronizationRegistry"))
				.hasSize(1)
				.allMatch(f -> f.descriptor.equals("Ljakarta/transaction/TransactionSynchronizationRegistry;"));
		}
	}

}
