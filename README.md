# Class file and Jar/War file Transformer

This project transforms class file and the class files in Jar/War files to rename type references. This project was developed is response to the type renaming needs of Jakarta EE.

Jakarta EE 8 is binary compatible with its ancestor Java EE 8 using java packages which start with the `javax.` package prefix. After Jakarta EE 8, all changes to any Jarkata EE specification will require a renaming effort to move away from the `javax.` package prefix.

The specifics of this renaming and the new names are still a subject of discussion within the Jakarta community. But, for a significant portion of the packages, renaming will be necessary for Jakarta EE releases after release 8.

## Purpose

This project defines a class which can be used to mutate existing binary class files to rename package references such as the renames which will be necessary for Jakarta EE releases after release 8. 

The project can mutate a individual class files and thus could be used in a class loader to dynamically rename package references are runtime while classes are being loaded. This could allow a program such as an application server to use this support in its class loaders to allow existing libraries using types with the `javax.` package prefix to be loaded and executed in a newer Jakarta EE 9+ environment using the renamed packages.

The project can also mutate all types in Jar and War files. This could be used during a tooling phase to process dependencies and application jars prior to assembly so that all the classes are mutated to the renamed types for Jakarta EE.

## Rule driven

While this project was created in response to the needs of the Jakarta EE community, it can be used for any type renaming situation. The project provides a built in rule set for Jakarta EE, but alternate rule sets can be used via the `--rules` command line option (or programmatically).

## Command line

In addition to the programmatic interface, a command line interface (CLI) is also provided which supports several options:

```bash
usage: transformer [options]

Options:
 -c,--class <arg>    Class FILE to transform
 -h,--help           Display help information
 -j,--jar <arg>      Jar FILE to transform
 -o,--output <arg>   Write to FILE instead of stdout
 -r,--rules <arg>    URL of transform rules. Built-in rules are used if
                     not specified
 -v,--verbose        Verbose output to stderr
 -w,--war <arg>      War FILE to transform

```

## Building

We use Gradle to build and the repo includes `gradlew`.
You can use your system `gradle` but we require at least version 5.0.

- `./gradlew build` - Assembles and tests the project. After building, a distribution zip/tar can be found in `build/distributions`.
- `./gradlew run --args="--help"` - Runs the project. You can use whatever arguments you wish.

[![Travis CI Build Status](https://travis-ci.com/bjhargrave/transformer.svg?branch=master)](https://travis-ci.com/bjhargrave/transformer)

## Future work

See the [open issues](https://github.com/bjhargrave/transformer/issues) for the list of outstanding TODOs.

## License

This program and the accompanying materials are made available under the terms of the Apache License, Version 2.0 which is available at <https://www.apache.org/licenses/LICENSE-2.0>.

## Contributing

Want to hack? There are [instructions](CONTRIBUTING.md) to get you
started.

They are probably not perfect, please let us know if anything feels
wrong or incomplete.

## Acknowledgments

This project is based upon the [Bnd](https://github.com/bndtools/bnd) project for its Jar and class file manipulation support.
