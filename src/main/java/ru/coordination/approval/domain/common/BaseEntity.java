package ru.coordination.approval.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Общий базовый класс для сущностей с PK типа UUID, генерируемым приложением
 * (08_db_schema.md §2.2: «UUID генерируется приложением»).
 *
 * <p>equals/hashCode реализованы по id (стабильный hashCode, чтобы не ломать
 * коллекции Hibernate при lazy-инициализации и detached-сущностях — см.
 * Vlad Mihalcea, "The best way to implement equals/hashCode with JPA and Hibernate").
 *
 * <p>Аннотирован {@code @SuperBuilder}, чтобы наследники могли строить объект
 * через builder, включая унаследованное поле {@code id}.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@lombok.NoArgsConstructor
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
