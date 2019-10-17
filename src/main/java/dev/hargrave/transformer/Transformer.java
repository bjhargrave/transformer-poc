package dev.hargrave.transformer;

import static aQute.bnd.classfile.ConstantPool.CONSTANT_Class;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Double;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Dynamic;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Fieldref;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Float;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Integer;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_InterfaceMethodref;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_InvokeDynamic;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Long;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_MethodHandle;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_MethodType;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Methodref;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Module;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_NameAndType;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Package;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_String;
import static aQute.bnd.classfile.ConstantPool.CONSTANT_Utf8;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.io.DataInput;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Formatter;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;

import aQute.bnd.classfile.AnnotationDefaultAttribute;
import aQute.bnd.classfile.AnnotationInfo;
import aQute.bnd.classfile.AnnotationsAttribute;
import aQute.bnd.classfile.Attribute;
import aQute.bnd.classfile.ClassFile;
import aQute.bnd.classfile.CodeAttribute;
import aQute.bnd.classfile.CodeAttribute.ExceptionHandler;
import aQute.bnd.classfile.ConstantPool.ClassInfo;
import aQute.bnd.classfile.ConstantPool.MethodTypeInfo;
import aQute.bnd.classfile.ConstantPool.NameAndTypeInfo;
import aQute.bnd.classfile.ConstantPool.StringInfo;
import aQute.bnd.classfile.ElementValueInfo;
import aQute.bnd.classfile.ElementValueInfo.EnumConst;
import aQute.bnd.classfile.ElementValueInfo.ResultConst;
import aQute.bnd.classfile.EnclosingMethodAttribute;
import aQute.bnd.classfile.ExceptionsAttribute;
import aQute.bnd.classfile.FieldInfo;
import aQute.bnd.classfile.InnerClassesAttribute;
import aQute.bnd.classfile.InnerClassesAttribute.InnerClass;
import aQute.bnd.classfile.LocalVariableTableAttribute;
import aQute.bnd.classfile.LocalVariableTableAttribute.LocalVariable;
import aQute.bnd.classfile.LocalVariableTypeTableAttribute;
import aQute.bnd.classfile.LocalVariableTypeTableAttribute.LocalVariableType;
import aQute.bnd.classfile.MemberInfo;
import aQute.bnd.classfile.MethodInfo;
import aQute.bnd.classfile.ModuleAttribute;
import aQute.bnd.classfile.ModulePackagesAttribute;
import aQute.bnd.classfile.NestHostAttribute;
import aQute.bnd.classfile.NestMembersAttribute;
import aQute.bnd.classfile.ParameterAnnotationInfo;
import aQute.bnd.classfile.ParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeInvisibleTypeAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleParameterAnnotationsAttribute;
import aQute.bnd.classfile.RuntimeVisibleTypeAnnotationsAttribute;
import aQute.bnd.classfile.SignatureAttribute;
import aQute.bnd.classfile.StackMapTableAttribute;
import aQute.bnd.classfile.StackMapTableAttribute.AppendFrame;
import aQute.bnd.classfile.StackMapTableAttribute.FullFrame;
import aQute.bnd.classfile.StackMapTableAttribute.ObjectVariableInfo;
import aQute.bnd.classfile.StackMapTableAttribute.SameLocals1StackItemFrame;
import aQute.bnd.classfile.StackMapTableAttribute.SameLocals1StackItemFrameExtended;
import aQute.bnd.classfile.StackMapTableAttribute.StackMapFrame;
import aQute.bnd.classfile.StackMapTableAttribute.VerificationTypeInfo;
import aQute.bnd.classfile.TypeAnnotationInfo;
import aQute.bnd.classfile.TypeAnnotationsAttribute;
import aQute.bnd.classfile.builder.ClassFileBuilder;
import aQute.bnd.classfile.builder.MutableConstantPool;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.osgi.EmbeddedResource;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.signatures.ArrayTypeSignature;
import aQute.bnd.signatures.BaseType;
import aQute.bnd.signatures.ClassSignature;
import aQute.bnd.signatures.ClassTypeSignature;
import aQute.bnd.signatures.FieldSignature;
import aQute.bnd.signatures.JavaTypeSignature;
import aQute.bnd.signatures.MethodSignature;
import aQute.bnd.signatures.ReferenceTypeSignature;
import aQute.bnd.signatures.Result;
import aQute.bnd.signatures.SimpleClassTypeSignature;
import aQute.bnd.signatures.ThrowsSignature;
import aQute.bnd.signatures.TypeArgument;
import aQute.bnd.signatures.TypeParameter;
import aQute.bnd.signatures.TypeVariableSignature;
import aQute.lib.io.ByteBufferDataInput;
import aQute.lib.io.ByteBufferDataOutput;
import aQute.lib.io.IO;
import aQute.lib.io.IOConstants;
import aQute.lib.strings.Strings;
import aQute.libg.glob.PathSet;

public class Transformer {
	static final String					metainfServices			= "META-INF/services/";
	static final int					metainfServicesLength	= metainfServices.length();
	static final Predicate<String>		metainfServicesFilter	= new PathSet(metainfServices + "*").matches();
	private final Processor				rules;
	private final Map<String, String>	packageRenames;
	private final PrintStream			verbose;
	private final Map<String, String>	binaryTypes				= new HashMap<>();
	private final Map<String, String>	signatures				= new HashMap<>();
	private final Map<String, String>	descriptors				= new HashMap<>();

	public Transformer(PrintStream verbose, Properties properties) {
		this.verbose = verbose;
		this.rules = new Processor(properties, false);
		this.packageRenames = OSGiHeader.parseProperties(rules.mergeProperties("-package-rename"));
	}

	enum SignatureType {
		CLASS,
		FIELD,
		METHOD
	}

	public class JarTransformer {
		final String			folder;
		final Predicate<String>	classes;

		public JarTransformer() {
			this("");
		}

		public JarTransformer(String folder) {
			this.folder = Processor.appendPath(folder);
			PathSet pathSet = new PathSet();
			Strings.splitAsStream(rules.mergeProperties("-class-selector"))
				.forEach(item -> {
					if (item.startsWith("!")) {
						pathSet.exclude(Processor.appendPath(folder, item.substring(1)));
					} else {
						pathSet.include(Processor.appendPath(folder, item));
					}
				});
			this.classes = pathSet.matches(Processor.appendPath(folder, "**/*.class"));
		}

		public Optional<Jar> transform(Jar jar) throws Exception {
			boolean modified = false;
			long now = System.currentTimeMillis();
			Jar originalJar = requireNonNull(jar);
			// copy Jar object
			jar = new Jar(originalJar.getName());
			jar.setDoNotTouchManifest();
			for (Entry<String, Resource> entry : originalJar.getResources()
				.entrySet()) {
				jar.putResource(entry.getKey(), entry.getValue(), true);
			}
			// TODO handle OSGi metadata
			for (String resourceName : originalJar.getResources()
				.keySet()) {
				if (classes.test(resourceName)) {
					Resource resource = jar.getResource(resourceName);
					ByteBuffer bb = resource.buffer();
					if (bb == null) {
						bb = IO.copy(resource.openInputStream(), ByteBuffer.allocate((int) resource.size()));
					}
					ClassTransformer classTransformer = new ClassTransformer();
					Optional<ByteBuffer> transformed = classTransformer.transform(bb);
					if (transformed.isPresent()) {
						modified = true;
						Resource transformedResource = new EmbeddedResource(transformed.get(), now);
						String resourcePath = resourceName;
						String extension = "";
						int n = resourcePath.lastIndexOf('.');
						if (n >= 0) {
							extension = resourcePath.substring(n);
							resourcePath = resourcePath.substring(0, n);
						}
						int beginIndex = folder.isEmpty() ? 0 : folder.length() + 1;
						String binaryType = resourcePath.substring(beginIndex);
						Optional<String> type = transformBinaryType(binaryType);
						if (type.isPresent()) {
							jar.remove(resourceName);
							String path = Processor.appendPath(folder, type.get()
								.concat(extension));
							jar.putResource(path, transformedResource, false);
						} else {
							jar.putResource(resourceName, transformedResource);
						}
					}
				}
				if (metainfServicesFilter.test(resourceName)) {
					String binaryType = resourceName.substring(metainfServicesLength)
						.replace('.', '/');
					Optional<String> transformed = transformBinaryType(binaryType);
					if (transformed.isPresent()) {
						modified = true;
						String serviceName = metainfServices.concat(transformed.get()
							.replace('/', '.'));
						Resource resource = jar.remove(resourceName);
						jar.putResource(serviceName, resource, false);
						jar.updateModified(now, serviceName);
						verbose.printf("  %s -> %s\n", resourceName, serviceName);
					}
				}
			}
			return modified ? Optional.of(jar) : Optional.empty();
		}
	}

	public class ClassTransformer {
		final StringBuilder	sb;
		final Formatter		formatter;

		public ClassTransformer() {
			sb = new StringBuilder();
			formatter = new Formatter(sb);
		}

		public Optional<ByteBuffer> transform(ByteBuffer bb) throws Exception {
			sb.setLength(0);
			boolean modified = false;
			DataInput din = ByteBufferDataInput.wrap(bb);
			ClassFile cf = ClassFile.parseClassFile(din);
			ClassFileBuilder builder = new ClassFileBuilder(cf);
			MutableConstantPool constant_pool = builder.constant_pool();
			Optional<String> this_class = transformBinaryType(builder.this_class());
			if (this_class.isPresent()) {
				modified = true;
				builder.this_class(this_class.get());
			}
			formatter.format("%s", builder);
			if (builder.super_class() != null) {
				Optional<String> super_class = transformBinaryType(builder.super_class());
				if (super_class.isPresent()) {
					modified = true;
					builder.super_class(super_class.get());
				}
				if (!Objects.equals(builder.super_class(), "java/lang/Object")) {
					formatter.format(" extends %s", builder.super_class());
				}
			}
			if (!builder.interfaces()
				.isEmpty()) {
				for (ListIterator<String> iter = builder.interfaces()
					.listIterator(); iter.hasNext();) {
					Optional<String> optional = transformBinaryType(iter.next());
					if (optional.isPresent()) {
						modified = true;
						iter.set(optional.get());
					}
				}
				formatter.format(" implements %s", builder.interfaces());
			}
			formatter.format("\n");
			for (ListIterator<FieldInfo> iter = builder.fields()
				.listIterator(); iter.hasNext();) {
				Optional<FieldInfo> field = transformMember(iter.next(), FieldInfo::new, SignatureType.FIELD);
				if (field.isPresent()) {
					modified = true;
					iter.set(field.get());
				}
			}
			for (ListIterator<MethodInfo> iter = builder.methods()
				.listIterator(); iter.hasNext();) {
				Optional<MethodInfo> method = transformMember(iter.next(), MethodInfo::new, SignatureType.METHOD);
				if (method.isPresent()) {
					modified = true;
					iter.set(method.get());
				}
			}
			formatter.format("  <<class>>\n");
			for (ListIterator<Attribute> iter = builder.attributes()
				.listIterator(); iter.hasNext();) {
				Attribute attr = iter.next();
				Optional<Attribute> attribute = transformAttribute(attr, SignatureType.CLASS);
				if (attribute.isPresent()) {
					modified = true;
					formatter.format("    %s -> %s\n", attr, attribute.get());
					iter.set(attribute.get());
				}
			}
			modified |= transformConstantPool(constant_pool);
			if (modified) {
				ClassFile build = builder.build();
				ByteBufferDataOutput bbout = new ByteBufferDataOutput(bb.limit() + IOConstants.PAGE_SIZE);
				build.write(bbout);
				int oldSize = cf.constant_pool.size();
				for (int index = 1; index < oldSize; index++) {
					switch (constant_pool.tag(index)) {
						case CONSTANT_String : {
							StringInfo info = constant_pool.entry(index);
							String utf8 = constant_pool.utf8(info.string_index);
							if (utf8.contains("javax")) {
								formatter.format("    #%s = String #%s [uses-javax] // %s\n", index, info.string_index,
									utf8);
							}
							break;
						}
						case CONSTANT_Class :
						case CONSTANT_NameAndType :
						case CONSTANT_MethodType :
						case CONSTANT_Module :
						case CONSTANT_Package :
						case CONSTANT_Fieldref :
						case CONSTANT_Methodref :
						case CONSTANT_InterfaceMethodref :
						case CONSTANT_MethodHandle :
						case CONSTANT_Dynamic :
						case CONSTANT_InvokeDynamic :
						case CONSTANT_Utf8 :
						case CONSTANT_Integer :
						case CONSTANT_Float :
							break;
						case CONSTANT_Long :
						case CONSTANT_Double :
							// For some insane optimization reason, the Long(5)
							// and Double(6) entries take two slots in the
							// constant pool.
							// See 4.4.5
							index++;
							break;
						default :
							throw new IOException("Unrecognized constant pool entry " + constant_pool.entry(index)
								+ " at index " + index);
					}
				}
				int newSize = constant_pool.size();
				if (newSize > oldSize) {
					formatter.format("    Constant Pool size: old %s -> new %s\n", oldSize, newSize);
					for (int index = oldSize; index < newSize; index++) {
						formatter.format("    #%s %s\n", index, constant_pool.entry(index));
					}

				}
				ByteBuffer transformed = bbout.toByteBuffer();
				formatter.format("    Class size: old %s -> new %s\n", bb.limit(), transformed.limit());
				verbose.print(formatter);
				return Optional.of(transformed);
			}
			return Optional.empty();
		}

		private <M extends MemberInfo> Optional<M> transformMember(M member, MemberInfo.Constructor<M> constructor,
			SignatureType signatureType) {
			int sblen = sb.length();
			Optional<Attribute[]> attributes = transformAttributes(member.attributes, signatureType);
			String attributesOut = sb.substring(sblen);
			sb.setLength(sblen);
			Optional<String> descriptor = transformDescriptor(member.descriptor);
			if (descriptor.isPresent() || attributes.isPresent()) {
				formatter.format(descriptor.isPresent() ? "  %s %s -> %1$s %s\n%4$s" : "  %s %s\n%4$s", member.name,
					member.descriptor, descriptor.orElse(member.descriptor), attributesOut);
				return Optional.of(constructor.init(member.access, member.name, descriptor.orElse(member.descriptor),
					attributes.orElse(member.attributes)));
			}
			return Optional.empty();
		}

		private Optional<Attribute[]> transformAttributes(Attribute[] attributes, SignatureType signatureType) {
			attributes = attributes.clone();
			boolean modified = false;
			for (int i = 0; i < attributes.length; i++) {
				Optional<Attribute> attribute = transformAttribute(attributes[i], signatureType);
				if (attribute.isPresent()) {
					modified = true;
					formatter.format("    %s -> %s\n", attributes[i], attribute.get());
					attributes[i] = attribute.get();
				}
			}
			return modified ? Optional.of(attributes) : Optional.empty();
		}

		private Optional<Attribute> transformAttribute(Attribute attr, SignatureType signatureType) {
			switch (attr.name()) {
				case SignatureAttribute.NAME : {
					SignatureAttribute attribute = (SignatureAttribute) attr;
					Optional<String> signature = transformSignature(attribute.signature, signatureType);
					return signature.map(SignatureAttribute::new);
				}
				case ExceptionsAttribute.NAME : {
					boolean modified = false;
					ExceptionsAttribute attribute = (ExceptionsAttribute) attr;
					String[] exceptions = attribute.exceptions.clone();
					for (int j = 0; j < exceptions.length; j++) {
						Optional<String> exception = transformBinaryType(exceptions[j]);
						if (exception.isPresent()) {
							modified = true;
							exceptions[j] = exception.get();
						}
					}
					return modified ? Optional.of(new ExceptionsAttribute(exceptions)) : Optional.empty();
				}
				case CodeAttribute.NAME : {
					boolean modified = false;
					CodeAttribute attribute = (CodeAttribute) attr;
					ExceptionHandler[] exception_table = attribute.exception_table.clone();
					for (int j = 0; j < exception_table.length; j++) {
						ExceptionHandler exception = exception_table[j];
						Optional<String> catch_type = Optional.ofNullable(exception.catch_type)
							.flatMap(Transformer.this::transformBinaryType);
						if (catch_type.isPresent()) {
							modified = true;
							exception_table[j] = new ExceptionHandler(exception.start_pc, exception.end_pc,
								exception.handler_pc, catch_type.get());
						}
					}
					int sblen = sb.length();
					Optional<Attribute[]> code_attributes = transformAttributes(attribute.attributes,
						SignatureType.METHOD);
					sb.setLength(sblen);
					// TODO Maybe intercept Class.forName/etc calls at
					// runtime to rename types
					return (modified || code_attributes.isPresent())
						? Optional.of(new CodeAttribute(attribute.max_stack, attribute.max_locals, attribute.code,
							exception_table, code_attributes.orElse(attribute.attributes)))
						: Optional.empty();
				}
				case EnclosingMethodAttribute.NAME : {
					EnclosingMethodAttribute attribute = (EnclosingMethodAttribute) attr;
					Optional<String> method_descriptor = Optional.ofNullable(attribute.method_descriptor)
						.flatMap(Transformer.this::transformDescriptor);
					return method_descriptor.map(descriptor -> new EnclosingMethodAttribute(attribute.class_name,
						attribute.method_name, descriptor));
				}
				case StackMapTableAttribute.NAME : {
					boolean modified = false;
					StackMapTableAttribute attribute = (StackMapTableAttribute) attr;
					StackMapFrame[] entries = attribute.entries.clone();
					for (int j = 0; j < entries.length; j++) {
						StackMapFrame entry = entries[j];
						switch (entry.type()) {
							case StackMapFrame.SAME_LOCALS_1_STACK_ITEM : {
								SameLocals1StackItemFrame frame = (SameLocals1StackItemFrame) entry;
								Optional<VerificationTypeInfo> stack = transformVerificationTypeInfo(frame.stack);
								if (stack.isPresent()) {
									modified = true;
									entries[j] = new SameLocals1StackItemFrame(frame.tag, stack.get());
								}
								break;
							}
							case StackMapFrame.SAME_LOCALS_1_STACK_ITEM_EXTENDED : {
								SameLocals1StackItemFrameExtended frame = (SameLocals1StackItemFrameExtended) entry;
								Optional<VerificationTypeInfo> stack = transformVerificationTypeInfo(frame.stack);
								if (stack.isPresent()) {
									modified = true;
									entries[j] = new SameLocals1StackItemFrameExtended(frame.tag, frame.delta,
										stack.get());
								}
								break;
							}
							case StackMapFrame.APPEND : {
								AppendFrame frame = (AppendFrame) entry;
								Optional<VerificationTypeInfo[]> locals = transformVerificationTypeInfos(frame.locals);
								if (locals.isPresent()) {
									modified = true;
									entries[j] = new AppendFrame(frame.tag, frame.delta, locals.get());
								}
								break;
							}
							case StackMapFrame.FULL_FRAME : {
								FullFrame frame = (FullFrame) entry;
								Optional<VerificationTypeInfo[]> locals = transformVerificationTypeInfos(frame.locals);
								Optional<VerificationTypeInfo[]> stack = transformVerificationTypeInfos(frame.stack);
								if (locals.isPresent() || stack.isPresent()) {
									modified = true;
									entries[j] = new FullFrame(frame.tag, frame.delta, locals.orElse(frame.locals),
										stack.orElse(frame.stack));
								}
								break;
							}
							default :
								break;
						}
					}
					return modified ? Optional.of(new StackMapTableAttribute(entries)) : Optional.empty();
				}
				case InnerClassesAttribute.NAME : {
					boolean modified = false;
					InnerClassesAttribute attribute = (InnerClassesAttribute) attr;
					InnerClass[] classes = attribute.classes.clone();
					for (int j = 0; j < classes.length; j++) {
						InnerClass inner = classes[j];
						Optional<String> innerClass = transformBinaryType(inner.inner_class);
						Optional<String> outerClass = Optional.ofNullable(inner.outer_class)
							.flatMap(Transformer.this::transformBinaryType);
						if (innerClass.isPresent() || outerClass.isPresent()) {
							modified = true;
							classes[j] = new InnerClass(innerClass.orElse(inner.inner_class),
								outerClass.orElse(inner.outer_class), inner.inner_name, inner.inner_access);
						}
					}
					return modified ? Optional.of(new InnerClassesAttribute(classes)) : Optional.empty();
				}
				case LocalVariableTableAttribute.NAME : {
					boolean modified = false;
					LocalVariableTableAttribute attribute = (LocalVariableTableAttribute) attr;
					LocalVariable[] local_variable_table = attribute.local_variable_table.clone();
					for (int j = 0; j < local_variable_table.length; j++) {
						LocalVariable local_variable = local_variable_table[j];
						Optional<String> optional = transformDescriptor(local_variable.descriptor);
						if (optional.isPresent()) {
							modified = true;
							local_variable_table[j] = new LocalVariable(local_variable.start_pc, local_variable.length,
								local_variable.name, optional.get(), local_variable.index);
						}
					}
					return modified ? Optional.of(new LocalVariableTableAttribute(local_variable_table))
						: Optional.empty();
				}
				case LocalVariableTypeTableAttribute.NAME : {
					boolean modified = false;
					LocalVariableTypeTableAttribute attribute = (LocalVariableTypeTableAttribute) attr;
					LocalVariableType[] local_variable_type_table = attribute.local_variable_type_table.clone();
					for (int j = 0; j < local_variable_type_table.length; j++) {
						LocalVariableType local_variable_type = local_variable_type_table[j];
						Optional<String> optional = transformSignature(local_variable_type.signature,
							SignatureType.FIELD);
						if (optional.isPresent()) {
							modified = true;
							local_variable_type_table[j] = new LocalVariableType(local_variable_type.start_pc,
								local_variable_type.length, local_variable_type.name, optional.get(),
								local_variable_type.index);
						}
					}
					return modified ? Optional.of(new LocalVariableTypeTableAttribute(local_variable_type_table))
						: Optional.empty();
				}
				case RuntimeVisibleAnnotationsAttribute.NAME : {
					RuntimeVisibleAnnotationsAttribute attribute = (RuntimeVisibleAnnotationsAttribute) attr;
					Optional<RuntimeVisibleAnnotationsAttribute> annotations = transformAnnotationsAttribute(attribute,
						RuntimeVisibleAnnotationsAttribute::new);
					return annotations.map(identity());
				}
				case RuntimeInvisibleAnnotationsAttribute.NAME : {
					RuntimeInvisibleAnnotationsAttribute attribute = (RuntimeInvisibleAnnotationsAttribute) attr;
					Optional<RuntimeInvisibleAnnotationsAttribute> annotations = transformAnnotationsAttribute(
						attribute, RuntimeInvisibleAnnotationsAttribute::new);
					return annotations.map(identity());
				}
				case RuntimeVisibleParameterAnnotationsAttribute.NAME : {
					RuntimeVisibleParameterAnnotationsAttribute attribute = (RuntimeVisibleParameterAnnotationsAttribute) attr;
					Optional<RuntimeVisibleParameterAnnotationsAttribute> parameter_annotations = transformParameterAnnotationsAttribute(
						attribute, RuntimeVisibleParameterAnnotationsAttribute::new);
					return parameter_annotations.map(identity());
				}
				case RuntimeInvisibleParameterAnnotationsAttribute.NAME : {
					RuntimeInvisibleParameterAnnotationsAttribute attribute = (RuntimeInvisibleParameterAnnotationsAttribute) attr;
					Optional<RuntimeInvisibleParameterAnnotationsAttribute> parameter_annotations = transformParameterAnnotationsAttribute(
						attribute, RuntimeInvisibleParameterAnnotationsAttribute::new);
					return parameter_annotations.map(identity());
				}
				case RuntimeVisibleTypeAnnotationsAttribute.NAME : {
					RuntimeVisibleTypeAnnotationsAttribute attribute = (RuntimeVisibleTypeAnnotationsAttribute) attr;
					Optional<RuntimeVisibleTypeAnnotationsAttribute> type_annotations = transformTypeAnnotationsAttribute(
						attribute, RuntimeVisibleTypeAnnotationsAttribute::new);
					return type_annotations.map(identity());
				}
				case RuntimeInvisibleTypeAnnotationsAttribute.NAME : {
					RuntimeInvisibleTypeAnnotationsAttribute attribute = (RuntimeInvisibleTypeAnnotationsAttribute) attr;
					Optional<RuntimeInvisibleTypeAnnotationsAttribute> type_annotations = transformTypeAnnotationsAttribute(
						attribute, RuntimeInvisibleTypeAnnotationsAttribute::new);
					return type_annotations.map(identity());
				}
				case AnnotationDefaultAttribute.NAME : {
					AnnotationDefaultAttribute attribute = (AnnotationDefaultAttribute) attr;
					Optional<Object> value = transformElementValue(attribute.value);
					return value.map(AnnotationDefaultAttribute::new);
				}
				case ModuleAttribute.NAME :
				case ModulePackagesAttribute.NAME :
					// TODO Handle module metadata in case some
					// used by some Java EE 8/Jakarta EE 8 artifacts.
					break;
				case NestHostAttribute.NAME :
				case NestMembersAttribute.NAME : {
					// TODO These Java SE 9+ attributes should not be used
					// by Java EE 8/Jakarta EE 8 artifacts, so
					// we ignore them for now.
					break;
				}
				default : {
					break;
				}
			}
			return Optional.empty();
		}

		private <A extends AnnotationsAttribute> Optional<A> transformAnnotationsAttribute(A attribute,
			AnnotationsAttribute.Constructor<A> constructor) {
			Optional<AnnotationInfo[]> annotations = transformAnnotationInfos(attribute.annotations);
			return annotations.map(constructor::init);
		}

		private Optional<AnnotationInfo[]> transformAnnotationInfos(AnnotationInfo[] annotations) {
			annotations = annotations.clone();
			boolean modified = false;
			for (int i = 0; i < annotations.length; i++) {
				AnnotationInfo annotation = annotations[i];
				Optional<AnnotationInfo> optional = transformAnnotationInfo(annotation, AnnotationInfo::new);
				if (optional.isPresent()) {
					modified = true;
					annotations[i] = optional.get();
				}
			}
			return modified ? Optional.of(annotations) : Optional.empty();
		}

		private <A extends ParameterAnnotationsAttribute> Optional<A> transformParameterAnnotationsAttribute(
			A attribute, ParameterAnnotationsAttribute.Constructor<A> constructor) {
			Optional<ParameterAnnotationInfo[]> parameter_annotations = transformParameterAnnotationInfos(
				attribute.parameter_annotations);
			return parameter_annotations.map(constructor::init);
		}

		private Optional<ParameterAnnotationInfo[]> transformParameterAnnotationInfos(
			ParameterAnnotationInfo[] parameter_annotations) {
			parameter_annotations = parameter_annotations.clone();
			boolean modified = false;
			for (int i = 0; i < parameter_annotations.length; i++) {
				ParameterAnnotationInfo parameter_annotation = parameter_annotations[i];
				Optional<AnnotationInfo[]> optional = transformAnnotationInfos(parameter_annotation.annotations);
				if (optional.isPresent()) {
					modified = true;
					parameter_annotations[i] = new ParameterAnnotationInfo(parameter_annotation.parameter,
						optional.get());
				}
			}
			return modified ? Optional.of(parameter_annotations) : Optional.empty();
		}

		private <A extends TypeAnnotationsAttribute> Optional<A> transformTypeAnnotationsAttribute(A attribute,
			TypeAnnotationsAttribute.Constructor<A> constructor) {
			Optional<TypeAnnotationInfo[]> type_annotations = transformTypeAnnotationInfos(attribute.type_annotations);
			return type_annotations.map(constructor::init);
		}

		private Optional<TypeAnnotationInfo[]> transformTypeAnnotationInfos(TypeAnnotationInfo[] type_annotations) {
			type_annotations = type_annotations.clone();
			boolean modified = false;
			for (int i = 0; i < type_annotations.length; i++) {
				TypeAnnotationInfo type_annotation = type_annotations[i];
				Optional<TypeAnnotationInfo> optional = transformAnnotationInfo(type_annotation,
					(type, values) -> new TypeAnnotationInfo(type_annotation.target_type, type_annotation.target_info,
						type_annotation.target_index, type_annotation.type_path, type, values));
				if (optional.isPresent()) {
					modified = true;
					type_annotations[i] = optional.get();
				}
			}
			return modified ? Optional.of(type_annotations) : Optional.empty();
		}

		private <A extends AnnotationInfo> Optional<A> transformAnnotationInfo(A annotation,
			AnnotationInfo.Constructor<A> constructor) {
			Optional<String> type = transformDescriptor(annotation.type);
			Optional<ElementValueInfo[]> values = transformElementValueInfos(annotation.values);
			if (type.isPresent() || values.isPresent()) {
				return Optional.of(constructor.init(type.orElse(annotation.type), values.orElse(annotation.values)));
			}
			return Optional.empty();
		}

		private Optional<ElementValueInfo[]> transformElementValueInfos(ElementValueInfo[] values) {
			values = values.clone();
			boolean modified = false;
			for (int i = 0; i < values.length; i++) {
				ElementValueInfo elementValue = values[i];
				Optional<Object> value = transformElementValue(elementValue.value);
				if (value.isPresent()) {
					modified = true;
					values[i] = new ElementValueInfo(elementValue.name, value.get());
				}
			}
			return modified ? Optional.of(values) : Optional.empty();
		}

		private Optional<Object> transformElementValue(Object value) {
			if (value instanceof EnumConst) {
				EnumConst enum_const_value = (EnumConst) value;
				return transformDescriptor(enum_const_value.type)
					.map(type -> new EnumConst(type, enum_const_value.name));
			} else if (value instanceof ResultConst) {
				ResultConst class_info = (ResultConst) value;
				return transformDescriptor(class_info.descriptor).map(ResultConst::new);
			} else if (value instanceof AnnotationInfo) {
				AnnotationInfo annotation_value = (AnnotationInfo) value;
				return transformAnnotationInfo(annotation_value, AnnotationInfo::new).map(identity());
			} else if (value instanceof Object[]) {
				Object[] array_values = ((Object[]) value).clone();
				boolean modified = false;
				for (int i = 0; i < array_values.length; i++) {
					Optional<Object> array_value = transformElementValue(array_values[i]);
					if (array_value.isPresent()) {
						modified = true;
						array_values[i] = array_value.get();
					}
				}
				return modified ? Optional.of(array_values) : Optional.empty();
			}
			return Optional.empty();
		}

		private Optional<VerificationTypeInfo> transformVerificationTypeInfo(VerificationTypeInfo vti) {
			if (vti instanceof ObjectVariableInfo) {
				ObjectVariableInfo ovi = (ObjectVariableInfo) vti;
				return transformBinaryType(ovi.type).map(type -> new ObjectVariableInfo(ovi.tag, type));
			}
			return Optional.empty();
		}

		private Optional<VerificationTypeInfo[]> transformVerificationTypeInfos(VerificationTypeInfo[] vtis) {
			vtis = vtis.clone();
			boolean modified = false;
			for (int i = 0; i < vtis.length; i++) {
				Optional<VerificationTypeInfo> vti = transformVerificationTypeInfo(vtis[i]);
				if (vti.isPresent()) {
					modified = true;
					vtis[i] = vti.get();
				}
			}
			return modified ? Optional.of(vtis) : Optional.empty();
		}

		private boolean transformConstantPool(MutableConstantPool constant_pool) throws IOException {
			boolean modified = false;
			int constant_pool_count = constant_pool.size();
			for (int index = 1; index < constant_pool_count; index++) {
				switch (constant_pool.tag(index)) {
					case CONSTANT_Class : {
						ClassInfo info = constant_pool.entry(index);
						String type = constant_pool.entry(info.class_index);
						Optional<String> optional = transformBinaryType(type);
						if (optional.isPresent()) {
							modified = true;
							constant_pool.entry(index, new ClassInfo(constant_pool.utf8Info(optional.get())));
							formatter.format("    ClassInfo: %s -> %s\n", type, optional.get());
						}
						break;
					}
					case CONSTANT_NameAndType : {
						NameAndTypeInfo info = constant_pool.entry(index);
						String descriptor = constant_pool.utf8(info.descriptor_index);
						Optional<String> optional = transformDescriptor(descriptor);
						if (optional.isPresent()) {
							modified = true;
							constant_pool.entry(index,
								new NameAndTypeInfo(info.name_index, constant_pool.utf8Info(optional.get())));
							formatter.format("    NameAndTypeInfo descriptor: %s -> %s\n", descriptor, optional.get());
						}
						break;
					}
					case CONSTANT_MethodType : {
						MethodTypeInfo info = constant_pool.entry(index);
						String descriptor = constant_pool.utf8(info.descriptor_index);
						Optional<String> optional = transformDescriptor(descriptor);
						if (optional.isPresent()) {
							modified = true;
							constant_pool.entry(index, new MethodTypeInfo(constant_pool.utf8Info(optional.get())));
							formatter.format("    MethodTypeInfo descriptor: %s -> %s\n", descriptor, optional.get());
						}
						break;
					}
					case CONSTANT_String : {
						// TODO maybe rewrite String constants for renamed
						// packages?
						break;
					}
					case CONSTANT_Fieldref :
					case CONSTANT_Methodref :
					case CONSTANT_InterfaceMethodref :
					case CONSTANT_MethodHandle :
					case CONSTANT_Dynamic :
					case CONSTANT_InvokeDynamic :
					case CONSTANT_Module :
					case CONSTANT_Package :
					case CONSTANT_Utf8 :
					case CONSTANT_Integer :
					case CONSTANT_Float :
						break;
					case CONSTANT_Long :
					case CONSTANT_Double :
						// For some insane optimization reason, the Long(5)
						// and Double(6) entries take two slots in the
						// constant pool.
						// See 4.4.5
						index++;
						break;
					default :
						throw new IOException(
							"Unrecognized constant pool entry " + constant_pool.entry(index) + " at index " + index);
				}
			}
			return modified;
		}
	}

	String transformBinaryPackage(String binaryPackage) {
		return packageRenames.getOrDefault(binaryPackage, binaryPackage);
	}

	Optional<String> transformBinaryType(String binaryType) {
		String transformed = binaryTypes.computeIfAbsent(binaryType, type -> {
			char c = type.charAt(0);
			if ((c == '[') || ((c == 'L') && (type.charAt(type.length() - 1) == ';'))) {
				String signature = type.replace('$', '.');
				JavaTypeSignature javaTypeSignature = JavaTypeSignature.of(signature);
				JavaTypeSignature transformedSig = transformJavaTypeSignature(javaTypeSignature);
				return transformedSig.toString()
					.replace('.', '$');
			}
			int n = type.lastIndexOf('/');
			if (n != -1) {
				String binaryPackage = type.substring(0, n);
				return transformBinaryPackage(binaryPackage).concat(type.substring(n));
			}
			return type;
		});
		return Objects.equals(transformed, binaryType) ? Optional.empty() : Optional.of(transformed);
	}

	Optional<String> transformDescriptor(String descriptor) {
		String transformed = descriptors.computeIfAbsent(descriptor, desc -> {
			char c = desc.charAt(0);
			if (c == '(') {
				String signature = desc.replace('$', '.');
				Optional<String> transformedSig = transformSignature(signature, SignatureType.METHOD);
				return transformedSig.map(sig -> sig.replace('.', '$'))
					.orElse(desc);
			}
			if ((c == '[') || ((c == 'L') && (desc.charAt(desc.length() - 1) == ';'))) {
				String signature = desc.replace('$', '.');
				Optional<String> transformedSig = transformSignature(signature, SignatureType.FIELD);
				return transformedSig.map(sig -> sig.replace('.', '$'))
					.orElse(desc);
			}
			return desc;
		});
		return Objects.equals(transformed, descriptor) ? Optional.empty() : Optional.of(transformed);
	}

	Optional<String> transformSignature(String signature, SignatureType signatureType) {
		String transformed = signatures.computeIfAbsent(signature, sig -> {
			switch (signatureType) {
				case CLASS : {
					ClassSignature classSignature = ClassSignature.of(sig);
					ClassSignature transformedSig = transformClassSignature(classSignature);
					return transformedSig.toString();
				}
				case FIELD : {
					FieldSignature fieldSignature = FieldSignature.of(sig);
					FieldSignature transformedSig = transformFieldSignature(fieldSignature);
					return transformedSig.toString();
				}
				case METHOD : {
					MethodSignature methodSignature = MethodSignature.of(sig);
					MethodSignature transformedSig = transformMethodSignature(methodSignature);
					return transformedSig.toString();
				}
				default : {
					throw new IllegalArgumentException(
						"Signature \"" + sig + "\" found for unknown element type: " + signatureType);
				}
			}
		});
		return Objects.equals(transformed, signature) ? Optional.empty() : Optional.of(transformed);
	}

	ArrayTypeSignature transformArrayTypeSignature(ArrayTypeSignature arrayType) {
		JavaTypeSignature component = arrayType.component;
		int depth = 1;
		while (component instanceof ArrayTypeSignature) {
			depth++;
			component = ((ArrayTypeSignature) component).component;
		}
		if ((component instanceof BaseType) || (component instanceof TypeVariableSignature)) {
			return arrayType;
		}
		component = transformClassTypeSignature((ClassTypeSignature) component);
		arrayType = new ArrayTypeSignature(component);
		while (--depth > 0) {
			arrayType = new ArrayTypeSignature(arrayType);
		}
		return arrayType;
	}

	ClassTypeSignature transformClassTypeSignature(ClassTypeSignature type) {
		String packageSpecifier = type.packageSpecifier;
		int length = packageSpecifier.length();
		if (length > 0) {
			String binaryPackage = packageSpecifier.substring(0, length - 1);
			packageSpecifier = transformBinaryPackage(binaryPackage).concat("/");
		}
		SimpleClassTypeSignature classType = transformSimpleClassTypeSignature(type.classType);
		SimpleClassTypeSignature[] innerTypes = new SimpleClassTypeSignature[type.innerTypes.length];
		for (int i = 0; i < innerTypes.length; i++) {
			innerTypes[i] = transformSimpleClassTypeSignature(type.innerTypes[i]);
		}
		// Note, we do not rebuild binary since it is not part of the
		// toString() result
		return new ClassTypeSignature(type.binary, packageSpecifier, classType, innerTypes);
	}

	JavaTypeSignature transformJavaTypeSignature(JavaTypeSignature type) {
		if (type instanceof ReferenceTypeSignature) {
			return transformReferenceTypeSignature((ReferenceTypeSignature) type);
		}
		return type;
	}

	ReferenceTypeSignature transformReferenceTypeSignature(ReferenceTypeSignature type) {
		if (type instanceof ClassTypeSignature) {
			return transformClassTypeSignature((ClassTypeSignature) type);
		}
		if (type instanceof ArrayTypeSignature) {
			return transformArrayTypeSignature((ArrayTypeSignature) type);
		}
		return type;
	}

	Result transformResult(Result type) {
		if (type instanceof JavaTypeSignature) {
			return transformJavaTypeSignature((JavaTypeSignature) type);
		}
		return type;
	}

	SimpleClassTypeSignature transformSimpleClassTypeSignature(SimpleClassTypeSignature type) {
		TypeArgument[] typeArguments = new TypeArgument[type.typeArguments.length];
		for (int i = 0; i < typeArguments.length; i++) {
			typeArguments[i] = transformTypeArgument(type.typeArguments[i]);
		}
		return new SimpleClassTypeSignature(type.identifier, typeArguments);
	}

	ThrowsSignature transformThrowsSignature(ThrowsSignature type) {
		if (type instanceof ClassTypeSignature) {
			return transformClassTypeSignature((ClassTypeSignature) type);
		}
		return type;
	}

	TypeArgument transformTypeArgument(TypeArgument typeArgument) {
		ReferenceTypeSignature type = typeArgument.type;
		return new TypeArgument(typeArgument.wildcard, transformReferenceTypeSignature(type));
	}

	TypeParameter transformTypeParameter(TypeParameter typeParameter) {
		ReferenceTypeSignature classBound = transformReferenceTypeSignature(typeParameter.classBound);
		ReferenceTypeSignature[] interfaceBounds = new ReferenceTypeSignature[typeParameter.interfaceBounds.length];
		for (int i = 0; i < interfaceBounds.length; i++) {
			interfaceBounds[i] = transformReferenceTypeSignature(typeParameter.interfaceBounds[i]);
		}
		return new TypeParameter(typeParameter.identifier, classBound, interfaceBounds);
	}

	ClassSignature transformClassSignature(ClassSignature classSignature) {
		TypeParameter[] typeParameters = new TypeParameter[classSignature.typeParameters.length];
		for (int i = 0; i < typeParameters.length; i++) {
			typeParameters[i] = transformTypeParameter(classSignature.typeParameters[i]);
		}
		ClassTypeSignature superClass = transformClassTypeSignature(classSignature.superClass);
		ClassTypeSignature[] superInterfaces = new ClassTypeSignature[classSignature.superInterfaces.length];
		for (int i = 0; i < superInterfaces.length; i++) {
			superInterfaces[i] = transformClassTypeSignature(classSignature.superInterfaces[i]);
		}
		return new ClassSignature(typeParameters, superClass, superInterfaces);
	}

	FieldSignature transformFieldSignature(FieldSignature fieldSignature) {
		ReferenceTypeSignature type = fieldSignature.type;
		return new FieldSignature(transformReferenceTypeSignature(type));
	}

	MethodSignature transformMethodSignature(MethodSignature methodSignature) {
		TypeParameter[] typeParameters = new TypeParameter[methodSignature.typeParameters.length];
		for (int i = 0; i < typeParameters.length; i++) {
			typeParameters[i] = transformTypeParameter(methodSignature.typeParameters[i]);
		}
		JavaTypeSignature[] parameterTypes = new JavaTypeSignature[methodSignature.parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypes[i] = transformJavaTypeSignature(methodSignature.parameterTypes[i]);
		}
		Result resultType = transformResult(methodSignature.resultType);
		ThrowsSignature[] throwTypes = new ThrowsSignature[methodSignature.throwTypes.length];
		for (int i = 0; i < throwTypes.length; i++) {
			throwTypes[i] = transformThrowsSignature(methodSignature.throwTypes[i]);
		}
		return new MethodSignature(typeParameters, parameterTypes, resultType, throwTypes);
	}
}
