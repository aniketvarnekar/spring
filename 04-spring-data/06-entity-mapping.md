# Entity Mapping

## Overview

JPA entity mapping translates Java class structures to relational database schemas through a set of annotations. The mapping layer handles identity (`@Id`, `@GeneratedValue`), column constraints (`@Column`), relationships (`@OneToMany`, `@ManyToOne`, `@ManyToMany`), embedded value objects (`@Embeddable`, `@Embedded`), mapped superclasses, and inheritance hierarchies. Getting the mapping right is foundational to both correctness and performance.

`@Entity` marks a class as a JPA entity. Every `@Entity` must have a field or property designated as the primary key with `@Id`. The class must be non-final (for proxy generation), have a no-arg constructor (which can be protected), and not be an inner class. These constraints are set by the JPA specification, and violating them causes subtle failures — often at runtime rather than startup.

Inheritance between entities is handled by three strategies: `SINGLE_TABLE` puts all subtype columns into one table discriminated by a type column; `JOINED` puts shared columns in the parent table and subtype columns in child tables linked by foreign key; `TABLE_PER_CLASS` puts all columns (shared + specific) in each subtype's table. Each strategy has distinct performance trade-offs and query complexity implications.

Embedded value objects (`@Embeddable` + `@Embedded`) model concepts that are logically part of an entity but are stored in the same table — for example, an `Address` embedded in a `Customer`. The embedded object is a plain Java class without its own table or `@Id`. This is the JPA equivalent of the Value Object pattern from Domain-Driven Design.

## Key Concepts

### Basic Entity

```java
@Entity
@Table(name = "orders",
       indexes = @Index(name = "idx_orders_customer", columnList = "customer_id"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"external_ref"}))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // database-generated auto-increment
    private Long id;

    @Column(name = "external_ref", nullable = false, unique = true, length = 50)
    private String externalRef;

    @Enumerated(EnumType.STRING)   // store enum name as string — resilient to reordering
    @Column(nullable = false)
    private OrderStatus status;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // required no-arg constructor (may be protected)
    protected Order() {}

    public Order(String externalRef, BigDecimal amount) {
        this.externalRef = externalRef;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }
}
```

### @Id and @GeneratedValue Strategies

| Strategy | Description | Notes |
|---|---|---|
| `IDENTITY` | Database auto-increment | No batch INSERT (ID unknown until after INSERT) |
| `SEQUENCE` | Database sequence | Supports batch INSERT; requires sequence object |
| `TABLE` | JPA-managed sequence table | Portable but slow; avoid in production |
| `AUTO` | Provider chooses | Defaults to TABLE in many Hibernate versions — specify explicitly |
| `UUID` | Application-assigned UUID | No database call; globally unique |

For Hibernate with PostgreSQL, `SEQUENCE` is the recommended strategy:

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
@SequenceGenerator(name = "order_seq", sequenceName = "order_id_seq",
                   allocationSize = 50)  // fetch 50 IDs per database round trip
private Long id;
```

### @Embedded and @Embeddable

```java
@Embeddable
public class Address {
    @Column(nullable = false)
    private String street;
    private String city;
    @Column(length = 10)
    private String postalCode;
    @Column(length = 2)
    private String countryCode;

    protected Address() {}  // required by JPA
    public Address(String street, String city, String postalCode, String countryCode) { ... }
    // getters
}

@Entity
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    // @AttributeOverride allows renaming columns when the same Embeddable
    // is used multiple times (e.g., billing address and shipping address)
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city"))
    })
    private Address billingAddress;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "shipping_street")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city"))
    })
    private Address shippingAddress;
}
```

### @MappedSuperclass

`@MappedSuperclass` shares mapping definitions across entities without creating a table for the superclass:

```java
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version  // optimistic locking — prevents lost updates
    private Long version;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // getters
}

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    // inherits id, createdAt, version columns
    private String status;
}
```

### Inheritance Strategies

```java
// SINGLE_TABLE — all types in one table with a discriminator column
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payment_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal amount;
}

@Entity
@DiscriminatorValue("CARD")
public class CardPayment extends Payment {
    private String cardLastFour;
    private String cardBrand;
}

@Entity
@DiscriminatorValue("BANK")
public class BankTransferPayment extends Payment {
    private String accountNumber;
    private String routingNumber;
}
```

| Strategy | Query complexity | Schema complexity | Nullable columns | Polymorphic query |
|---|---|---|---|---|
| `SINGLE_TABLE` | Simple (single table) | Simple | Many nullables | Fast (one table) |
| `JOINED` | Complex (joins required) | Normalized | Few nullables | Requires joins |
| `TABLE_PER_CLASS` | Complex (UNION) | Redundant columns | Few nullables | Slow (UNION ALL) |

`SINGLE_TABLE` is the default and the most performant for polymorphic queries. Use `JOINED` when schema normalization is important and subtype-specific queries are common. Avoid `TABLE_PER_CLASS` except in specific circumstances where polymorphic queries are never needed.

## Gotchas

`@Enumerated(EnumType.ORDINAL)` (the JPA default) stores the enum's ordinal (integer position). Adding a new value in the middle of the enum shifts all subsequent values' ordinals, silently corrupting existing data. Always use `EnumType.STRING`.

`@OneToMany` without `orphanRemoval = true` does not delete child entities when they are removed from the collection. Removing an element from the collection simply breaks the foreign key reference (setting it to null if nullable, or throwing a constraint violation if not). Set `orphanRemoval = true` when the child's lifecycle is owned by the parent.

`@ManyToMany` with a `List` instead of a `Set` triggers Hibernate's "bag" semantics, which issues a DELETE of all rows in the join table followed by re-insertion whenever the collection is modified. This is extremely inefficient. Use `Set<T>` for `@ManyToMany` associations.

Setting `FetchType.EAGER` on `@OneToMany` or `@ManyToMany` causes Hibernate to load the entire associated collection whenever the owning entity is loaded, regardless of whether the collection is needed. For most associations, `LAZY` is the correct default. Load associations eagerly only in specific queries via `JOIN FETCH`.

When `@GeneratedValue(strategy = GenerationType.IDENTITY)` is used, Hibernate cannot use JDBC batch inserts because it needs the generated ID from the database after each INSERT to populate the entity's ID field. Switching to `SEQUENCE` with an `allocationSize > 1` enables batching at the cost of requiring a sequence object in the database schema.
