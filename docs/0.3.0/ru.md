# relational-paths 0.3.0 — руководство

[← Back to documentation and versions / Назад к документации и версиям](../index.md)

[English](en.md) · [Русский](ru.md)

[Версии](../index.md) · [README](../../README.md) · [Возможности Java](java.ru.md)

Стройте понятные и воспроизводимые планы JOIN по метаданным таблиц. Явно выбирайте неоднозначные связи и получайте SELECT-запросы с отдельными значениями параметров.

## Сводка возможностей

| Возможность | Применение | Пример |
| --- | --- | --- |
| Подключение и локальная сборка | Для Java-разработчиков конструкторов запросов, отчётных инструментов и API на основе метаданных. Начните с JDK 17 или новее и локальной сборки версии 0.3.0. | [Код и результат](#подключение-и-локальная-сборка) |
| Описание ролей и связей | Задайте физические таблицы, их логические роли и разрешённые колонки. Каждая связь имеет направление и содержит все пары колонок, необходимые для соединения. | [Код и результат](#описание-ролей-и-связей) |
| Планирование маршрутов и объяснение результата | Запросите все роли, нужные для выбранных полей и фильтров. Планировщик выбирает единственный маршрут минимальной стоимости к каждой цели и объединяет общие совместимые участки. | [Код и результат](#планирование-маршрутов-и-объяснение-результата) |
| Выбор неоднозначных связей и роли таблиц | Два пути одинаковой стоимости могут иметь разный смысл. Планировщик сообщает о неоднозначности, а не выбирает между платёжным адресом и адресом доставки без указания. | [Код и результат](#выбор-неоднозначных-связей-и-роли-таблиц) |
| Формирование SELECT с параметрами | SqlRenderer превращает проверенный план в SELECT с INNER JOIN. Идентификаторы берутся из объявленных метаданных, а значения фильтров хранятся отдельно от текста SQL. | [Код и результат](#формирование-select-с-параметрами) |
| Лимиты, ошибки и гарантии | Если корректный результат получить нельзя, планировщик возвращает явную ошибку. Он не выдаёт неполный маршрут только ради соблюдения лимита поиска. | [Код и результат](#лимиты-ошибки-и-гарантии) |
| Запуск полного примера и решение проблем | Скачайте Example.java и запустите его с локально собранным JAR. Пример проверяет описанные маршруты, алиасы, SQL, параметры, обработку null, лимиты и неизменяемость результатов без базы данных. | [Код и результат](#запуск-полного-примера-и-решение-проблем) |

## Подключение и локальная сборка

Для Java-разработчиков конструкторов запросов, отчётных инструментов и API на основе метаданных. Начните с JDK 17 или новее и локальной сборки версии 0.3.0.

- Минимальная версия — Java 17. Поддерживаются LTS 17, 21 и 25, а также Java 27. Preview-флаги и дополнительные зависимости при выполнении не нужны.
- Библиотека распространяется по лицензии MIT. Она строит соединения и формирует SQL, но сама не подключается к базе данных.

```bash
git clone https://github.com/59Alex/relational-paths.git
cd relational-paths
./gradlew build publishToMavenLocal
```

Соберите текущую версию и опубликуйте её в локальном Maven-репозитории.

```text
build/libs/relational-paths-0.3.0.jar
build/libs/relational-paths-0.3.0-sources.jar
build/libs/relational-paths-0.3.0-javadoc.jar
```

Ожидаемые артефакты версии 0.3.0.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.59alex:relational-paths:0.3.0")
}
```

Добавьте локально опубликованный артефакт в Gradle-проект потребителя.

> **Локальная установка**
>
> Эти инструкции используют mavenLocal и не означают, что версия 0.3.0 опубликована в Maven Central. Для другой версии исходников согласуйте координаты зависимости и имя JAR-файла.

## Описание ролей и связей

Задайте физические таблицы, их логические роли и разрешённые колонки. Каждая связь имеет направление и содержит все пары колонок, необходимые для соединения.

```java
import io.github.alex59.relationalpaths.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
```

Общие импорты для всех Java-фрагментов ниже.

Следующие фрагменты размещаются внутри метода. Примеры схемы, планирования, SQL, null и лимитов используют переменные из предыдущих шагов. Скачиваемый Example.java объединяет их в полную программу.

```java
Schema schema = new Schema(List.of(
    new Table("orders", "sales.orders",
        Set.of("id", "customer_id", "tenant_id", "region_id")),
    new Table("customer", "sales.customer",
        Set.of("id", "tenant_id", "region_id", "name")),
    new Table("region", "sales.region", Set.of("id", "name"))
), List.of(
    new Relationship("order_customer", "orders", "customer", 1,
        List.of(new ColumnPair("customer_id", "id"),
                new ColumnPair("tenant_id", "tenant_id"))),
    new Relationship("customer_region", "customer", "region", 1,
        List.of(new ColumnPair("region_id", "id"))),
    new Relationship("order_region", "orders", "region", 5,
        List.of(new ColumnPair("region_id", "id")))
));
System.out.println(schema.tables().keySet());
System.out.println(schema.relationship("order_customer").columns().size());
```

Объявите три роли таблиц и связь заказа с покупателем по двум колонкам.

```text
[customer, orders, region]
2
```

Ожидаемый вывод: отсортированные роли и две пары колонок составного ключа.

- Table.id — логическая роль; Table.name — физическая таблица или schema.table. Разные роли могут ссылаться на одну физическую таблицу.
- Пары customer_id/id и tenant_id/tenant_id обе попадут в условие JOIN. Библиотека не выводит отсутствующие колонки внешнего ключа автоматически.
- Повторяющиеся идентификаторы, неизвестные роли, необъявленные колонки связи и неположительные веса отклоняются при создании метаданных.

## Планирование маршрутов и объяснение результата

Запросите все роли, нужные для выбранных полей и фильтров. Планировщик выбирает единственный маршрут минимальной стоимости к каждой цели и объединяет общие совместимые участки.

```java
JoinPlan plan = new JoinPlanner(schema)
    .plan("orders", List.of("customer", "region"));
System.out.println(plan.routes().get("region"));
System.out.println(plan.aliases());
System.out.println(plan.joins().size());
System.out.println(plan.explain());
```

Продолжение примера с переменной schema из предыдущего раздела.

```text
[order_customer, customer_region]
{orders=t0, customer=t1, region=t2}
2
ROOT orders AS t0
order_customer: orders -> customer AS t1 cost=1 on orders.customer_id = customer.id AND orders.tenant_id = customer.tenant_id
customer_region: customer -> region AS t2 cost=1 on customer.region_id = region.id
```

Ожидаемые маршруты, алиасы, число соединений и объяснение плана.

- Путь через покупателя стоит 1 + 1, а прямая связь order_region — 5. Два общих соединения покрывают обе запрошенные цели.
- Положительные целые веса задают предпочтение по смыслу данных. Они не оценивают время выполнения SQL или количество строк.
- План и алиасы воспроизводимы независимо от порядка входных метаданных и целей. Результаты и вложенные списки маршрутов неизменяемы.

> **Граница оптимизации**
>
> Планировщик выбирает кратчайший путь отдельно для каждой цели. Он не заменяет оптимизатор СУБД и не минимизирует суммарную стоимость общего дерева соединений.

## Выбор неоднозначных связей и роли таблиц

Два пути одинаковой стоимости могут иметь разный смысл. Планировщик сообщает о неоднозначности, а не выбирает между платёжным адресом и адресом доставки без указания.

```java
Schema ambiguousSchema = new Schema(List.of(
    new Table("orders", "orders", Set.of("id", "billing_id", "shipping_id")),
    new Table("address", "address", Set.of("id", "city"))
), List.of(
    new Relationship("billing_ref", "orders", "address", 1,
        List.of(new ColumnPair("billing_id", "id"))),
    new Relationship("shipping_ref", "orders", "address", 1,
        List.of(new ColumnPair("shipping_id", "id")))
));
JoinPlanner addressPlanner = new JoinPlanner(ambiguousSchema);
try {
    addressPlanner.plan("orders", List.of("address"));
    throw new AssertionError("Expected an ambiguous path");
} catch (AmbiguousPathException error) {
    System.out.println(error.target());
    System.out.println(error.candidates());
}
JoinPlan shippingPlan = addressPlanner.plan("orders", List.of("address"),
    Map.of("address", List.of("shipping_ref")));
System.out.println(shippingPlan.routes().get("address"));
```

Независимая схема: посмотрите два варианта пути, затем явно выберите shipping_ref.

```text
address
[[billing_ref], [shipping_ref]]
[shipping_ref]
```

Ожидаемые цель, варианты неоднозначного пути и выбранный маршрут.

candidates() возвращает два воспроизводимых примера пути, а не все возможные маршруты. Явный путь из идентификаторов связей может быть длиннее кратчайшего, но обязан начинаться в корне, заканчиваться в своей цели и не повторять логические роли.

```java
Schema addressRoles = new Schema(List.of(
    new Table("orders", "orders", Set.of("id", "billing_id", "shipping_id")),
    new Table("billing", "address", Set.of("id", "city")),
    new Table("shipping", "address", Set.of("id", "city"))
), List.of(
    new Relationship("billing_ref", "orders", "billing", 1,
        List.of(new ColumnPair("billing_id", "id"))),
    new Relationship("shipping_ref", "orders", "shipping", 1,
        List.of(new ColumnPair("shipping_id", "id")))
));
JoinPlan twoAddresses = new JoinPlanner(addressRoles)
    .plan("orders", List.of("shipping", "billing"));
System.out.println(twoAddresses.aliases());
```

Чтобы присоединить оба адреса, задайте две логические роли одной физической таблицы. Это отдельная независимая схема.

```text
{orders=t0, billing=t1, shipping=t2}
```

Ожидаемые алиасы: одна физическая таблица address используется в двух независимых ролях.

> **Противоречивые маршруты**
>
> Если пути к разным целям входят в одну роль через разные связи, построение плана завершится ошибкой. Согласуйте маршруты или задайте отдельные роли: библиотека не дублирует роль и не меняет её смысл автоматически.

## Формирование SELECT с параметрами

SqlRenderer превращает проверенный план в SELECT с INNER JOIN. Идентификаторы берутся из объявленных метаданных, а значения фильтров хранятся отдельно от текста SQL.

```java
BoundSql query = SqlRenderer.select(plan,
    List.of(new ColumnRef("orders", "id"), new ColumnRef("region", "name")),
    List.of(new Filter(new ColumnRef("customer", "name"),
        Filter.Operator.EQ, "Alice' OR 1=1 --")));
System.out.println(query.sql());
System.out.println(query.bindings());
```

Используйте plan из примера взвешенных маршрутов. Строка, похожая на SQL-инъекцию, намеренно передаётся как обычное значение.

```sql
SELECT "t0"."id" AS "c0", "t2"."name" AS "c1" FROM "sales"."orders" AS "t0" INNER JOIN "sales"."customer" AS "t1" ON "t0"."customer_id" = "t1"."id" AND "t0"."tenant_id" = "t1"."tenant_id" INNER JOIN "sales"."region" AS "t2" ON "t1"."region_id" = "t2"."id" WHERE "t1"."name" = ?
```

Ожидаемый SQL. Строка результата однострочная; выбранные колонки имеют алиасы c0 и c1.

```text
[Alice' OR 1=1 --]
```

Ожидаемые параметры. Ни одна часть этой строки не подставляется в query.sql().

Выполняйте query.sql() через собственный JDBC PreparedStatement. Передавайте query.bindings() в порядке списка, начиная с JDBC-индекса 1. Учитывайте типы значений конкретного драйвера и не склеивайте значения с SQL. Этот пример не открывает соединение с базой.

```java
BoundSql nullQuery = SqlRenderer.select(plan,
    List.of(new ColumnRef("customer", "id")),
    List.of(new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, null)));
System.out.println(nullQuery.sql().endsWith("WHERE \"t1\".\"name\" IS NULL"));
System.out.println(nullQuery.bindings());
```

Используйте тот же plan. EQ null превращается в IS NULL без параметра.

```text
true
[]
```

Ожидаемый результат проверки окончания SQL и пустой список параметров.

- Фильтры объединяются через AND. Поддерживаются EQ, NE, LT, LE, GT, GE. NE null превращается в IS NOT NULL; сравнения порядка с null отклоняются.
- Выбранные и фильтруемые колонки должны быть объявлены, а их роли — присутствовать в плане. Имена используют ASCII-идентификаторы; физические имена имеют вид table или schema.table.

> **Права доступа и смысл SQL**
>
> Список разрешённых колонок запрещает неизвестные идентификаторы, но не реализует права пользователя и построчный доступ. INNER JOIN может исключать строки без соответствия или размножать строки связи один-ко-многим. Приложение отвечает за доступ, транзакции и ожидаемую кратность результата.

## Лимиты, ошибки и гарантии

Если корректный результат получить нельзя, планировщик возвращает явную ошибку. Он не выдаёт неполный маршрут только ради соблюдения лимита поиска.

```java
try {
    new JoinPlanner(schema, new SearchLimits(10, 10, 0))
        .plan("orders", List.of("customer"));
    throw new AssertionError("Expected the edge-visit limit");
} catch (PlanningException error) {
    System.out.println(error.getMessage());
}
```

Продолжение примера с исходной schema. Нулевой лимит просмотра рёбер намеренно вызывает ошибку.

```text
Search exceeded maxEdgeVisits=0
```

Ожидаемое сообщение PlanningException.

- Лимиты по умолчанию: 512 ролей таблиц, 8192 связи и 8192 просмотра рёбер за автоматический поиск. Поиск просматривает все достижимые роли, даже если запрошенная цель находится рядом.
- Поиск Дейкстры хранит не более двух примеров пути на роль. Полные сохранённые пути могут занимать O(V²) ссылок в цепочке; планировщик не обещает постоянного объёма памяти.
- Циклы в метаданных допустимы благодаря положительным весам. Явный маршрут с повторным посещением логической роли отклоняется; для повторных физических таблиц задавайте разные роли.
- Schema, JoinPlanner и JoinPlan неизменяемы и могут использоваться разными потоками. Значения Filter и параметры BoundSql сохраняются по ссылке; используйте неизменяемые значения или управляйте их жизненным циклом самостоятельно.
- В библиотеку не входят чтение схемы из СУБД, внешние соединения, агрегации, подзапросы, выполнение SQL и поиск глобально оптимального дерева соединений. JoinPlan можно передать собственному генератору запросов.

> **Типы ошибок**
>
> AmbiguousPathException наследует PlanningException, а PlanningException — IllegalArgumentException. Неизвестные таблицы или колонки и некорректные метаданные также вызывают IllegalArgumentException; попытка изменить неизменяемый результат вызывает UnsupportedOperationException.

## Запуск полного примера и решение проблем

Запустите [Example.java](examples/Example.java), уже включённый в исходники, из корня репозитория на полном JDK 17 или поддерживаемой более новой версии. Команды для POSIX shell используют двоеточие как разделитель classpath; в Windows используйте точку с запятой. Примеру нужны только JAR библиотеки и JDK.

```bash
./gradlew jar
mkdir -p build/docs-example
javac --release 17 -cp build/libs/relational-paths-0.3.0.jar \
  -d build/docs-example docs/0.3.0/examples/Example.java
java -cp build/docs-example:build/libs/relational-paths-0.3.0.jar Example
```

```text
allowlist, null rules and immutable results: OK
ALL CHECKS PASSED
```

Ожидаемые последние строки. Предыдущие строки повторяют результаты из разделов выше.

```bash
./gradlew clean build -PtestJavaVersion=17
./gradlew clean build -PtestJavaVersion=21
./gradlew clean build -PtestJavaVersion=25
./gradlew clean build -PtestJavaVersion=27
```

Дополнительно: выполните тесты библиотеки и встроенный пример из каталога её исходников. Каждый выбранный JDK должен быть установлен и доступен Gradle.

- Could not find io.github.59alex:relational-paths:0.3.0 → соберите нужную версию, выполните publishToMavenLocal и добавьте mavenLocal в проект потребителя.
- Ambiguous path to address → посмотрите candidates(), выберите явный путь связей или разделите роли billing и shipping.
- No directed path from ... → проверьте направление связей. Обратное направление нужно объявить явно, если оно допустимо по смыслу данных.
- Table is absent from plan → добавьте роль выбранной колонки или фильтра в список целей плана.
- Undeclared column → добавьте разрешённую колонку в метаданные или исправьте ColumnRef. Не обходите проверку доступа приёмом произвольных идентификаторов.
- Search exceeded maxEdgeVisits → уменьшите входную схему или осознанно увеличьте SearchLimits после проверки памяти и задержки на своей нагрузке.

> **Что проверено**
>
> Скачиваемый файл скомпилирован с --release 17 и выполнен на настоящей JVM Java 17 с JAR библиотеки 0.3.0. Он проверяет строки SQL и значения параметров, но не выполняет запрос к живой базе и не измеряет производительность.

## Скачиваемые примеры

- [Example.java](examples/Example.java)

[MIT](../../LICENSE)

[← Back to documentation and versions / Назад к документации и версиям](../index.md)
