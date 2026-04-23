# Spring Notes — AGENTS.md

This repository is a comprehensive Spring Framework reference for experienced Java developers. It covers the Spring ecosystem from the IoC container through Spring Boot, MVC, Data, Security, and AOP, with runnable Maven example projects for every major concept. The writing targets advanced readers who need precise, internally consistent explanations of how Spring works under the hood, not introductory tutorials.

## Versions

- Java 25
- Spring Framework 6.x
- Spring Boot 3.x (latest stable)

## Directory Structure

```
spring-notes/
  AGENTS.md                        # This file — repo description, structure, style guide
  README.md                        # Root index with section table
  01-spring-core/                  # IoC container, DI, bean lifecycle, scopes, events
  02-spring-boot/                  # Auto-configuration, starters, actuator, testing
  03-spring-mvc/                   # REST controllers, filters, validation, exception handling
  04-spring-data/                  # JPA repositories, queries, transactions, projections
  05-spring-security/              # Authentication, authorization, filters, JWT, OAuth2
  06-spring-aop/                   # Aspects, pointcuts, advice types, proxy internals
  07-interview-prep/               # Common questions, tricky scenarios, cheatsheet
```

Each section contains `.md` note files, a `README.md` section index, and an `examples/` directory with self-contained Maven projects.

## Style Guide

### Tone

The target reader is an experienced Java developer who wants a precise, comprehensive reference for Spring. Write at an advanced level — cover internals, trade-offs, and edge cases, not just happy-path usage. Assume familiarity with Java and core Spring concepts. Every non-obvious term should still be defined on first use within each file.

### Note File Format

Every note `.md` file must follow this structure in this exact order:

```
# <Title>

## Overview
2–4 paragraphs of plain prose. No bullet points.

## Key Concepts
Named subsections using ### headings. Each subsection contains prose and,
where helpful, a focused code block. Code blocks must serve the explanation
— do not include them as a formality.

## Gotchas
4–6 specific pitfalls written as plain prose paragraphs.
Each pitfall is 2–4 sentences. No sub-bullets.
```

There is no mandatory Code Snippet section. Code lives inside Key Concepts subsections where it aids understanding, and in the `examples/` folder for runnable demonstrations.

### Section README Format

Each section `README.md` must contain:

1. A prose intro of 2–3 sentences describing what the section covers. No bullet points.
2. A contents table linking every `.md` file with a one-line description.
3. A contents table linking every example project with a one-line description.

### Writing Rules

- No emojis anywhere
- No References section in any file — use `###` subsections instead
- No filler phrases: "it's worth noting", "importantly", "keep in mind", "note that", "it should be noted"
- ASCII diagrams are encouraged where they clarify structure or flow
- Comparison tables are encouraged
- All code blocks in `.md` files must use the correct syntax highlighting: `java`, `yaml`, `properties`, `xml`, or `sql` as appropriate

## Example Project Conventions

### Technology

- Java 25
- Spring Boot 3.x (latest stable)
- Spring Framework 6.x
- Build tool: Maven (`pom.xml` per project)
- Each example is a self-contained Maven project that opens and runs in IntelliJ IDEA without any additional setup

### Structure

Each example lives in its own folder under the section's `examples/` directory, named in PascalCase (e.g. `examples/BeanLifecycleDemo/`). The structure within each example folder follows standard Maven layout.

Use the package name `com.example.<conceptname>` for each project, where `<conceptname>` is the lowercase, no-separator version of the folder name (e.g. `com.example.beanlifecycle`).

### pom.xml

Every `pom.xml` must:
- Extend `spring-boot-starter-parent` 3.x (latest stable) as the parent
- Declare Java 25 as the source/target version
- Include only the dependencies actually needed for that example
- Be fully functional — running the main class in IntelliJ IDEA must work without modification

### Comments

Every `.java` file must open with a block comment explaining what the file represents within the example.

Inline comments must explain the why — the reasoning behind a design decision, a Spring behavior being demonstrated, or a subtle edge case. They must not restate what the code literally does.

The code and its comments together must make the example self-explanatory without requiring the reader to open the `.md` file.

Do not add `System.out.println` statements as a substitute for comments. Use them only when the printed output is the observable result of the demonstration.

### application.yaml

Prefer `application.yaml` over `application.properties`. Include only properties relevant to the example. Add a comment above each non-obvious property explaining why it is set.

## Adding New Content

All new `.md` files must follow the note file format above — `## Overview`, `## Key Concepts` with `###` subsections, and `## Gotchas`. All new example projects must follow the Maven structure convention, use `spring-boot-starter-parent` as parent, target Java 25, and include the required block comment in every `.java` file. Do not introduce a style that differs from existing files.
