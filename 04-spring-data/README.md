# Spring Data

This section covers Spring Data JPA from the repository abstraction through query methods, custom queries, transaction semantics, entity mapping, auditing, and projections. The emphasis is on how the framework generates repository implementations at runtime, how transactions propagate, and the JPA-specific behaviors that differ from raw Hibernate usage.

## Notes

| File | Description |
|------|-------------|
| [01-repository-abstraction.md](./01-repository-abstraction.md) | Repository hierarchy, RepositoryFactoryBean internals, choosing the right interface |
| [02-jpa-repositories.md](./02-jpa-repositories.md) | SimpleJpaRepository, EntityManager usage, flush modes, save vs saveAndFlush |
| [03-query-methods.md](./03-query-methods.md) | Method name parsing rules, subject and predicate keywords, supported return types |
| [04-custom-queries.md](./04-custom-queries.md) | @Query with JPQL and native SQL, @Modifying, @Param, SpEL expressions |
| [05-transactions.md](./05-transactions.md) | @Transactional attributes, all propagation behaviors, self-invocation problem, read-only optimizations |
| [06-entity-mapping.md](./06-entity-mapping.md) | @Entity, @Column, @Id strategies, @Embedded, @MappedSuperclass, @Inheritance strategies |
| [07-auditing.md](./07-auditing.md) | @CreatedDate, @LastModifiedDate, @CreatedBy, @EnableJpaAuditing, AuditorAware |
| [08-projections.md](./08-projections.md) | Interface projections, DTO projections, dynamic projections, SpEL in projections |

## Examples

| Project | Description |
|---------|-------------|
| [RepositoryDemo](./examples/RepositoryDemo/) | Shows query methods, @Query, and custom repository fragments with H2 |
| [TransactionDemo](./examples/TransactionDemo/) | Demonstrates propagation behaviors, rollback rules, and the self-invocation trap |
| [EntityMappingDemo](./examples/EntityMappingDemo/) | Maps inheritance hierarchies and embedded value objects |
| [ProjectionsDemo](./examples/ProjectionsDemo/) | Compares interface projections vs DTO projections vs entity fetches |
